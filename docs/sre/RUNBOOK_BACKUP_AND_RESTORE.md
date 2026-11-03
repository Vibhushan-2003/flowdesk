# FlowDesk SRE Runbook: Production Backup & Safe Restore

**Document Version:** 1.0.0  
**Target Architecture:** Oracle Cloud Always Free Ampere A1 (Ubuntu 24.04 / ARM64, 2 OCPU, 12 GB RAM)  
**Service Level Objectives:**  
- **Target RPO (Recovery Point Objective):** 24 hours (daily scheduled automated backup at 02:00 UTC).  
- **Target RTO (Recovery Time Objective):** 30 minutes for small portfolio-scale datasets (subject to measured drill benchmarks).  

---

## 1. System Overview & Consistency Strategy

FlowDesk state is partitioned across two coupled stores:
1. **Relational Database:** PostgreSQL 16 storing users, tickets, comments, audit events, and attachment metadata.
2. **Blob Storage:** Persistent Docker named volume `flowdesk_prod_attachments` mounted at `/app/data/attachments` storing `{UUID}.dat` opaque binary files.

### The Referential Consistency Challenge
A standard uncoordinated `pg_dump` and independent `tar` cannot guarantee transactional consistency: an attachment uploaded while `pg_dump` executes would be recorded in the database dump but missing from the disk snapshot if taken out of order.

### Supported Consistency Strategies

#### Strategy A: `verified_snapshot` (Default, High Availability)
1. **Transactional Database Snapshot:** Dumps PostgreSQL using native `pg_dump --clean --if-exists --no-owner --no-privileges` (PostgreSQL executes dumps in a single snapshot transaction).
2. **Attachment Storage Snapshot:** Gzips all files from `/app/data/attachments`.
3. **Cross-Referential Snapshot Audit:** Parses `ticket_attachments` directly from the frozen database dump file (`database.sql.gz`) for all active attachments (`status != 'DELETED'`), extracts the attachment archive into a temporary verification sandbox, and cryptographically verifies that **every single attachment referenced in the dump exists and matches its recorded `checksum_sha256`**.
4. **Atomic Publication:** If any file is missing or corrupted, the backup aborts immediately, deletes the staging directory, records a failure metric for Prometheus, and exits with a non-zero code.

#### Strategy B: `quiescence` (Scheduled Low-Traffic Window)
1. Executes `docker pause flowdesk-prod-backend` to freeze in-flight HTTP requests and database write transactions.
2. Dumps PostgreSQL and archives attachments while the application is quiescent.
3. Resumes backend with `docker unpause flowdesk-prod-backend` in a guaranteed `trap` cleanup handler.
4. Executes the cross-referential snapshot audit against the dump file.

---

## 2. Backup Execution Procedures

### Manual Ad-Hoc Backup
To create an immediate backup before upgrades or database migrations:

```bash
# As root on the production host:
/opt/flowdesk/scripts/sre/flowdesk-backup.sh \
  --backup-root /var/backups/flowdesk \
  --use-docker true \
  --db-container flowdesk-prod-postgres \
  --backend-container flowdesk-prod-backend \
  --consistency-mode verified_snapshot
```

### Verifying a Completed Backup Set
Each published backup directory (`flowdesk_backup_YYYYMMDD_HHMMSSZ`) contains:
- `manifest.json`: Metadata, timestamps, Git commit, Flyway schema version, and cryptographic hashes.
- `database.sql.gz`: Compressed SQL dump (`0600` permissions).
- `attachments.tar.gz`: Compressed attachment archive (`0600` permissions).
- `SHA256SUMS`: Cryptographic verification sidecar.

To verify checksum integrity:
```bash
cd /var/backups/flowdesk/flowdesk_backup_20261103_020000Z
sha256sum -c SHA256SUMS
```

---

## 3. Automated Scheduling on Ubuntu ARM64

### Systemd Timer Installation (Recommended)
Systemd timers are the standard scheduling mechanism on Ubuntu ARM64, offering missed-run catch-up (`Persistent=true`), randomized jitter, and integrated journal logging.

1. Install the configuration file:
   ```bash
   sudo mkdir -p /etc/flowdesk
   sudo cp /opt/flowdesk/scripts/sre/flowdesk-backup.env.example /etc/flowdesk/backup.env
   sudo chmod 600 /etc/flowdesk/backup.env
   ```
2. Install systemd unit files:
   ```bash
   sudo cp /opt/flowdesk/scripts/sre/systemd/flowdesk-backup.service /etc/systemd/system/
   sudo cp /opt/flowdesk/scripts/sre/systemd/flowdesk-backup.timer /etc/systemd/system/
   sudo systemctl daemon-reload
   ```
