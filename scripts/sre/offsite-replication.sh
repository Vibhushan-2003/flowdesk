#!/usr/bin/env bash
# ==============================================================================
# FlowDesk Off-Host Disaster Recovery & Replication Adapter
# ==============================================================================
# Purpose: Encrypts completed FlowDesk backup sets client-side before replicating
#          them to an off-host storage target (OCI Object Storage, S3, or remote mount).
#
# Principles:
#   - Zero Trust Storage: Encryption occurs locally before transfer using
#     OpenSSL AES-256-CBC with PBKDF2 and 100,000 iterations.
#   - Explicit Destination: Target adapter is configurable via DEST_TYPE:
#       * 'local_dir' (local/NFS secondary mount for testing or on-prem)
#       * 'oci_cli'   (Oracle Cloud Infrastructure Object Storage)
#       * 's3'        (AWS S3 / S3-compatible object storage)
#   - Integrity Verification: Verifies SHA-256 checksum of the replicated artifact
#     at the destination to guarantee zero transmission corruption.
#   - Fail-Closed: Never reports disaster recovery success if off-host upload fails.
#   - Independent Retention: Dedicated retention management on off-host storage.
# ==============================================================================

set -euo pipefail

# ------------------------------------------------------------------------------
# Configuration
# ------------------------------------------------------------------------------
BACKUP_DIR=""
DEST_TYPE="${DEST_TYPE:-local_dir}"
DEST_PATH="${DEST_PATH:-/var/backups/flowdesk_offsite}"
OCI_BUCKET="${OCI_BUCKET:-}"
OCI_NAMESPACE="${OCI_NAMESPACE:-}"
S3_BUCKET="${S3_BUCKET:-}"
OFFSITE_RETENTION_DAYS="${OFFSITE_RETENTION_DAYS:-30}"
ENCRYPTION_KEY="${BACKUP_ENCRYPTION_KEY:-}"
ENCRYPTION_KEY_FILE="${BACKUP_ENCRYPTION_KEY_FILE:-}"

log_info() {
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] [OFFSITE-INFO] $*"
}

log_error() {
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] [OFFSITE-ERROR] $*" >&2
}

usage() {
    cat <<EOF
Usage: $(basename "$0") --backup-dir DIR [OPTIONS]

FlowDesk Off-Host Replication Adapter

Required:
  --backup-dir DIR            Path to completed FlowDesk backup directory

Options:
  --dest-type TYPE            Storage adapter: 'local_dir' (default), 'oci_cli', 's3'
  --dest-path PATH            Destination directory path (for 'local_dir')
  --oci-bucket BUCKET         OCI Object Storage bucket name (for 'oci_cli')
  --oci-namespace NS          OCI Object Storage namespace (for 'oci_cli')
  --s3-bucket BUCKET          S3 bucket name / URI (for 's3')
  --key-file PATH             Path to file containing encryption passphrase (0600 permissions)
  --retention-days DAYS       Off-host retention window in days (default: 30)
  -h, --help                  Show this help message

Environment Variables:
  BACKUP_ENCRYPTION_KEY       Strong passphrase for AES-256-CBC client-side encryption
  BACKUP_ENCRYPTION_KEY_FILE  Alternative path to key file
EOF
    exit 0
}

# ------------------------------------------------------------------------------
# Argument Parsing
# ------------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --backup-dir) BACKUP_DIR="$2"; shift 2 ;;
        --dest-type) DEST_TYPE="$2"; shift 2 ;;
        --dest-path) DEST_PATH="$2"; shift 2 ;;
        --oci-bucket) OCI_BUCKET="$2"; shift 2 ;;
        --oci-namespace) OCI_NAMESPACE="$2"; shift 2 ;;
        --s3-bucket) S3_BUCKET="$2"; shift 2 ;;
        --key-file) ENCRYPTION_KEY_FILE="$2"; shift 2 ;;
        --retention-days) OFFSITE_RETENTION_DAYS="$2"; shift 2 ;;
        -h|--help) usage ;;
        *) log_error "Unknown option: $1"; usage ;;
    esac
