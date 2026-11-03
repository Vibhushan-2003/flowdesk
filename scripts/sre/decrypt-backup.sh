#!/usr/bin/env bash
# ==============================================================================
# FlowDesk Offsite Backup Decryption Helper
# ==============================================================================
# Purpose: Verifies and decrypts an authenticated offsite backup archive (.tar.gz.gpg)
#          using GnuPG OpenPGP AES-256 with Modification Detection Code (MDC).
#          Fails closed immediately if ciphertext has been modified or corrupted.
# ==============================================================================

set -euo pipefail

ENC_FILE=""
OUTPUT_DIR=""
ENCRYPTION_KEY="${BACKUP_ENCRYPTION_KEY:-}"
ENCRYPTION_KEY_FILE="${BACKUP_ENCRYPTION_KEY_FILE:-}"
TEMP_KEY_FILE=""

log_info() {
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] [DECRYPT-INFO] $*"
}

log_error() {
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] [DECRYPT-ERROR] $*" >&2
}

cleanup() {
    if [ -n "$TEMP_KEY_FILE" ] && [ -f "$TEMP_KEY_FILE" ]; then
        rm -f "$TEMP_KEY_FILE"
    fi
}
trap cleanup EXIT ERR INT TERM

usage() {
    cat <<EOF
Usage: $(basename "$0") --enc-file FILE --output-dir DIR [OPTIONS]

Decrypt an off-host authenticated FlowDesk backup package (.tar.gz.gpg).

Required:
  --enc-file FILE        Path to .tar.gz.gpg archive
  --output-dir DIR       Target directory to unpack decrypted backup set

Options:
  --key-file PATH        Path to file containing decryption key (0600 permissions)
  -h, --help             Show this help message

Environment:
  BACKUP_ENCRYPTION_KEY  Passphrase used during encryption
EOF
    exit 0
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --enc-file) ENC_FILE="$2"; shift 2 ;;
        --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
        --key-file) ENCRYPTION_KEY_FILE="$2"; shift 2 ;;
        -h|--help) usage ;;
        *) log_error "Unknown option: $1"; usage ;;
    esac
done

if [ -z "$ENC_FILE" ] || [ ! -f "$ENC_FILE" ]; then
    log_error "Valid --enc-file is required."
    exit 1
fi

if [ -z "$OUTPUT_DIR" ]; then
    log_error "Valid --output-dir is required."
    exit 1
fi

command -v gpg >/dev/null 2>&1 || { log_error "'gpg' (GnuPG) is required for decryption but not installed."; exit 1; }

# ------------------------------------------------------------------------------
# Resolve Decryption Key File (Zero Process Argument Exposure)
# ------------------------------------------------------------------------------
SECURE_KEY_PATH=""
if [ -n "$ENCRYPTION_KEY_FILE" ] && [ -f "$ENCRYPTION_KEY_FILE" ]; then
    SECURE_KEY_PATH="$ENCRYPTION_KEY_FILE"
elif [ -n "$ENCRYPTION_KEY" ]; then
    TEMP_KEY_FILE="/tmp/.flowdesk_dec_key_$$.tmp"
    touch "$TEMP_KEY_FILE"
    chmod 600 "$TEMP_KEY_FILE"
    printf "%s" "$ENCRYPTION_KEY" > "$TEMP_KEY_FILE"
    SECURE_KEY_PATH="$TEMP_KEY_FILE"
else
    log_error "No decryption key provided. Set BACKUP_ENCRYPTION_KEY or pass --key-file."
    exit 1
fi

# ------------------------------------------------------------------------------
# Sidecar Checksum Pre-Verification
# ------------------------------------------------------------------------------
SHA_FILE="${ENC_FILE}.sha256"
if [ -f "$SHA_FILE" ]; then
    log_info "Verifying encrypted archive integrity against sidecar checksum..."
    EXPECTED_SHA=$(cut -d' ' -f1 < "$SHA_FILE" | tr -d '\r\n')
    ACTUAL_SHA=$(sha256sum "$ENC_FILE" | cut -d' ' -f1 | tr -d '\r\n')
    if [ "$EXPECTED_SHA" != "$ACTUAL_SHA" ]; then
        log_error "CHECKSUM CORRUPTION: Encrypted archive SHA-256 does not match sidecar!"
        log_error "  Expected: $EXPECTED_SHA"
        log_error "  Actual:   $ACTUAL_SHA"
        exit 1
    fi
    log_info "Sidecar SHA-256 integrity confirmed."
fi

mkdir -p "$OUTPUT_DIR"
chmod 700 "$OUTPUT_DIR"

# ------------------------------------------------------------------------------
# Authenticated Decryption with GPG MDC Verification
# ------------------------------------------------------------------------------
log_info "Decrypting and extracting '${ENC_FILE}' with GPG authenticated verification..."

# GPG verifies MDC (Modification Detection Code). If ciphertext has been modified or corrupted,
# GPG aborts and returns non-zero. The pipe to tar fails due to set -o pipefail.
gpg --batch --yes --decrypt --pinentry-mode loopback \
    --passphrase-file "$SECURE_KEY_PATH" "$ENC_FILE" | tar -xzf - -C "$OUTPUT_DIR"

log_info "Decryption complete. Validating extracted backup structure..."
EXTRACTED_MANIFEST=$(find "$OUTPUT_DIR" -maxdepth 2 -name "manifest.json" | head -n 1)
if [ -z "$EXTRACTED_MANIFEST" ] || [ ! -f "$EXTRACTED_MANIFEST" ]; then
    log_error "Decryption failed or archive was corrupt: manifest.json not found in output."
    exit 1
fi

EXTRACTED_BACKUP_DIR=$(dirname "$EXTRACTED_MANIFEST")
log_info "Valid FlowDesk backup set extracted to: ${EXTRACTED_BACKUP_DIR}"

exit 0
