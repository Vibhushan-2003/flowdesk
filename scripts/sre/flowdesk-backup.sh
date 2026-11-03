#!/usr/bin/env bash
# ==============================================================================
# FlowDesk Production Backup Orchestrator
# ==============================================================================
# Purpose: Safely captures consistent, cryptographically verifiable backups of
#          FlowDesk's PostgreSQL database and private ticket attachments.
#
# Supported Consistency Strategies:
#   1. verified_snapshot (Default): Captures database and attachment snapshots,
#      then cryptographically and referentially verifies that every active attachment
#      referenced in the frozen database dump exists with matching SHA-256 checksum.
#   2. quiescence: Temporarily pauses the backend container during snapshot
#      creation to guarantee zero write operations in flight, followed by
#      referential consistency verification.
#
# Security:
#   - Restrictive permissions (umask 077, 0700 dir, 0600 files).
#   - Zero credentials printed in logs or terminal output.
#   - Atomic publication (temporary staging directory moved on success only).
#   - Concurrency locking prevents overlapping execution.
# ==============================================================================

set -euo pipefail

# ------------------------------------------------------------------------------
# Configuration Defaults (overridable via environment variables or flags)
# ------------------------------------------------------------------------------
BACKUP_ROOT="${BACKUP_ROOT:-/var/backups/flowdesk}"
USE_DOCKER="${USE_DOCKER:-true}"
DB_CONTAINER="${DB_CONTAINER:-flowdesk-prod-postgres}"
BACKEND_CONTAINER="${BACKEND_CONTAINER:-flowdesk-prod-backend}"
DB_NAME="${DB_NAME:-flowdesk}"
DB_USER="${DB_USER:-flowdesk_app}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_SCHEMA="${DB_SCHEMA:-}"
ATTACHMENTS_DIR="${ATTACHMENTS_DIR:-/var/lib/docker/volumes/flowdesk_prod_attachments/_data}"
CONSISTENCY_MODE="${CONSISTENCY_MODE:-verified_snapshot}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
MIN_FREE_SPACE_MB="${MIN_FREE_SPACE_MB:-500}"
LOCK_FILE="${LOCK_FILE:-/tmp/flowdesk_backup.lock}"
METRICS_DIR="${METRICS_DIR:-}"
OFFSITE_ENABLED="${OFFSITE_ENABLED:-false}"

# Global state tracking for atomic cleanup
STAGING_DIR=""
BACKUP_ID=""
START_TIME=$(date +%s)

# ------------------------------------------------------------------------------
# Logging Functions
# ------------------------------------------------------------------------------
log_info() {
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] [INFO] $*"
}

log_warn() {
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] [WARN] $*" >&2
}

log_error() {
    echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] [ERROR] $*" >&2
}

# ------------------------------------------------------------------------------
# Cleanup on Failure or Interruption
# ------------------------------------------------------------------------------
cleanup() {
    local exit_code=$?
    if [ -n "$STAGING_DIR" ] && [ -d "$STAGING_DIR" ]; then
        log_warn "Backup failed or interrupted (exit code $exit_code). Cleaning up temporary staging directory: $STAGING_DIR"
        rm -rf "$STAGING_DIR"
    fi
    # If quiescence mode paused the container, ensure it is unpaused
    if [ "$CONSISTENCY_MODE" = "quiescence" ] && [ "$USE_DOCKER" = "true" ]; then
        docker unpause "$BACKEND_CONTAINER" 2>/dev/null || true
    fi
    # Release flock lock if file descriptor 200 is open
    flock -u 200 2>/dev/null || true
    if [ $exit_code -ne 0 ]; then
        log_error "FlowDesk backup aborted with failure code $exit_code."
        if [ -n "$METRICS_DIR" ] && [ -d "$METRICS_DIR" ]; then
            cat > "${METRICS_DIR}/flowdesk_backup.prom.$$" <<EOF
# HELP flowdesk_backup_status Binary indicator of last backup run (1 = success, 0 = failure)
# TYPE flowdesk_backup_status gauge
flowdesk_backup_status 0
# HELP flowdesk_backup_collector_healthy Binary indicator of collector health
# TYPE flowdesk_backup_collector_healthy gauge
flowdesk_backup_collector_healthy 1
EOF
            mv "${METRICS_DIR}/flowdesk_backup.prom.$$" "${METRICS_DIR}/flowdesk_backup.prom" 2>/dev/null || true
            chmod 644 "${METRICS_DIR}/flowdesk_backup.prom" 2>/dev/null || true
        fi
    fi
}
trap cleanup EXIT ERR INT TERM