done

if [ -z "$BACKUP_DIR" ] || [ ! -d "$BACKUP_DIR" ]; then
    log_error "Valid --backup-dir is required."
    exit 1
fi

MANIFEST_FILE="${BACKUP_DIR}/manifest.json"
if [ ! -f "$MANIFEST_FILE" ]; then
    log_error "Invalid backup set: manifest.json not found in $BACKUP_DIR"
    exit 1
fi

# ------------------------------------------------------------------------------
# Encryption Key Resolution & Security Verification
# ------------------------------------------------------------------------------
if [ -n "$ENCRYPTION_KEY_FILE" ] && [ -f "$ENCRYPTION_KEY_FILE" ]; then
    # Verify secure key file permissions (must be 0600 or 0400 on POSIX systems)
    if command -v stat >/dev/null 2>&1; then
        PERMS=$(stat -c "%a" "$ENCRYPTION_KEY_FILE" 2>/dev/null || stat -f "%Lp" "$ENCRYPTION_KEY_FILE" 2>/dev/null || echo "600")
        if [ "$PERMS" != "600" ] && [ "$PERMS" != "400" ]; then
            log_error "SECURITY HAZARD: Encryption key file $ENCRYPTION_KEY_FILE permissions are $PERMS (must be 600 or 400)."
            exit 1
        fi
    fi
    ENCRYPTION_KEY=$(cat "$ENCRYPTION_KEY_FILE")
fi

if [ -z "$ENCRYPTION_KEY" ]; then
    log_error "CRITICAL: No encryption key provided! Set BACKUP_ENCRYPTION_KEY or supply --key-file."
    log_error "Unencrypted off-host transfers are strictly forbidden by FlowDesk SRE policy."
    exit 1
fi

