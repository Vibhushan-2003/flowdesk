#!/usr/bin/env bash
# ==============================================================================
# FlowDesk Safe Restore Orchestrator
# ==============================================================================
# Purpose: Validates, verifies, and safely restores a FlowDesk backup set into a
#          designated target environment.
#
# Safety Mandates:
#   - NEVER defaults to existing production or development database.
#   - Requires explicit --target-db and --target-attachments-dir.
#   - Requires explicit --confirm-destructive token to prevent accidental invocation.
#   - Strict pre-restore cryptographic SHA-256 verification of all archives against
#     the manifest. Corrupted or tampered backups are rejected before execution.
#   - Safe archive extraction (path traversal prevention).
#   - Post-restore database and attachment referential consistency audit.
# ==============================================================================

set -euo pipefail

# ------------------------------------------------------------------------------
# Configuration & Flags
# ------------------------------------------------------------------------------
BACKUP_DIR=""
TARGET_DB=""
TARGET_ATTACHMENTS_DIR=""
TARGET_SCHEMA="${TARGET_SCHEMA:-}"
CONFIRM_DESTRUCTIVE=""
ALLOW_PRODUCTION_OVERWRITE="${ALLOW_PRODUCTION_OVERWRITE:-false}"
USE_DOCKER="${USE_DOCKER:-false}"
DB_CONTAINER="${DB_CONTAINER:-flowdesk-prod-postgres}"
DB_USER="${DB_USER:-flowdesk_app}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

START_TIME=$(date +%s)

# ------------------------------------------------------------------------------
# Logging Helpers
# ------------------------------------------------------------------------------
log_info() {
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] [RESTORE-INFO] $*"
}

log_warn() {
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] [RESTORE-WARN] $*" >&2
}

log_error() {
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] [RESTORE-ERROR] $*" >&2
}

usage() {
    cat <<EOF
Usage: $(basename "$0") --backup-dir DIR --target-db NAME --target-attachments-dir DIR --confirm-destructive TOKEN [OPTIONS]

FlowDesk Safe Restore Orchestrator

Required Arguments:
  --backup-dir DIR              Path to directory containing completed backup set
  --target-db NAME              Target database name (NEVER defaults to production or development)
  --target-attachments-dir DIR  Target directory for restored attachment files
  --confirm-destructive TOKEN   Confirmation token ('CONFIRM-RESTORE-OVERWRITE' or 'RESTORE-PRODUCTION-DATA-DESTRUCTION')

Optional Arguments:
  --use-docker BOOL             Execute restoration via docker exec (default: false)
  --db-container NAME           Database container name (default: flowdesk-prod-postgres)
  --db-user USER                Database username (default: flowdesk_app)
  --db-host HOST                Database host (default: localhost)
  --db-port PORT                Database port (default: 5432)
  --allow-production-overwrite  Explicit flag required to target 'flowdesk' production database
  -h, --help                    Show this help message
EOF
    exit 0
}

# ------------------------------------------------------------------------------
# Parse Arguments
# ------------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --backup-dir) BACKUP_DIR="$2"; shift 2 ;;
        --target-db) TARGET_DB="$2"; shift 2 ;;
        --target-attachments-dir) TARGET_ATTACHMENTS_DIR="$2"; shift 2 ;;
        --target-schema) TARGET_SCHEMA="$2"; shift 2 ;;
        --confirm-destructive) CONFIRM_DESTRUCTIVE="$2"; shift 2 ;;
        --allow-production-overwrite) ALLOW_PRODUCTION_OVERWRITE="true"; shift ;;
        --use-docker) USE_DOCKER="$2"; shift 2 ;;
        --db-container) DB_CONTAINER="$2"; shift 2 ;;
        --db-user) DB_USER="$2"; shift 2 ;;
        --db-host) DB_HOST="$2"; shift 2 ;;
        --db-port) DB_PORT="$2"; shift 2 ;;
        -h|--help) usage ;;
        *) log_error "Unknown option: $1"; usage ;;
    esac
done

# ------------------------------------------------------------------------------
# Safety Guardrails & Validation
# ------------------------------------------------------------------------------
if [ -z "$BACKUP_DIR" ] || [ ! -d "$BACKUP_DIR" ]; then
    log_error "Valid --backup-dir is required."
    exit 1
fi

if [ -z "$TARGET_DB" ]; then
    log_error "Explicit --target-db is required. Restore will NEVER default to an existing database."
    exit 1
fi

if [ -z "$TARGET_ATTACHMENTS_DIR" ]; then
    log_error "Explicit --target-attachments-dir is required."
    exit 1
fi

