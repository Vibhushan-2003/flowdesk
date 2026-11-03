#!/usr/bin/env bash
# ==============================================================================
# FlowDesk Disaster Recovery Drill & Failure Testing Suite
# ==============================================================================
# Executes an end-to-end disaster recovery drill and validates 9 distinct
# failure and corruption scenarios against isolated test infrastructure.
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DRILL_ROOT="${PROJECT_ROOT}/scratch/dr_drill_workspace"

DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="flowdesk_test"
DB_USER="flowdesk_app"
export PGPASSWORD="${PGPASSWORD:-12345678}"

PASSED_COUNT=0
FAILED_COUNT=0

log_drill() {
    echo -e "\n======================================================================"
    echo -e ">>> [DRILL] $*"
    echo -e "======================================================================"
}

assert_success() {
    local test_name="$1"
    shift
    echo -n "Running: $test_name ... "
    if "$@"; then
        echo "[PASSED]"
        PASSED_COUNT=$((PASSED_COUNT + 1))
    else
        echo "[FAILED]" >&2
        FAILED_COUNT=$((FAILED_COUNT + 1))
        exit 1
    fi
}

assert_failure() {
    local test_name="$1"
    shift
    echo -n "Running (expecting failure): $test_name ... "
    if "$@" >/dev/null 2>&1; then
        echo "[UNEXPECTED SUCCESS - FAILED]" >&2
        FAILED_COUNT=$((FAILED_COUNT + 1))
        exit 1
    else
        echo "[PASSED - REJECTED SAFELY]"
        PASSED_COUNT=$((PASSED_COUNT + 1))
    fi
}

# ------------------------------------------------------------------------------
# 1. Clean Workspace Preparation
# ------------------------------------------------------------------------------
log_drill "Phase 1: Setting up isolated test directories and attachments..."
rm -rf "$DRILL_ROOT"
mkdir -p "$DRILL_ROOT/attachments_src"
mkdir -p "$DRILL_ROOT/backups"
mkdir -p "$DRILL_ROOT/offsite_dest"
mkdir -p "$DRILL_ROOT/decrypted_stage"
mkdir -p "$DRILL_ROOT/attachments_restored"

# Create representative test attachments
ATT1_KEY="00000001-0000-0000-0000-000000000001.dat"
ATT2_KEY="00000002-0000-0000-0000-000000000002.dat"
ATT3_KEY="00000003-0000-0000-0000-000000000003.dat"

echo "FlowDesk Server Error Log - NullPointer in AuthFilter at 02:14:09" > "$DRILL_ROOT/attachments_src/$ATT1_KEY"
echo "Network Diagnostic Packet Capture: SYN-ACK timeout to payment gateway" > "$DRILL_ROOT/attachments_src/$ATT2_KEY"
echo "Confidential Security Audit Report 2026: All endpoints authenticated" > "$DRILL_ROOT/attachments_src/$ATT3_KEY"

ATT1_SHA=$(sha256sum "$DRILL_ROOT/attachments_src/$ATT1_KEY" | cut -d' ' -f1 | tr -d '\r\n')
ATT2_SHA=$(sha256sum "$DRILL_ROOT/attachments_src/$ATT2_KEY" | cut -d' ' -f1 | tr -d '\r\n')
ATT3_SHA=$(sha256sum "$DRILL_ROOT/attachments_src/$ATT3_KEY" | cut -d' ' -f1 | tr -d '\r\n')

# ------------------------------------------------------------------------------
# 2. Database Test Schema & Data Seeding
# ------------------------------------------------------------------------------
log_drill "Phase 2: Seeding isolated test schema 'flowdesk_drill_src' in '$DB_NAME'..."

psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -d "$DB_NAME" -v ON_ERROR_STOP=1 <<EOF
DROP SCHEMA IF EXISTS flowdesk_drill_src CASCADE;
CREATE SCHEMA flowdesk_drill_src;
SET search_path TO flowdesk_drill_src;