# ------------------------------------------------------------------------------
# Usage Documentation
# ------------------------------------------------------------------------------
usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

FlowDesk Production Backup Orchestrator

Options:
  --backup-root DIR          Target directory for backups (default: /var/backups/flowdesk)
  --use-docker BOOL          Use docker exec for database/attachments (default: true)
  --db-container NAME        PostgreSQL container name (default: flowdesk-prod-postgres)
  --backend-container NAME   Backend container name for quiescence (default: flowdesk-prod-backend)
  --db-name NAME             Database name (default: flowdesk)
  --db-user USER             Database username (default: flowdesk_app)
  --db-host HOST             Database host for standalone mode (default: localhost)
  --db-port PORT             Database port for standalone mode (default: 5432)
  --attachments-dir DIR      Path to attachments directory on host or container
  --consistency-mode MODE    Consistency strategy: 'verified_snapshot' (default) or 'quiescence'
  --retention-days DAYS      Number of days to keep local backups (default: 7)
  --min-free-mb MB           Minimum required free disk space in MB (default: 500)
  --metrics-dir DIR          Prometheus textfile metrics directory (optional)
  -h, --help                 Display this help message
EOF
    exit 0
}

# ------------------------------------------------------------------------------
# Parse Command-Line Arguments
# ------------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --backup-root) BACKUP_ROOT="$2"; shift 2 ;;
        --use-docker) USE_DOCKER="$2"; shift 2 ;;
        --db-container) DB_CONTAINER="$2"; shift 2 ;;
        --backend-container) BACKEND_CONTAINER="$2"; shift 2 ;;
        --db-name) DB_NAME="$2"; shift 2 ;;
        --db-user) DB_USER="$2"; shift 2 ;;
        --db-host) DB_HOST="$2"; shift 2 ;;
        --db-port) DB_PORT="$2"; shift 2 ;;
        --db-schema) DB_SCHEMA="$2"; shift 2 ;;
        --attachments-dir) ATTACHMENTS_DIR="$2"; shift 2 ;;
        --consistency-mode) CONSISTENCY_MODE="$2"; shift 2 ;;
        --retention-days) RETENTION_DAYS="$2"; shift 2 ;;
        --min-free-mb) MIN_FREE_SPACE_MB="$2"; shift 2 ;;
        --metrics-dir) METRICS_DIR="$2"; shift 2 ;;
        --offsite) OFFSITE_ENABLED="$2"; shift 2 ;;
        -h|--help) usage ;;
        *) log_error "Unknown option: $1"; usage ;;
    esac
done

# ------------------------------------------------------------------------------
# Environment and Security Pre-flight Checks
# ------------------------------------------------------------------------------
umask 077

if [ "$CONSISTENCY_MODE" != "verified_snapshot" ] && [ "$CONSISTENCY_MODE" != "quiescence" ]; then
    log_error "Invalid consistency mode: '$CONSISTENCY_MODE'. Must be 'verified_snapshot' or 'quiescence'."
    exit 1
fi

command -v tar >/dev/null 2>&1 || { log_error "'tar' utility is required but not installed."; exit 1; }
command -v gzip >/dev/null 2>&1 || { log_error "'gzip' utility is required but not installed."; exit 1; }
command -v sha256sum >/dev/null 2>&1 || { log_error "'sha256sum' utility is required but not installed."; exit 1; }

# Concurrency Guard: Ensure only one backup runs at a time
if command -v flock >/dev/null 2>&1; then
    exec 200>"$LOCK_FILE"
    if ! flock -n 200; then
        log_error "Another FlowDesk backup process is actively running (lock held on $LOCK_FILE). Exiting."
        exit 1
    fi
else
    # Fallback directory-based locking for platforms without flock
    LOCK_DIR="${LOCK_FILE}.d"
    if ! mkdir "$LOCK_DIR" 2>/dev/null; then
        log_error "Another FlowDesk backup process is actively running (lock dir $LOCK_DIR exists). Exiting."
        exit 1
    fi
    trap 'rm -rf "$LOCK_DIR"; cleanup' EXIT ERR INT TERM
