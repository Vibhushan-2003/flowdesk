#!/usr/bin/env bash
# ==============================================================================
# FlowDesk Offsite Backup Decryption Helper
# ==============================================================================
# Purpose: Verifies and decrypts an offsite encrypted backup archive (.tar.gz.enc)
#          restoring the plaintext backup set for the Safe Restore Orchestrator.
# ==============================================================================

set -euo pipefail

ENC_FILE=""
OUTPUT_DIR=""
ENCRYPTION_KEY="${BACKUP_ENCRYPTION_KEY:-}"
ENCRYPTION_KEY_FILE="${BACKUP_ENCRYPTION_KEY_FILE:-}"

log_info() {
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] [DECRYPT-INFO] $*"
}

log_error() {
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] [DECRYPT-ERROR] $*" >&2
}

usage() {
    cat <<EOF
Usage: $(basename "$0") --enc-file FILE --output-dir DIR [OPTIONS]

Decrypt an off-host encrypted FlowDesk backup package.

Required:
  --enc-file FILE        Path to .tar.gz.enc archive
  --output-dir DIR       Target directory to unpack decrypted backup set

Options:
  --key-file PATH        Path to file containing decryption key
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

if [ -n "$ENCRYPTION_KEY_FILE" ] && [ -f "$ENCRYPTION_KEY_FILE" ]; then
    ENCRYPTION_KEY=$(cat "$ENCRYPTION_KEY_FILE")
fi

if [ -z "$ENCRYPTION_KEY" ]; then
    log_error "No decryption key provided. Set BACKUP_ENCRYPTION_KEY or pass --key-file."
    exit 1
fi

# Optional verification against .sha256 sidecar
SHA_FILE="${ENC_FILE}.sha256"
if [ -f "$SHA_FILE" ]; then
    log_info "Verifying encrypted archive integrity against sidecar checksum..."
    EXPECTED_SHA=$(cut -d' ' -f1 < "$SHA_FILE")
    ACTUAL_SHA=$(sha256sum "$ENC_FILE" | cut -d' ' -f1)
    if [ "$EXPECTED_SHA" != "$ACTUAL_SHA" ]; then
        log_error "CHECKSUM CORRUPTION: Encrypted file checksum does not match sidecar checksum!"
        exit 1
    fi
    log_info "Encrypted archive integrity confirmed."
fi

mkdir -p "$OUTPUT_DIR"
chmod 700 "$OUTPUT_DIR"

log_info "Decrypting and extracting '${ENC_FILE}'..."
openssl enc -d -aes-256-cbc -pbkdf2 -iter 100000 -in "$ENC_FILE" -pass "pass:$ENCRYPTION_KEY" | \
    tar -xzf - -C "$OUTPUT_DIR"

log_info "Decryption complete. Validating extracted backup structure..."
EXTRACTED_MANIFEST=$(find "$OUTPUT_DIR" -maxdepth 2 -name "manifest.json" | head -n 1)
if [ -z "$EXTRACTED_MANIFEST" ] || [ ! -f "$EXTRACTED_MANIFEST" ]; then
    log_error "Decryption failed or archive was corrupt: manifest.json not found in output."
    exit 1
fi

EXTRACTED_BACKUP_DIR=$(dirname "$EXTRACTED_MANIFEST")
log_info "Valid FlowDesk backup set extracted to: ${EXTRACTED_BACKUP_DIR}"

exit 0