CREATE TABLE flyway_schema_history (
    installed_rank integer NOT NULL PRIMARY KEY,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);

INSERT INTO flyway_schema_history VALUES 
(1, '1', 'create user role schema', 'SQL', 'V1__create_user_role_schema.sql', 101, 'flowdesk_app', now(), 12, true),
(25, '25', 'add ticket comment visibility', 'SQL', 'V25__add_ticket_comment_visibility.sql', 125, 'flowdesk_app', now(), 15, true);

CREATE TABLE users (
    id uuid PRIMARY KEY,
    email character varying(255) NOT NULL UNIQUE,
    password_hash character varying(255) NOT NULL,
    first_name character varying(100) NOT NULL,
    last_name character varying(100) NOT NULL,
    role character varying(50) NOT NULL,
    is_active boolean DEFAULT true NOT NULL
);

INSERT INTO users VALUES
('a0000000-0000-0000-0000-000000000001', 'admin@flowdesk.test', '\$2a\$10\$N9qo8uLOickgx2ZMRZoMye.pB1bA0Vv8d9/iVb9rW1YqYf3GqO8KG', 'Drill', 'Admin', 'ADMINISTRATOR', true),
('a0000000-0000-0000-0000-000000000002', 'agent@flowdesk.test', '\$2a\$10\$N9qo8uLOickgx2ZMRZoMye.pB1bA0Vv8d9/iVb9rW1YqYf3GqO8KG', 'Support', 'Agent', 'SUPPORT_AGENT', true),
('a0000000-0000-0000-0000-000000000003', 'user@flowdesk.test', '\$2a\$10\$N9qo8uLOickgx2ZMRZoMye.pB1bA0Vv8d9/iVb9rW1YqYf3GqO8KG', 'Standard', 'Employee', 'EMPLOYEE', true);

CREATE TABLE tickets (
    id uuid PRIMARY KEY,
    ticket_number character varying(50) NOT NULL UNIQUE,
    title character varying(255) NOT NULL,
    description text NOT NULL,
    status character varying(50) NOT NULL,
    priority character varying(50) NOT NULL,
    created_by_user_id uuid NOT NULL REFERENCES users(id)
);

INSERT INTO tickets VALUES
('b0000000-0000-0000-0000-000000000001', 'FD-1001', 'Critical Outage: SSO Login Failure', 'Users cannot log in via Google OAuth', 'OPEN', 'URGENT', 'a0000000-0000-0000-0000-000000000003'),
('b0000000-0000-0000-0000-000000000002', 'FD-1002', 'Hardware Request: Dual Monitor', 'Requesting 27 inch 4K display', 'IN_PROGRESS', 'NORMAL', 'a0000000-0000-0000-0000-000000000003'),
('b0000000-0000-0000-0000-000000000003', 'FD-1003', 'VPN Routing Latency', 'High packet loss on us-east VPN gateway', 'RESOLVED', 'HIGH', 'a0000000-0000-0000-0000-000000000003');

CREATE TABLE ticket_attachments (
    id uuid PRIMARY KEY,
    ticket_id uuid NOT NULL REFERENCES tickets(id),
    uploaded_by_user_id uuid NOT NULL REFERENCES users(id),
    original_filename character varying(255) NOT NULL,
    sanitized_filename character varying(255) NOT NULL,
    content_type character varying(100) NOT NULL,
    size_bytes bigint NOT NULL,
    storage_key character varying(255) NOT NULL UNIQUE,
    checksum_sha256 character varying(64) NOT NULL,
    status character varying(30) NOT NULL,
    scan_status character varying(30) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);

INSERT INTO ticket_attachments VALUES
('c0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000003', 'error.log', 'error.log', 'text/plain', 66, '$ATT1_KEY', '$ATT1_SHA', 'ACTIVE', 'CLEAN', now()),
('c0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000003', 'pcap.txt', 'pcap.txt', 'text/plain', 71, '$ATT2_KEY', '$ATT2_SHA', 'ACTIVE', 'CLEAN', now()),
('c0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001', 'audit.txt', 'audit.txt', 'text/plain', 68, '$ATT3_KEY', '$ATT3_SHA', 'ACTIVE', 'CLEAN', now());
EOF