# Production database safety guard
if [ "$TARGET_DB" = "flowdesk" ]; then
    if [ "$ALLOW_PRODUCTION_OVERWRITE" != "true" ] || [ "$CONFIRM_DESTRUCTIVE" != "RESTORE-PRODUCTION-DATA-DESTRUCTION" ]; then
        log_error "SAFETY INTERVENTION: Target database is 'flowdesk' (production). Overwriting production requires:"
        log_error "  1. Flag: --allow-production-overwrite"
        log_error "  2. Token: --confirm-destructive RESTORE-PRODUCTION-DATA-DESTRUCTION"
        log_error "Restoration aborted."
        exit 1
    fi
else
    # Non-production targets require standard confirmation
    if [ "$CONFIRM_DESTRUCTIVE" != "CONFIRM-RESTORE-OVERWRITE" ] && [ "$CONFIRM_DESTRUCTIVE" != "RESTORE-PRODUCTION-DATA-DESTRUCTION" ]; then
        log_error "Destructive confirmation token missing or invalid. Pass '--confirm-destructive CONFIRM-RESTORE-OVERWRITE'."
        exit 1
    fi
fi

# ------------------------------------------------------------------------------
# Manifest & Cryptographic Pre-Flight Verification
# ------------------------------------------------------------------------------
MANIFEST_FILE="${BACKUP_DIR}/manifest.json"
if [ ! -f "$MANIFEST_FILE" ]; then
    log_error "Invalid backup set: Missing manifest.json in $BACKUP_DIR"
    exit 1
fi

DB_DUMP_FILE="${BACKUP_DIR}/database.sql.gz"
ATTACHMENTS_ARCHIVE="${BACKUP_DIR}/attachments.tar.gz"

if [ ! -f "$DB_DUMP_FILE" ] || [ ! -f "$ATTACHMENTS_ARCHIVE" ]; then
    log_error "Invalid backup set: Missing database.sql.gz or attachments.tar.gz in $BACKUP_DIR"
    exit 1
fi

log_info "Verifying cryptographic checksums from manifest..."
EXPECTED_DB_SHA256=$(grep -A 5 '"database"' "$MANIFEST_FILE" | grep '"sha256"' | cut -d'"' -f4 | tr -d '\r\n')
EXPECTED_ATT_SHA256=$(grep -A 5 '"attachments"' "$MANIFEST_FILE" | grep '"sha256"' | cut -d'"' -f4 | tr -d '\r\n')

ACTUAL_DB_SHA256=$(sha256sum "$DB_DUMP_FILE" | cut -d' ' -f1 | tr -d '\r\n')
ACTUAL_ATT_SHA256=$(sha256sum "$ATTACHMENTS_ARCHIVE" | cut -d' ' -f1 | tr -d '\r\n')

if [ "$ACTUAL_DB_SHA256" != "$EXPECTED_DB_SHA256" ]; then
    log_error "INTEGRITY VIOLATION: Database dump checksum mismatch!"
    log_error "  Expected: $EXPECTED_DB_SHA256"
    log_error "  Actual:   $ACTUAL_DB_SHA256"
    exit 1
fi

if [ "$ACTUAL_ATT_SHA256" != "$EXPECTED_ATT_SHA256" ]; then
    log_error "INTEGRITY VIOLATION: Attachments archive checksum mismatch!"
    log_error "  Expected: $EXPECTED_ATT_SHA256"
    log_error "  Actual:   $ACTUAL_ATT_SHA256"
    exit 1
fi

log_info "Cryptographic validation PASSED. All archives match manifest."

# ------------------------------------------------------------------------------
# Database Restore Execution
# ------------------------------------------------------------------------------
execute_target_sql() {
    local sql_query="$1"
    if [ "$USE_DOCKER" = "true" ]; then
        if [ -n "$TARGET_SCHEMA" ]; then
            docker exec -i -e PGOPTIONS="-c search_path=${TARGET_SCHEMA}" "$DB_CONTAINER" psql -U "$DB_USER" -d "$TARGET_DB" -t -A -c "$sql_query" | tr -d '\r'
        else
            docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$TARGET_DB" -t -A -c "$sql_query" | tr -d '\r'
        fi
    else
        if [ -n "$TARGET_SCHEMA" ]; then
            PGOPTIONS="-c search_path=${TARGET_SCHEMA}" psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -d "$TARGET_DB" -t -A -c "$sql_query" | tr -d '\r'
        else
            psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -d "$TARGET_DB" -t -A -c "$sql_query" | tr -d '\r'
        fi
    fi
}

log_info "Restoring PostgreSQL database into '${TARGET_DB}'..."
if [ "$USE_DOCKER" = "true" ]; then
    gunzip -c "$DB_DUMP_FILE" | docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$TARGET_DB" --single-transaction -v ON_ERROR_STOP=1 >/dev/null