3. Enable and start the timer:
   ```bash
   sudo systemctl enable --now flowdesk-backup.timer
   ```
4. Verify timer status:
   ```bash
   systemctl list-timers flowdesk-backup.timer
   ```

### Reviewing Backup Execution Logs
```bash
journalctl -u flowdesk-backup.service -n 100 --no-pager
```

---

## 4. Safe Restore Procedures

### Absolute Safety Guardrails
The restore orchestrator [`scripts/sre/flowdesk-restore.sh`](file:///C:/Users/gvibh/Projects/flowdesk/scripts/sre/flowdesk-restore.sh) enforces strict safety controls:
1. **Never Defaults to Production:** Mandatory `--target-db` and `--target-attachments-dir`.
2. **Explicit Confirmation Required:** Mandatory `--confirm-destructive` token.
3. **Production Overwrite Lock:** Targeting `flowdesk` requires both `--allow-production-overwrite` and `--confirm-destructive RESTORE-PRODUCTION-DATA-DESTRUCTION`.
4. **Pre-flight Integrity Check:** Checksums are recalculated and validated against `manifest.json` before any destructive operation commences.

### Scenario A: Restoring to an Isolated Test / Validation Environment
To validate a backup without touching production:
```bash
/opt/flowdesk/scripts/sre/flowdesk-restore.sh \
  --backup-dir /var/backups/flowdesk/flowdesk_backup_20261103_020000Z \
  --target-db flowdesk_restore_test \
  --target-attachments-dir /tmp/flowdesk_restore_test_attachments \
  --confirm-destructive CONFIRM-RESTORE-OVERWRITE
```

### Scenario B: Restoring from Last Known Good Backup (Production Recovery)
In the event of catastrophic data loss where production must be restored:

1. Stop web ingress to prevent incoming writes:
   ```bash
   docker stop flowdesk-prod-caddy flowdesk-prod-backend
   ```
2. Identify the latest valid backup:
   ```bash
   LATEST_BACKUP=$(ls -td /var/backups/flowdesk/flowdesk_backup_* | head -n 1)
   echo "Restoring from: $LATEST_BACKUP"
   ```
3. Execute the restore:
   ```bash
   /opt/flowdesk/scripts/sre/flowdesk-restore.sh \
     --backup-dir "$LATEST_BACKUP" \
     --use-docker true \
     --target-db flowdesk \
     --target-attachments-dir /var/lib/docker/volumes/flowdesk_prod_attachments/_data \
     --allow-production-overwrite \
     --confirm-destructive RESTORE-PRODUCTION-DATA-DESTRUCTION
   ```
4. Start backend and verify health:
   ```bash
   docker start flowdesk-prod-backend
   # Verify health probe inside container (port 8080 is internal to Docker and unpublished on host):
   docker exec flowdesk-prod-backend wget -qO- http://127.0.0.1:8080/api/health/ready
   # Or inspect container health status until healthy:
   docker inspect --format='{{.State.Health.Status}}' flowdesk-prod-backend
   ```
5. Resume edge ingress:
   ```bash
   docker start flowdesk-prod-caddy
   # Verify through edge HTTPS ingress:
   curl -k -fsS https://${CADDY_DOMAIN:-localhost}/api/health/ready
   ```

---

## 5. Monitoring & Alerting Integration

When configured with `METRICS_DIR=/var/lib/node_exporter/textfile_collector`, the backup orchestrator emits Prometheus metrics:
- `flowdesk_backup_last_success_timestamp`: Unix epoch of the last successful backup.
- `flowdesk_backup_duration_seconds`: Runtime duration in seconds.
- `flowdesk_backup_size_bytes`: Total byte size of the backup set.
- `flowdesk_backup_status`: Binary gauge (`1` = success, `0` = failure).

### Prometheus SRE Alert Rule: Missed or Failing Backup
Add to [`monitoring/prometheus/alert_rules.yml`](file:///C:/Users/gvibh/Projects/flowdesk/monitoring/prometheus/alert_rules.yml):
```yaml
- alert: FlowDeskBackupMissingOrFailed
  expr: (time() - flowdesk_backup_last_success_timestamp > 93600) or (flowdesk_backup_status == 0) or (absent(flowdesk_backup_collector_healthy) == 1)
  for: 15m
  labels:
    severity: critical
    service: flowdesk-backup
  annotations:
    summary: "FlowDesk production backup is missing or failed"
    description: "No successful FlowDesk backup has completed in the last 26 hours, or the last backup attempt failed, or the metrics collector is down."
    action: "Check systemd journal on host: journalctl -u flowdesk-backup.service -xe"
```