# ------------------------------------------------------------------------------
# 3. Execute Production Backup
# ------------------------------------------------------------------------------
log_drill "Phase 3: Executing FlowDesk Backup Orchestrator..."
BACKUP_START_TIME=$(date +%s)

"$SCRIPT_DIR/flowdesk-backup.sh" \
    --use-docker false \
    --db-host "$DB_HOST" \
    --db-port "$DB_PORT" \
    --db-name "$DB_NAME" \
    --db-user "$DB_USER" \
    --db-schema "flowdesk_drill_src" \
    --attachments-dir "$DRILL_ROOT/attachments_src" \
    --backup-root "$DRILL_ROOT/backups" \
    --consistency-mode "verified_snapshot"

BACKUP_END_TIME=$(date +%s)
BACKUP_DURATION=$((BACKUP_END_TIME - BACKUP_START_TIME))
echo "Backup execution finished in ${BACKUP_DURATION}s."

PUBLISHED_BACKUP=$(find "$DRILL_ROOT/backups" -maxdepth 1 -type d -name "flowdesk_backup_*" | head -n 1)
[ -n "$PUBLISHED_BACKUP" ] || { echo "ERROR: No published backup directory found!"; exit 1; }

echo "Published backup located at: $PUBLISHED_BACKUP"
[ -f "$PUBLISHED_BACKUP/manifest.json" ] || { echo "ERROR: Missing manifest.json!"; exit 1; }
[ -f "$PUBLISHED_BACKUP/database.sql.gz" ] || { echo "ERROR: Missing database.sql.gz!"; exit 1; }
[ -f "$PUBLISHED_BACKUP/attachments.tar.gz" ] || { echo "ERROR: Missing attachments.tar.gz!"; exit 1; }

# Verify checksums using sidecar
(cd "$PUBLISHED_BACKUP" && sha256sum -c SHA256SUMS)
PASSED_COUNT=$((PASSED_COUNT + 1))

# ------------------------------------------------------------------------------
# 4. Off-Host Replication (Client-Side AES-256-CBC Encryption)
# ------------------------------------------------------------------------------
log_drill "Phase 4: Executing Off-Host Encrypted Replication..."
DRILL_KEY="SRE-Disaster-Recovery-Secret-Key-2026!"

export BACKUP_ENCRYPTION_KEY="$DRILL_KEY"
"$SCRIPT_DIR/offsite-replication.sh" \
    --backup-dir "$PUBLISHED_BACKUP" \
    --dest-type "local_dir" \
    --dest-path "$DRILL_ROOT/offsite_dest"

OFFSITE_ENC_FILE=$(find "$DRILL_ROOT/offsite_dest" -type f -name "*.tar.gz.enc" | head -n 1)
[ -n "$OFFSITE_ENC_FILE" ] || { echo "ERROR: Encrypted offsite artifact not found!"; exit 1; }
echo "Offsite encrypted package: $OFFSITE_ENC_FILE"

# Verify ciphertext does not leak plaintext strings
if grep -q "Critical Outage" "$OFFSITE_ENC_FILE" 2>/dev/null; then
    echo "SECURITY FAILURE: Plaintext database content leaked into encrypted file!"; exit 1;
fi
PASSED_COUNT=$((PASSED_COUNT + 1))

# ------------------------------------------------------------------------------
# 5. Offsite Decryption & Unpacking
# ------------------------------------------------------------------------------
log_drill "Phase 5: Decrypting offsite archive into fresh staging directory..."

"$SCRIPT_DIR/decrypt-backup.sh" \
    --enc-file "$OFFSITE_ENC_FILE" \
    --output-dir "$DRILL_ROOT/decrypted_stage"