fi

# Ensure backup destination root exists with restrictive permissions
mkdir -p "$BACKUP_ROOT"
chmod 700 "$BACKUP_ROOT"

# Check available disk space
if command -v df >/dev/null 2>&1; then
    FREE_KB=$(df -k "$BACKUP_ROOT" | awk 'NR==2 {print $4}')
    FREE_MB=$((FREE_KB / 1024))
    if [ "$FREE_MB" -lt "$MIN_FREE_SPACE_MB" ]; then
        log_error "Insufficient disk space on $BACKUP_ROOT: ${FREE_MB}MB available, minimum required is ${MIN_FREE_SPACE_MB}MB."
        exit 1
    fi
    log_info "Storage capacity check passed: ${FREE_MB}MB free on target volume."
fi

# ------------------------------------------------------------------------------
# Initialize Staging Directory
# ------------------------------------------------------------------------------
TIMESTAMP=$(date -u +"%Y%m%d_%H%M%SZ")
BACKUP_ID="flowdesk_backup_${TIMESTAMP}"
STAGING_DIR="${BACKUP_ROOT}/.staging_${BACKUP_ID}"
FINAL_BACKUP_DIR="${BACKUP_ROOT}/${BACKUP_ID}"

mkdir -p "$STAGING_DIR"
chmod 700 "$STAGING_DIR"

log_info "Starting FlowDesk backup [ID: ${BACKUP_ID}]"
log_info "Consistency Strategy: ${CONSISTENCY_MODE}"

# ------------------------------------------------------------------------------
# Database Query and Dump Helpers
# ------------------------------------------------------------------------------
execute_sql() {
    local sql_query="$1"
    if [ "$USE_DOCKER" = "true" ]; then
        if [ -n "$DB_SCHEMA" ]; then
            docker exec -i -e PGOPTIONS="-c search_path=${DB_SCHEMA}" "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -A -c "$sql_query" | tr -d '\r'
        else
            docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -A -c "$sql_query" | tr -d '\r'
        fi
    else
        if [ -n "$DB_SCHEMA" ]; then
            PGOPTIONS="-c search_path=${DB_SCHEMA}" psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -d "$DB_NAME" -t -A -c "$sql_query" | tr -d '\r'
        else
            psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -d "$DB_NAME" -t -A -c "$sql_query" | tr -d '\r'
        fi
    fi
}

dump_database() {
    local target_dump_file="$1"
    log_info "Dumping PostgreSQL database '${DB_NAME}' using native pg_dump..."
    if [ "$USE_DOCKER" = "true" ]; then
        docker exec "$DB_CONTAINER" pg_dump \
            -U "$DB_USER" \
            -d "$DB_NAME" \
            ${DB_SCHEMA:+-n "$DB_SCHEMA"} \
            --clean \
            --if-exists \
            --no-owner \
            --no-privileges | gzip -9 > "$target_dump_file"
    else
        pg_dump \
            -U "$DB_USER" \
            -h "$DB_HOST" \
            -p "$DB_PORT" \
            -d "$DB_NAME" \
            ${DB_SCHEMA:+-n "$DB_SCHEMA"} \
            --clean \
            --if-exists \
            --no-owner \
            --no-privileges | gzip -9 > "$target_dump_file"
    fi

    # Verify dump file is non-empty and valid gzip
    if [ ! -s "$target_dump_file" ]; then
        log_error "Database dump produced an empty file: $target_dump_file"
        exit 1
    fi
    gzip -t "$target_dump_file" || { log_error "Database dump gzip integrity check failed."; exit 1; }
    chmod 600 "$target_dump_file"
    log_info "Database dump completed and compressed: $(du -h "$target_dump_file" | cut -f1)"
}

