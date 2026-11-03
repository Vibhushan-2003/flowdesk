# FlowDesk SRE Runbook: Incident Recovery Procedures

**Document Version:** 1.0.0  
**Scope:** Specific incident triage, containment, and recovery workflows for FlowDesk infrastructure failures.

---

## Incident 1: PostgreSQL Data Loss or Database Corruption

### Severity: CRITICAL (Sev-1)
### Symptoms:
- Spring Boot backend reports `PSQLException: connection refused` or `FATAL: database "flowdesk" does not exist`.
- `/api/health/ready` returns HTTP 503 with database health check down.
- Data table corruption errors in PostgreSQL logs (`pg_log`).

### Recovery Workflow:
1. **Quarantine & Ingress Cutoff:**
   Stop Caddy immediately to avoid accepting client writes that would fail or worsen data divergence:
   ```bash
   docker stop flowdesk-prod-caddy flowdesk-prod-backend
   ```
2. **Inspect Existing Database Volume:**
   Check PostgreSQL logs to determine failure cause (disk error, WAL corruption, manual drop):
   ```bash
   docker logs flowdesk-prod-postgres --tail 100
   ```
3. **Point-in-Time Assessment:**
   Identify the most recent verified backup:
   ```bash
   LATEST_BACKUP=$(ls -td /var/backups/flowdesk/flowdesk_backup_* | head -n 1)
   cat "$LATEST_BACKUP/manifest.json"
   ```
4. **Clean Schema / Database Reset:**
   Reset the PostgreSQL database to a clean initial state:
   ```bash
   docker exec -i flowdesk-prod-postgres psql -U flowdesk_app -d postgres -c "DROP DATABASE IF EXISTS flowdesk;"
   docker exec -i flowdesk-prod-postgres psql -U flowdesk_app -d postgres -c "CREATE DATABASE flowdesk OWNER flowdesk_app;"
   ```
5. **Execute Safe Restore:**
   ```bash
   /opt/flowdesk/scripts/sre/flowdesk-restore.sh \
     --backup-dir "$LATEST_BACKUP" \
     --use-docker true \
     --target-db flowdesk \
     --target-attachments-dir /var/lib/docker/volumes/flowdesk_prod_attachments/_data \
     --allow-production-overwrite \
     --confirm-destructive RESTORE-PRODUCTION-DATA-DESTRUCTION
   ```
6. **Resume Services & Healthcheck:**
   ```bash
   docker start flowdesk-prod-backend
   # Wait for readiness
   curl -fsS http://localhost:8080/api/health/ready
   docker start flowdesk-prod-caddy
   ```

---

## Incident 2: Missing or Corrupted Attachment Files

### Severity: MAJOR (Sev-2)
### Symptoms:
- Users receive HTTP 404 or `StorageException: Attachment file not found` when downloading ticket attachments.
- ClamAV or Attachment Rescan reports missing physical files.

### Recovery Workflow:
1. **Identify Missing Storage Keys:**
   Query database to identify orphaned rows:
   ```bash
   docker exec -i flowdesk-prod-postgres psql -U flowdesk_app -d flowdesk -c "
     SELECT id, ticket_id, storage_key, checksum_sha256 FROM ticket_attachments WHERE status != 'DELETED';
   "
   ```
2. **Inspect Backup Sets for Target Keys:**
   Search prior backup archives for the missing opaque `{UUID}.dat` files:
   ```bash
   for b in /var/backups/flowdesk/flowdesk_backup_*/attachments.tar.gz; do
       echo "Checking $b:"
       tar -tzf "$b" | grep "<MISSING-STORAGE-KEY>" || true
   done
   ```
3. **Extract Individual File into Production Attachment Volume:**
   ```bash
   tar -xzf /var/backups/flowdesk/<FOUND-BACKUP>/attachments.tar.gz \
     -C /var/lib/docker/volumes/flowdesk_prod_attachments/_data \
     --strip-components=1 "attachments/<MISSING-STORAGE-KEY>"
   
   chmod 600 "/var/lib/docker/volumes/flowdesk_prod_attachments/_data/<MISSING-STORAGE-KEY>"
   ```
4. **Trigger Admin ClamAV Rescan:**
   Use the FlowDesk Admin Attachment Rescan API to verify file integrity and malware status.

---

## Incident 3: Host Storage Full (/var/backups or Root Filesystem)

### Severity: MAJOR (Sev-2)
### Symptoms:
- Backup fails with: `[ERROR] Insufficient disk space on /var/backups/flowdesk: ...MB available`.
- Docker containers fail to write logs or temp files.

### Recovery Workflow:
1. **Triage Disk Space by Directory:**
   ```bash
   df -h
   du -sh /var/backups/flowdesk/* | sort -hr
   du -sh /var/lib/docker/* | sort -hr
   ```
2. **Prune Old Backups Beyond Policy:**
   ```bash
   # Remove backups older than 7 days if retention script failed
   find /var/backups/flowdesk -maxdepth 1 -type d -name "flowdesk_backup_*" -mtime +7 -exec rm -rf {} +
   ```
3. **Prune Docker Build Cache & Dangling Images:**
   ```bash
   docker system prune -f --volumes=false
   ```
4. **Verify Storage Headroom:**
   Ensure at least **15% headroom** remains on the primary disk volume.

---

## Incident 4: Automated Backup Job Failure or Lock Collision

### Severity: WARNING / MAJOR
### Symptoms:
- Systemd reports: `flowdesk-backup.service: Failed with result 'exit-code'`.
- Journal logs show: `[ERROR] Another FlowDesk backup process is actively running (lock held on /tmp/flowdesk_backup.lock)`.

### Recovery Workflow:
1. **Check for Running Backup Processes:**
   ```bash
   ps aux | grep -E 'flowdesk-backup|pg_dump|tar'
   ```
2. **Investigate Stale Lock:**
   If no backup process is actually running (e.g. following an abrupt server reboot), remove the stale lockfile:
   ```bash
   rm -f /tmp/flowdesk_backup.lock
   ```
3. **Review Detailed Error in Journal:**
   ```bash
   journalctl -u flowdesk-backup.service --no-pager -n 50
   ```
4. **Trigger Manual Run to Confirm Resolution:**
   ```bash
   systemctl start flowdesk-backup.service
   systemctl status flowdesk-backup.service
   ```