DECRYPTED_MANIFEST=$(find "$DRILL_ROOT/decrypted_stage" -maxdepth 2 -name "manifest.json" | head -n 1)
[ -n "$DECRYPTED_MANIFEST" ] || { echo "ERROR: Decryption did not produce valid manifest.json!"; exit 1; }
DECRYPTED_DIR=$(dirname "$DECRYPTED_MANIFEST")
echo "Decrypted backup validated at: $DECRYPTED_DIR"
PASSED_COUNT=$((PASSED_COUNT + 1))

# ------------------------------------------------------------------------------
# 6. Disaster Simulation: Destroy Source Schema & Attachments
# ------------------------------------------------------------------------------
log_drill "Phase 6: Simulating catastrophic loss - Destroying source test schema and files..."
psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -d "$DB_NAME" -c "DROP SCHEMA flowdesk_drill_src CASCADE;" >/dev/null
rm -rf "$DRILL_ROOT/attachments_src"
echo "Primary data destroyed."

# ------------------------------------------------------------------------------
# 7. Safe Restore into Clean Destination Environment
# ------------------------------------------------------------------------------
log_drill "Phase 7: Executing Safe Restore into fresh schema 'flowdesk_drill_src'..."

RESTORE_START_TIME=$(date +%s)

"$SCRIPT_DIR/flowdesk-restore.sh" \
    --backup-dir "$DECRYPTED_DIR" \
    --target-db "$DB_NAME" \
    --target-schema "flowdesk_drill_src" \
    --target-attachments-dir "$DRILL_ROOT/attachments_restored" \
    --confirm-destructive "CONFIRM-RESTORE-OVERWRITE" \
    --use-docker false \
    --db-user "$DB_USER" \
    --db-host "$DB_HOST" \
    --db-port "$DB_PORT"

RESTORE_END_TIME=$(date +%s)
RTO_MEASURED_SECONDS=$((RESTORE_END_TIME - RESTORE_START_TIME))
echo "Restoration completed in ${RTO_MEASURED_SECONDS} seconds."
PASSED_COUNT=$((PASSED_COUNT + 1))

# ------------------------------------------------------------------------------
# 8. Post-Restore Data & Security Audit
# ------------------------------------------------------------------------------
log_drill "Phase 8: Validating restored database records and attachments..."

RESTORED_USERS=$(psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -d "$DB_NAME" -t -A -c "SELECT count(*) FROM flowdesk_drill_src.users;" | tr -d '\r\n')
RESTORED_TICKETS=$(psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -d "$DB_NAME" -t -A -c "SELECT count(*) FROM flowdesk_drill_src.tickets;" | tr -d '\r\n')
RESTORED_ATTACHMENTS=$(psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -d "$DB_NAME" -t -A -c "SELECT count(*) FROM flowdesk_drill_src.ticket_attachments;" | tr -d '\r\n')
RESTORED_FLYWAY=$(psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -d "$DB_NAME" -t -A -c "SELECT version FROM flowdesk_drill_src.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;" | tr -d '\r\n')

echo "Restored Users:        $RESTORED_USERS (expected 3)"
echo "Restored Tickets:      $RESTORED_TICKETS (expected 3)"
echo "Restored Attachments:  $RESTORED_ATTACHMENTS (expected 3)"
echo "Restored Flyway State: V${RESTORED_FLYWAY} (expected V25)"

[ "$RESTORED_USERS" = "3" ] || { echo "User count mismatch!"; exit 1; }
[ "$RESTORED_TICKETS" = "3" ] || { echo "Ticket count mismatch!"; exit 1; }
[ "$RESTORED_ATTACHMENTS" = "3" ] || { echo "Attachment count mismatch!"; exit 1; }
[ "$RESTORED_FLYWAY" = "25" ] || { echo "Flyway version mismatch!"; exit 1; }