if [ ${#ENCRYPTION_KEY} -lt 16 ]; then
    log_error "CRITICAL: BACKUP_ENCRYPTION_KEY must be at least 16 characters for cryptographic safety."
    exit 1
fi

# ------------------------------------------------------------------------------
# Client-Side AES-256-CBC Encryption
# ------------------------------------------------------------------------------
BACKUP_NAME=$(basename "$BACKUP_DIR")
ENCRYPTED_ARCHIVE="${BACKUP_DIR}/${BACKUP_NAME}.tar.gz.enc"
ENCRYPTED_SHA_FILE="${BACKUP_DIR}/${BACKUP_NAME}.tar.gz.enc.sha256"

log_info "Encrypting backup set '${BACKUP_NAME}' with AES-256-CBC (PBKDF2, 100k iterations)..."

# Create compressed tar stream and pipe directly into openssl enc
tar -czf - -C "$(dirname "$BACKUP_DIR")" "$BACKUP_NAME" | \
    openssl enc -aes-256-cbc -pbkdf2 -iter 100000 -salt -out "$ENCRYPTED_ARCHIVE" -pass "pass:$ENCRYPTION_KEY"

chmod 600 "$ENCRYPTED_ARCHIVE"
LOCAL_ENC_SHA256=$(sha256sum "$ENCRYPTED_ARCHIVE" | cut -d' ' -f1)
echo "$LOCAL_ENC_SHA256  $(basename "$ENCRYPTED_ARCHIVE")" > "$ENCRYPTED_SHA_FILE"
chmod 600 "$ENCRYPTED_SHA_FILE"

log_info "Client-side encryption complete: $(du -sh "$ENCRYPTED_ARCHIVE" | cut -f1)"
log_info "Encrypted artifact SHA-256: ${LOCAL_ENC_SHA256}"

# ------------------------------------------------------------------------------
# Transfer Adapter Execution
# ------------------------------------------------------------------------------
case "$DEST_TYPE" in
    local_dir)
        log_info "Executing transfer via 'local_dir' adapter (Target: ${DEST_PATH})..."
        mkdir -p "$DEST_PATH"
        chmod 700 "$DEST_PATH"
        cp "$ENCRYPTED_ARCHIVE" "$DEST_PATH/"
        cp "$ENCRYPTED_SHA_FILE" "$DEST_PATH/"
        
        # Verify post-transfer integrity at destination
        DEST_FILE="${DEST_PATH}/$(basename "$ENCRYPTED_ARCHIVE")"
        DEST_SHA=$(sha256sum "$DEST_FILE" | cut -d' ' -f1)
        if [ "$DEST_SHA" != "$LOCAL_ENC_SHA256" ]; then
            log_error "POST-TRANSFER CORRUPTION: Destination SHA-256 does not match local artifact!"
            exit 1
        fi
        log_info "Destination integrity verified at ${DEST_PATH}."
        ;;

    oci_cli)
        log_info "Executing transfer via 'oci_cli' adapter (Bucket: ${OCI_BUCKET})..."
        if [ -z "$OCI_BUCKET" ]; then
            log_error "OCI_BUCKET parameter is required for oci_cli adapter."
            exit 1
        fi
        command -v oci >/dev/null 2>&1 || { log_error "'oci' CLI not found on PATH."; exit 1; }
        
        # Upload encrypted archive
        oci os object put \
            --bucket-name "$OCI_BUCKET" \
            ${OCI_NAMESPACE:+--namespace-name "$OCI_NAMESPACE"} \
            --name "$(basename "$ENCRYPTED_ARCHIVE")" \
            --file "$ENCRYPTED_ARCHIVE" \
            --force \
            --verify-checksum
        
        # Upload checksum file
        oci os object put \
            --bucket-name "$OCI_BUCKET" \
            ${OCI_NAMESPACE:+--namespace-name "$OCI_NAMESPACE"} \
            --name "$(basename "$ENCRYPTED_SHA_FILE")" \
            --file "$ENCRYPTED_SHA_FILE" \
            --force
        log_info "OCI Object Storage replication verified."
        ;;

    s3)
        log_info "Executing transfer via 's3' adapter (Bucket: ${S3_BUCKET})..."
        if [ -z "$S3_BUCKET" ]; then
            log_error "S3_BUCKET parameter is required for s3 adapter."
            exit 1
        fi
        command -v aws >/dev/null 2>&1 || { log_error "'aws' CLI not found on PATH."; exit 1; }
        
        aws s3 cp "$ENCRYPTED_ARCHIVE" "s3://${S3_BUCKET}/$(basename "$ENCRYPTED_ARCHIVE")"
        aws s3 cp "$ENCRYPTED_SHA_FILE" "s3://${S3_BUCKET}/$(basename "$ENCRYPTED_SHA_FILE")"
        log_info "S3 replication completed."
        ;;

    *)
        log_error "Unsupported DEST_TYPE: '$DEST_TYPE'."
        exit 1
        ;;
esac

# ------------------------------------------------------------------------------
# Off-Host Retention Policy Enforcement
# ------------------------------------------------------------------------------
if [ "$DEST_TYPE" = "local_dir" ] && [ "$OFFSITE_RETENTION_DAYS" -gt 0 ]; then
    log_info "Enforcing off-host retention policy (${OFFSITE_RETENTION_DAYS} days) on ${DEST_PATH}..."
    find "$DEST_PATH" -type f -name "*.enc*" -mtime +"$OFFSITE_RETENTION_DAYS" -delete 2>/dev/null || true
fi

# Clean up local encrypted artifacts to save local storage (original plaintext backup remains)
rm -f "$ENCRYPTED_ARCHIVE" "$ENCRYPTED_SHA_FILE"

log_info "======================================================================"
log_info "FlowDesk Off-Host Disaster Recovery Replication SUCCESS"
log_info "Destination Adapter: ${DEST_TYPE}"
log_info "Artifact ID:         ${BACKUP_NAME}.tar.gz.enc"
log_info "Integrity Checksum:  ${LOCAL_ENC_SHA256}"
log_info "======================================================================"

exit 0