archive_attachments() {
    local target_archive="$1"
    log_info "Archiving private attachment repository..."

    if [ "$USE_DOCKER" = "true" ]; then
        # Check if attachments directory exists inside backend container or volume
        docker exec "$BACKEND_CONTAINER" tar -czf - -C /app/data attachments > "$target_archive" 2>/dev/null || {
            # Fallback to direct host volume if backend container tar is unavailable
            if [ -d "$ATTACHMENTS_DIR" ]; then
                tar -czf "$target_archive" -C "$(dirname "$ATTACHMENTS_DIR")" "$(basename "$ATTACHMENTS_DIR")"
            else
                # If attachments directory is currently empty or doesn't exist, create empty archive
                tar -czf "$target_archive" -T /dev/null
            fi
        }
    else
        if [ -d "$ATTACHMENTS_DIR" ]; then
            tar -czf "$target_archive" -C "$(dirname "$ATTACHMENTS_DIR")" "$(basename "$ATTACHMENTS_DIR")"
        else
            mkdir -p "$ATTACHMENTS_DIR"
            tar -czf "$target_archive" -C "$(dirname "$ATTACHMENTS_DIR")" "$(basename "$ATTACHMENTS_DIR")"
        fi
    fi

    gzip -t "$target_archive" || { log_error "Attachment archive gzip verification failed."; exit 1; }
    chmod 600 "$target_archive"
    log_info "Attachments archived and compressed: $(du -h "$target_archive" | cut -f1)"
}

# ------------------------------------------------------------------------------
# Execution Workflow with Consistency Strategy
# ------------------------------------------------------------------------------
DB_DUMP_FILE="${STAGING_DIR}/database.sql.gz"
ATTACHMENTS_ARCHIVE="${STAGING_DIR}/attachments.tar.gz"

if [ "$CONSISTENCY_MODE" = "quiescence" ] && [ "$USE_DOCKER" = "true" ]; then
    log_info "Engaging Quiescence Mode: Pausing backend container to freeze application transactions..."
    docker pause "$BACKEND_CONTAINER"
    
    dump_database "$DB_DUMP_FILE"
    archive_attachments "$ATTACHMENTS_ARCHIVE"
    
    log_info "Quiescence window complete: Resuming backend container..."
    docker unpause "$BACKEND_CONTAINER"
else
    # Verified Snapshot Mode (Zero downtime)
    # Step 1: Dump database transactionally
    dump_database "$DB_DUMP_FILE"
    # Step 2: Snapshot attachments
    archive_attachments "$ATTACHMENTS_ARCHIVE"
fi

# ------------------------------------------------------------------------------
# Snapshot Referential Consistency Verification
# ------------------------------------------------------------------------------
log_info "Verifying database and attachment referential consistency directly from captured snapshot..."

# Extract active attachment references directly from the database dump file (database.sql.gz)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if command -v python3 >/dev/null 2>&1 && [ -f "$SCRIPT_DIR/extract_dump_attachments.py" ]; then
    DB_ATTACHMENTS_LIST=$(python3 "$SCRIPT_DIR/extract_dump_attachments.py" "$DB_DUMP_FILE")
elif command -v python >/dev/null 2>&1 && [ -f "$SCRIPT_DIR/extract_dump_attachments.py" ]; then
    DB_ATTACHMENTS_LIST=$(python "$SCRIPT_DIR/extract_dump_attachments.py" "$DB_DUMP_FILE")