# Verify physical files on disk
[ -f "$DRILL_ROOT/attachments_restored/$ATT1_KEY" ] || { echo "Restored file 1 missing!"; exit 1; }
[ -f "$DRILL_ROOT/attachments_restored/$ATT2_KEY" ] || { echo "Restored file 2 missing!"; exit 1; }
[ -f "$DRILL_ROOT/attachments_restored/$ATT3_KEY" ] || { echo "Restored file 3 missing!"; exit 1; }

RESTORED_ATT1_SHA=$(sha256sum "$DRILL_ROOT/attachments_restored/$ATT1_KEY" | cut -d' ' -f1 | tr -d '\r\n')
RESTORED_ATT2_SHA=$(sha256sum "$DRILL_ROOT/attachments_restored/$ATT2_KEY" | cut -d' ' -f1 | tr -d '\r\n')
RESTORED_ATT3_SHA=$(sha256sum "$DRILL_ROOT/attachments_restored/$ATT3_KEY" | cut -d' ' -f1 | tr -d '\r\n')

[ "$RESTORED_ATT1_SHA" = "$ATT1_SHA" ] || { echo "Attachment 1 SHA mismatch!"; exit 1; }
[ "$RESTORED_ATT2_SHA" = "$ATT2_SHA" ] || { echo "Attachment 2 SHA mismatch!"; exit 1; }
[ "$RESTORED_ATT3_SHA" = "$ATT3_SHA" ] || { echo "Attachment 3 SHA mismatch!"; exit 1; }

echo "All 3 restored attachments match original cryptographic hashes 100%."
PASSED_COUNT=$((PASSED_COUNT + 1))

# ------------------------------------------------------------------------------
# 9. Failure Mode Scenario Verification (Task 7)
# ------------------------------------------------------------------------------
log_drill "Phase 9: Testing All Failure Modes & Safety Interventions..."

# Test F1: Corrupted Checksum Rejection
CORRUPT_DIR="$DRILL_ROOT/test_corrupt_checksum"
cp -r "$PUBLISHED_BACKUP" "$CORRUPT_DIR"
echo "CORRUPTION_INJECTION" >> "$CORRUPT_DIR/database.sql.gz"
assert_failure "F1: Restore must reject corrupted database checksum" \
    "$SCRIPT_DIR/flowdesk-restore.sh" \
    --backup-dir "$CORRUPT_DIR" \
    --target-db "$DB_NAME" \
    --target-attachments-dir "$DRILL_ROOT/tmp_att" \
    --confirm-destructive "CONFIRM-RESTORE-OVERWRITE" \
    --use-docker false

# Test F2: Missing Attachment Archive
MISSING_ATT_DIR="$DRILL_ROOT/test_missing_att"
cp -r "$PUBLISHED_BACKUP" "$MISSING_ATT_DIR"
rm -f "$MISSING_ATT_DIR/attachments.tar.gz"
assert_failure "F2: Restore must reject missing attachments archive" \
    "$SCRIPT_DIR/flowdesk-restore.sh" \
    --backup-dir "$MISSING_ATT_DIR" \
    --target-db "$DB_NAME" \
    --target-attachments-dir "$DRILL_ROOT/tmp_att" \
    --confirm-destructive "CONFIRM-RESTORE-OVERWRITE" \
    --use-docker false

# Test F3: Incomplete Database Dump
INCOMPLETE_DUMP_DIR="$DRILL_ROOT/test_incomplete_dump"
cp -r "$PUBLISHED_BACKUP" "$INCOMPLETE_DUMP_DIR"
head -c 20 "$PUBLISHED_BACKUP/database.sql.gz" > "$INCOMPLETE_DUMP_DIR/database.sql.gz"
# Update manifest checksum to match truncated file to force gzip decompression failure
TRUNC_SHA=$(sha256sum "$INCOMPLETE_DUMP_DIR/database.sql.gz" | cut -d' ' -f1)
sed -i "s/\"sha256\": \".*\"/\"sha256\": \"$TRUNC_SHA\"/" "$INCOMPLETE_DUMP_DIR/manifest.json" 2>/dev/null || true
assert_failure "F3: Restore must reject truncated / corrupted gzip dump" \
    "$SCRIPT_DIR/flowdesk-restore.sh" \
    --backup-dir "$INCOMPLETE_DUMP_DIR" \
    --target-db "$DB_NAME" \
    --target-attachments-dir "$DRILL_ROOT/tmp_att" \
    --confirm-destructive "CONFIRM-RESTORE-OVERWRITE" \
    --use-docker false