else
    gunzip -c "$DB_DUMP_FILE" | psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -d "$TARGET_DB" --single-transaction -v ON_ERROR_STOP=1 >/dev/null
fi
log_info "Database schema and tables restored successfully."

# ------------------------------------------------------------------------------
# Safe Attachments Extraction (Path Traversal Protection)
# ------------------------------------------------------------------------------
log_info "Extracting attachments into '${TARGET_ATTACHMENTS_DIR}'..."
mkdir -p "$TARGET_ATTACHMENTS_DIR"
chmod 700 "$TARGET_ATTACHMENTS_DIR"

# Test archive for dangerous absolute paths or parent directory traversal before extraction
if tar -tzf "$ATTACHMENTS_ARCHIVE" | grep -E '^/|\.\./' >/dev/null 2>&1; then
    log_error "SECURITY RISK: Attachment archive contains dangerous absolute or parent path traversal entries. Aborting!"
    exit 1
fi

# Extract into target directory
tar -xzf "$ATTACHMENTS_ARCHIVE" -C "$TARGET_ATTACHMENTS_DIR" --strip-components=1 2>/dev/null || \
    tar -xzf "$ATTACHMENTS_ARCHIVE" -C "$TARGET_ATTACHMENTS_DIR"

# Enforce secure permissions on extracted files
find "$TARGET_ATTACHMENTS_DIR" -type f -exec chmod 600 {} + 2>/dev/null || true
find "$TARGET_ATTACHMENTS_DIR" -type d -exec chmod 700 {} + 2>/dev/null || true
log_info "Attachment extraction complete with restrictive permissions."

# ------------------------------------------------------------------------------
# Post-Restore Health & Referential Consistency Audit
# ------------------------------------------------------------------------------
log_info "Executing post-restore data integrity audit..."

FLYWAY_VER=$(execute_target_sql "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1;" || echo "unknown")
USER_COUNT=$(execute_target_sql "SELECT count(*) FROM users;" || echo "0")
TICKET_COUNT=$(execute_target_sql "SELECT count(*) FROM tickets;" || echo "0")
ACTIVE_ATTACHMENTS=$(execute_target_sql "SELECT count(*) FROM ticket_attachments WHERE status != 'DELETED';" || echo "0")

log_info "Restored Schema Flyway Version: ${FLYWAY_VER}"
log_info "Restored Records: Users=${USER_COUNT}, Tickets=${TICKET_COUNT}, Active Attachments=${ACTIVE_ATTACHMENTS}"

# Audit all active attachments on disk
DB_ATTACHMENTS=$(execute_target_sql "SELECT storage_key, checksum_sha256 FROM ticket_attachments WHERE status != 'DELETED';")
AUDIT_ERRORS=0

while IFS='|' read -r storage_key expected_sha; do
    storage_key=$(echo "$storage_key" | tr -d '\r\n')
    expected_sha=$(echo "$expected_sha" | tr -d '\r\n')
    [ -z "$storage_key" ] && continue
    ATTACHMENT_PATH=$(find "$TARGET_ATTACHMENTS_DIR" -type f -name "$storage_key" | head -n 1)
    if [ -z "$ATTACHMENT_PATH" ] || [ ! -f "$ATTACHMENT_PATH" ]; then
        log_error "POST-RESTORE AUDIT FAILURE: Attachment file '$storage_key' does not exist in $TARGET_ATTACHMENTS_DIR!"
        AUDIT_ERRORS=$((AUDIT_ERRORS + 1))
        continue
    fi
    ACTUAL_SHA=$(sha256sum "$ATTACHMENT_PATH" | cut -d' ' -f1)
    if [ "$ACTUAL_SHA" != "$expected_sha" ]; then
        log_error "POST-RESTORE AUDIT FAILURE: Attachment file '$storage_key' checksum corrupted! (expected $expected_sha, got $ACTUAL_SHA)"
        AUDIT_ERRORS=$((AUDIT_ERRORS + 1))
    fi
done <<< "$DB_ATTACHMENTS"

if [ "$AUDIT_ERRORS" -gt 0 ]; then
    log_error "Post-restore integrity audit FAILED with ${AUDIT_ERRORS} errors."
    exit 1
fi

END_TIME=$(date +%s)
RTO_DURATION=$((END_TIME - START_TIME))

log_info "======================================================================"
log_info "FlowDesk Restoration Completed Successfully!"
log_info "Recovery Time (RTO Duration): ${RTO_DURATION} seconds"
log_info "Flyway Schema Version:       ${FLYWAY_VER}"
log_info "Total Users Restored:        ${USER_COUNT}"
log_info "Total Tickets Restored:      ${TICKET_COUNT}"
log_info "Verified Attachments:        ${ACTIVE_ATTACHMENTS} (100% integrity match)"
log_info "======================================================================"

exit 0