else
    # Fallback to direct dump parsing via awk
    DB_ATTACHMENTS_LIST=$(gunzip -c "$DB_DUMP_FILE" | awk -F'\t' '
        BEGIN { in_copy = 0; k_idx = -1; s_idx = -1; stat_idx = -1; }
        !in_copy && /^COPY .*ticket_attachments/ {
            in_copy = 1;
            match($0, /\((.*)\)/, m);
            n = split(m[1], cols, ", ");
            for (i = 1; i <= n; i++) {
                gsub(/[ "]/, "", cols[i]);
                if (cols[i] == "storage_key") k_idx = i;
                if (cols[i] == "checksum_sha256") s_idx = i;
                if (cols[i] == "status") stat_idx = i;
            }
            next;
        }
        in_copy && /^\\[.]/ { in_copy = 0; next; }
        in_copy && k_idx > 0 && s_idx > 0 {
            status = (stat_idx > 0) ? $stat_idx : "ACTIVE";
            if (status != "DELETED" && $k_idx != "" && $s_idx != "") {
                print $k_idx "|" tolower($s_idx);
            }
        }
    ')
fi
TOTAL_ACTIVE_ATTACHMENTS=$(echo "$DB_ATTACHMENTS_LIST" | grep -v '^$' | wc -l | tr -d ' ' || true)
log_info "Database dump contains ${TOTAL_ACTIVE_ATTACHMENTS} active attachment references."

if [ "$TOTAL_ACTIVE_ATTACHMENTS" -gt 0 ]; then
    # Inspect files inside the generated attachments archive
    ARCHIVE_FILE_LIST=$(tar -tzf "$ATTACHMENTS_ARCHIVE")
    
    TEMP_CHECK_DIR="${STAGING_DIR}/.consistency_check"
    mkdir -p "$TEMP_CHECK_DIR"
    tar -xzf "$ATTACHMENTS_ARCHIVE" -C "$TEMP_CHECK_DIR"
    
    MISSING_COUNT=0
    CORRUPT_COUNT=0

    while IFS='|' read -r storage_key expected_sha256; do
        storage_key=$(echo "$storage_key" | tr -d '\r\n')
        expected_sha256=$(echo "$expected_sha256" | tr -d '\r\n')
        [ -z "$storage_key" ] && continue
        
        # Locate the attachment file inside extracted archive
        MATCHED_FILE=$(find "$TEMP_CHECK_DIR" -type f -name "$storage_key" | head -n 1)
        if [ -z "$MATCHED_FILE" ] || [ ! -f "$MATCHED_FILE" ]; then
            log_error "CONSISTENCY FAILURE: Attachment '$storage_key' recorded in database is MISSING from attachment archive!"
            MISSING_COUNT=$((MISSING_COUNT + 1))
            continue
        fi
        
        # Verify SHA-256 matches database record
        ACTUAL_SHA256=$(sha256sum "$MATCHED_FILE" | cut -d' ' -f1)
        if [ "$ACTUAL_SHA256" != "$expected_sha256" ]; then
            log_error "INTEGRITY FAILURE: Attachment '$storage_key' SHA-256 mismatch! (expected $expected_sha256, got $ACTUAL_SHA256)"
            CORRUPT_COUNT=$((CORRUPT_COUNT + 1))
        fi
    done <<< "$DB_ATTACHMENTS_LIST"

    # Clean up verification scratch directory
    rm -rf "$TEMP_CHECK_DIR"

    if [ "$MISSING_COUNT" -gt 0 ] || [ "$CORRUPT_COUNT" -gt 0 ]; then
        log_error "Consistency validation failed: ${MISSING_COUNT} missing, ${CORRUPT_COUNT} corrupted attachments. Aborting backup!"
        exit 1
    fi
    log_info "Consistency verified: All ${TOTAL_ACTIVE_ATTACHMENTS} database-referenced attachments exist with valid cryptographic checksums."
fi

# ------------------------------------------------------------------------------
# Flyway Schema & Application Metadata Extraction
# ------------------------------------------------------------------------------
FLYWAY_VERSION=$(execute_sql "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1;" || echo "unknown")
TABLE_COUNT=$(execute_sql "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';" || echo "0")
POSTGRES_VERSION=$(execute_sql "SHOW server_version;" || echo "unknown")

# Cryptographic SHA-256 Manifest
DB_SHA256=$(sha256sum "$DB_DUMP_FILE" | cut -d' ' -f1)
ATTACHMENTS_SHA256=$(sha256sum "$ATTACHMENTS_ARCHIVE" | cut -d' ' -f1)
DB_SIZE=$(wc -c < "$DB_DUMP_FILE")
ATTACHMENTS_SIZE=$(wc -c < "$ATTACHMENTS_ARCHIVE")
TOTAL_BACKUP_BYTES=$((DB_SIZE + ATTACHMENTS_SIZE))

MANIFEST_FILE="${STAGING_DIR}/manifest.json"
cat > "$MANIFEST_FILE" <<EOF
{
  "backup_id": "${BACKUP_ID}",
  "timestamp_utc": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "flowdesk_version": "$(git rev-parse HEAD 2>/dev/null || echo "unknown")",
  "flyway_migration_version": "${FLYWAY_VERSION}",
  "postgres_version": "${POSTGRES_VERSION}",
  "consistency_mode": "${CONSISTENCY_MODE}",
  "status": "CONSISTENT_VERIFIED",
  "database": {
    "filename": "database.sql.gz",
    "sha256": "${DB_SHA256}",
    "size_bytes": ${DB_SIZE},
    "tables_count": ${TABLE_COUNT}
  },
  "attachments": {
    "filename": "attachments.tar.gz",
    "sha256": "${ATTACHMENTS_SHA256}",
    "size_bytes": ${ATTACHMENTS_SIZE},
    "verified_active_count": ${TOTAL_ACTIVE_ATTACHMENTS}
  },
  "total_size_bytes": ${TOTAL_BACKUP_BYTES}
}
EOF
chmod 600 "$MANIFEST_FILE"

# Generate combined checksums file
cd "$STAGING_DIR"
sha256sum database.sql.gz attachments.tar.gz manifest.json > SHA256SUMS
chmod 600 SHA256SUMS
cd - >/dev/null

# ------------------------------------------------------------------------------
# Atomic Publication
# ------------------------------------------------------------------------------
log_info "Atomically publishing backup set to ${FINAL_BACKUP_DIR}..."
mv "$STAGING_DIR" "$FINAL_BACKUP_DIR"
STAGING_DIR="" # Disarm trap cleanup
chmod 700 "$FINAL_BACKUP_DIR"

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

log_info "Backup [${BACKUP_ID}] completed successfully in ${DURATION}s."
log_info "Backup Manifest: ${FINAL_BACKUP_DIR}/manifest.json"
log_info "Total Archive Size: $(du -sh "$FINAL_BACKUP_DIR" | cut -f1)"

# ------------------------------------------------------------------------------
# Off-Host Replication (if enabled)
# ------------------------------------------------------------------------------
if [ "$OFFSITE_ENABLED" = "true" ]; then
    log_info "Initiating off-host disaster recovery replication..."
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    "$SCRIPT_DIR/offsite-replication.sh" --backup-dir "$FINAL_BACKUP_DIR"
fi

# ------------------------------------------------------------------------------
# Automated Retention Pruning
# ------------------------------------------------------------------------------
if [ "$RETENTION_DAYS" -gt 0 ]; then
    log_info "Evaluating retention policy: Pruning local backups older than ${RETENTION_DAYS} days..."
    PRUNE_COUNT=0
    # Find directories matching flowdesk_backup_* older than retention days
    while IFS= read -r old_backup; do
        [ -z "$old_backup" ] && continue
        log_info "Pruning expired backup set: $(basename "$old_backup")"
        rm -rf "$old_backup"
        PRUNE_COUNT=$((PRUNE_COUNT + 1))
    done < <(find "$BACKUP_ROOT" -maxdepth 1 -type d -name "flowdesk_backup_*" -mtime +"$RETENTION_DAYS")
    log_info "Retention cleanup finished. ${PRUNE_COUNT} expired backup sets removed."
fi

# ------------------------------------------------------------------------------
# Prometheus Metric Emission (Optional Textfile Collector)
# ------------------------------------------------------------------------------
if [ -n "$METRICS_DIR" ] && [ -d "$METRICS_DIR" ]; then
    METRICS_FILE="${METRICS_DIR}/flowdesk_backup.prom"
    cat > "${METRICS_FILE}.$$" <<EOF
# HELP flowdesk_backup_last_success_timestamp Timestamp of last successful FlowDesk backup
# TYPE flowdesk_backup_last_success_timestamp gauge
flowdesk_backup_last_success_timestamp ${END_TIME}
# HELP flowdesk_backup_duration_seconds Execution duration of the last FlowDesk backup
# TYPE flowdesk_backup_duration_seconds gauge
flowdesk_backup_duration_seconds ${DURATION}
# HELP flowdesk_backup_size_bytes Total uncompressed size of the last backup set
# TYPE flowdesk_backup_size_bytes gauge
flowdesk_backup_size_bytes ${TOTAL_BACKUP_BYTES}
# HELP flowdesk_backup_status Binary indicator of last backup run (1 = success, 0 = failure)
# TYPE flowdesk_backup_status gauge
flowdesk_backup_status 1
# HELP flowdesk_backup_collector_healthy Binary indicator of collector health
# TYPE flowdesk_backup_collector_healthy gauge
flowdesk_backup_collector_healthy 1
EOF
    mv "${METRICS_FILE}.$$" "$METRICS_FILE"
    chmod 644 "$METRICS_FILE"
fi

exit 0