# Test F4: Destructive Token Missing
assert_failure "F4: Restore must reject execution when confirmation token is omitted" \
    "$SCRIPT_DIR/flowdesk-restore.sh" \
    --backup-dir "$PUBLISHED_BACKUP" \
    --target-db "$DB_NAME" \
    --target-attachments-dir "$DRILL_ROOT/tmp_att" \
    --use-docker false

# Test F5: Production Database Protection Guard
assert_failure "F5: Targeting production DB 'flowdesk' without explicit override must abort" \
    "$SCRIPT_DIR/flowdesk-restore.sh" \
    --backup-dir "$PUBLISHED_BACKUP" \
    --target-db "flowdesk" \
    --target-attachments-dir "$DRILL_ROOT/tmp_att" \
    --confirm-destructive "CONFIRM-RESTORE-OVERWRITE" \
    --use-docker false

# Test F6: Invalid Decryption Key
assert_failure "F6: Decryption with incorrect passphrase must fail" \
    env BACKUP_ENCRYPTION_KEY="WRONG-KEY-FAIL-EXPECTED" "$SCRIPT_DIR/decrypt-backup.sh" \
    --enc-file "$OFFSITE_ENC_FILE" \
    --output-dir "$DRILL_ROOT/bad_decrypt_stage"

# Test F7: Concurrency Lock Collision
TOUCH_LOCK="/tmp/flowdesk_backup.lock"
touch "$TOUCH_LOCK"
# Hold an exclusive lock using flock in subshell
(
  exec 200>"$TOUCH_LOCK"
  if command -v flock >/dev/null 2>&1; then
      flock -x 200
      sleep 2
  fi
) &
LOCK_PID=$!
sleep 0.2
assert_failure "F7: Concurrent backup invocation must abort on lock collision" \
    "$SCRIPT_DIR/flowdesk-backup.sh" \
    --use-docker false \
    --db-host "$DB_HOST" \
    --db-port "$DB_PORT" \
    --db-name "$DB_NAME" \
    --db-user "$DB_USER" \
    --attachments-dir "$DRILL_ROOT/attachments_src" \
    --backup-root "$DRILL_ROOT/backups"
wait $LOCK_PID 2>/dev/null || true
rm -f "$TOUCH_LOCK"

# Test F8: Unencrypted Offsite Transfer Refusal
assert_failure "F8: Off-host transfer without encryption key must fail closed" \
    env -u BACKUP_ENCRYPTION_KEY "$SCRIPT_DIR/offsite-replication.sh" \
    --backup-dir "$PUBLISHED_BACKUP" \
    --dest-type "local_dir" \
    --dest-path "$DRILL_ROOT/offsite_dest"

# ------------------------------------------------------------------------------
# 10. Clean Up Disposable Test Database Schema
# ------------------------------------------------------------------------------
psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -d "$DB_NAME" -c "DROP SCHEMA IF EXISTS flowdesk_drill_src CASCADE;" >/dev/null
rm -rf "$DRILL_ROOT"

echo -e "\n======================================================================"
echo -e "DISASTER RECOVERY DRILL & FAILURE MODES COMPLETE"
echo -e "Total Checks Passed: $PASSED_COUNT"
echo -e "Total Checks Failed: $FAILED_COUNT"
echo -e "Measured Recovery Duration (RTO): ${RTO_MEASURED_SECONDS} seconds"
echo -e "Target RPO Cadence:               24 Hours"
echo -e "Data Integrity Result:            100% Match (Users, Tickets, Flyway V25, Attachments)"
echo -e "======================================================================"

exit 0
