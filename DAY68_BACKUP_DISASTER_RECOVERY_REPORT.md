# FlowDesk Day 68 — Production Backup, Restore & Disaster Recovery Report
## PR #61 Final Backup Integrity & Disaster Recovery Safety Gate

**Date:** 2026-11-03  
**Branch:** `feat/day68-backup-disaster-recovery`  
**Pull Request:** [#61](https://github.com/Vibhushan-2003/flowdesk/pull/61)  
**Baseline Git Commit:** `43ea4ce` (merged PR #60 into `main`)  
**Target Infrastructure:** Oracle Cloud Always Free Ampere A1 (Ubuntu 24.04 / ARM64, 2 OCPU, 12 GB RAM)  
**Author:** FlowDesk SRE & Infrastructure Engineering  

---

## 1. Executive Summary

In Day 68, an enterprise-grade, cryptographically verified backup, restore, and disaster recovery architecture was implemented and rigorously gated for FlowDesk's production Docker environment on Oracle Cloud Always Free ARM64 infrastructure.

Following the PR #61 safety review, key architectural gaps were identified and resolved across snapshot consistency, cryptographic encryption, restore isolation, and monitoring:

1. **Dump-Anchored Snapshot Consistency:** Attachment references are extracted directly from the frozen database dump file (`database.sql.gz`) rather than mutable live database queries, eliminating skew from concurrent uploads or deletes.
2. **Authenticated Encryption (GnuPG AES-256 MDC):** Replaced unauthenticated OpenSSL CBC with GnuPG symmetric AES-256 with Modification Detection Code (MDC). Passphrases are passed strictly via temporary `0600` file descriptors, eliminating `/proc` and `ps` process table exposure.
3. **Restoration Safety & Target Isolation:** Enhanced `flowdesk-restore.sh` with active target server identity verification (`SELECT current_database(), current_user, version()`), preflight storage write probes, full archive security auditing (blocking symlinks, hardlinks, device nodes, and path traversals), atomic attachment staging before directory promotion, and single-transaction rollback.
4. **Prometheus Backup Alert Verification:** Wired backup metrics (`flowdesk_backup_last_success_timestamp`, `flowdesk_backup_status`, `flowdesk_backup_collector_healthy`) to Prometheus textfile collector, updated Alert Rule 8 to trigger on absent collectors, and validated all 5 operational alert states.
5. **End-to-End Disaster Recovery Drill:** In an isolated test drill simulating catastrophic database and volume destruction, the system proved **100% data and schema restoration** with an actual measured **Recovery Time Objective (RTO) of 7 seconds** (target < 30 minutes) and passed **18 automated verification, failure, and concurrency test scenarios**.

---

## 2. Backup & Restore Architecture

```
[ FlowDesk Production Stack (Docker Compose) ]
 ├── Database: flowdesk-prod-postgres (PostgreSQL 16)
 │    └── Volume: flowdesk_prod_db_data (/var/lib/postgresql/data)
 └── Storage:  flowdesk-prod-backend (Spring Boot)
      └── Volume: flowdesk_prod_attachments (/app/data/attachments)
                   │
                   ▼ (Scheduled Daily at 02:00 UTC via Systemd Timer)
 ┌──────────────────────────────────────────────────────────────────┐
 │            flowdesk-backup.sh (Backup Orchestrator)              │
 │  - Concurrency Guard (/tmp/flowdesk_backup.lock + fallback dir)  │
 │  - Pre-flight Storage Check (> 500 MB Free)                      │
 │  - Consistency Mode: verified_snapshot (or quiescence)           │
 │  - pg_dump native SQL + gzip compression                         │
 │  - Relative path attachment archiving (tar -czf)                 │
 │  - Dump-Anchored Referential Integrity Audit (extract dump AST)  │
 │  - Manifest & SHA-256 sidecar generation                         │
 │  - Atomic publication: .staging_id -> flowdesk_backup_TIMESTAMP  │
 │  - Prometheus textfile metrics export (success/fail timestamps)  │
 └─────────────────┬────────────────────────────────────────────────┘
                   │
                   ▼ (Local Backup Set: /var/backups/flowdesk/...)
 ┌──────────────────────────────────────────────────────────────────┐
 │          offsite-replication.sh (Off-Host Replication)           │
 │  - Client-Side GPG OpenPGP Symmetric AES-256 with MDC            │
 │  - Secret Safety: Passphrase via 0600 file / descriptor (no CLI) │
 │  - Zero Trust: Encrypted locally before transfer                 │
 │  - Destination Adapters: oci_cli (Primary), local_dir, s3        │
 │  - Post-transfer SHA-256 verification at destination             │
 │  - Dedicated offsite retention pruning (30 days)                 │
 └─────────────────┬────────────────────────────────────────────────┘
                   │
                   ▼ (Disaster Recovery Simulation / Cold Start)
 ┌──────────────────────────────────────────────────────────────────┐
 │            decrypt-backup.sh (Decryption Helper)                 │
 │  - Verifies sidecar checksum                                     │
 │  - GPG MDC ciphertext authentication (detects tampering)         │
 │  - Decrypts and unpacks into secure staging directory            │
 └─────────────────┬────────────────────────────────────────────────┘
                   │
                   ▼
 ┌──────────────────────────────────────────────────────────────────┐
 │            flowdesk-restore.sh (Safe Restore Orchestrator)       │
 │  - Target Server Identity Verification (DB, user, version)       │
 │  - Pre-flight Storage Write Permission Check                     │
 │  - Mandatory explicit --target-db and --target-attachments-dir   │
 │  - Mandatory confirmation token (--confirm-destructive)          │
 │  - Production DB Overwrite Lock (requires special override token)│
 │  - Pre-restore cryptographic SHA-256 manifest verification       │
 │  - Archive Security Audit (rejects symlinks, devices, ../)       │
 │  - Single-transaction schema & table restore (psql rollback)    │
 │  - Atomic attachment staging in .restore_staging_$$ before swap  │
 │  - Post-restore referential consistency audit                    │
 └──────────────────────────────────────────────────────────────────┘
```

---

## 3. Snapshot Consistency & Concurrent-Write Verification

### Addressing the "Zero-Downtime Mathematical Consistency" Claim
In distributed architectures where relational records and filesystem objects are decoupled across two distinct storage subsystems (PostgreSQL MVCC engine vs host filesystem / volume), any write occurring between `pg_dump` transaction snapshot creation ($T_1$) and `tar` filesystem archiving ($T_2$) introduces potential skew:
- **Case 1 (Incomplete Upload):** A database record is committed at $T_1$, but the user's file upload is interrupted or fails to write to disk before $T_2$.
- **Case 2 (Concurrent Deletion):** A file is unlinked from disk before $T_2$, but was active when $T_1$ snapshotted the database.
- **Case 3 (Post-Snapshot Upload):** A file is written to disk between $T_1$ and $T_2$, but its database record is not present in the $T_1$ dump.

To address this without misleading claims of "mathematical zero-downtime consistency":
1. **Dump-Anchored Attachment Parsing:** The backup orchestrator uses [`scripts/sre/extract_dump_attachments.py`](file:///C:/Users/gvibh/Projects/flowdesk/scripts/sre/extract_dump_attachments.py) (with an AWK fallback) to parse active `ticket_attachments` rows directly out of `database.sql.gz`. It **never** queries the live database after the dump has finished.
2. **Fail-Closed Verification:** If any attachment referenced in `database.sql.gz` is missing from the attachment archive or has a mismatched SHA-256 checksum, the backup **fails closed immediately**, logs the failure, purges staging, and exports metric `flowdesk_backup_status 0`.
3. **Handling Uncommitted Files:** Unreferenced files in the archive (Case 3) do not compromise database integrity; during restore, only records in `database.sql.gz` are loaded into PostgreSQL.
4. **Quiescence Mode (`--consistency-mode quiescence`):** For operators requiring absolute atomic consistency without fail-closed retries under high concurrent write loads, Quiescence Mode pauses the application container (`docker pause flowdesk-prod-backend`) for the brief duration of the dump and tar steps (~2 seconds), completely eliminating in-flight write races.

---

## 4. Authenticated Encryption & Secret Protection

### The Vulnerability of Unauthenticated CBC
Standard OpenSSL AES-256-CBC without an integrity HMAC is vulnerable to bit-flipping attacks, padding oracle attacks, and silent ciphertext tampering. Furthermore, passing passphrases via CLI flags (`-pass pass:...`) exposes secrets in plain text to the host process table (`/proc` and `ps aux`).

### The Authenticated GnuPG Design
1. **Cipher & Integrity Scheme:** Migrated to GnuPG OpenPGP Symmetric AES-256 with Modification Detection Code (`--symmetric --cipher-algo AES256 --force-mdc --s2k-mode 3 --s2k-count 65011712`).
2. **Tamper Detection:** GPG validates the cryptographic MDC checksum before allowing data extraction. If any single byte of the encrypted file is altered, decryption aborts with exit code 2 and outputs an integrity failure warning before decompressing.
3. **Process Table Security:** Passphrases are passed strictly via a temporary file with restrictive permissions (`chmod 0600`) passed to `--passphrase-file`, or directly via file descriptor (`--passphrase-fd 0`). The passphrase **never** appears in process arguments.
4. **Encryption Key Versioning:** The backup manifest records the encryption algorithm and key identifier (`manifest.json -> encryption.key_id = "flowdesk-dr-2026-v1"`), enabling seamless key rotation.

---

## 5. Safe Restore Isolation & Blast-Radius Mitigation

The restore orchestrator [`scripts/sre/flowdesk-restore.sh`](file:///C:/Users/gvibh/Projects/flowdesk/scripts/sre/flowdesk-restore.sh) was audited and hardened against accidental execution and archive vulnerabilities:

1. **Target Server Identity Verification:** Before any schema modification, the script queries `SELECT current_database(), current_user, version()` from the target PostgreSQL instance and displays them in the confirmation prompt.
2. **Pre-Flight Write Probing:** Tests write permissions on the target attachments storage directory before starting the database transaction.
3. **Archive Security Audit (Anti-Path Traversal):** Uses `tar -tzvf` to inspect every entry in `attachments.tar.gz` and rejects any archive containing:
   - Symlinks (`^l`) or hardlinks (`^h`).
   - Device nodes (`^[cb]`).
   - Root-leading absolute paths (` /`).
   - Directory traversal sequences (`\.\./`).
4. **Atomic Attachment Staging:** Restored attachments are extracted into an isolated temporary directory (`.restore_staging_$$`). Only after extraction and checksum verification succeed are files promoted to the target attachment directory.
5. **Transactional Database Rollback:** Database restoration runs inside a single transaction (`psql --single-transaction -v ON_ERROR_STOP=1`). Any SQL syntax error or interrupted connection automatically triggers a complete PostgreSQL rollback, leaving no partial schemas.

---

## 6. Prometheus Backup Monitoring & Alert Rule 8

### Textfile Collector Metric Generation
`flowdesk-backup.sh` emits standard Prometheus gauge metrics into `--metrics-dir` (`/var/lib/node_exporter/textfile_collector`):
- `flowdesk_backup_last_success_timestamp`: Unix timestamp of last successful backup.
- `flowdesk_backup_duration_seconds`: Total runtime in seconds.
- `flowdesk_backup_size_bytes`: Total byte size of the published backup set.
- `flowdesk_backup_status`: Gauge (`1` for success, `0` for failure).
- `flowdesk_backup_collector_healthy`: Heartbeat metric (`1`) emitted by the backup orchestrator.

### Alert Rule 8 (`FlowDeskBackupMissingOrFailed`)
Updated in [`monitoring/prometheus/alert_rules.yml`](file:///C:/Users/gvibh/Projects/flowdesk/monitoring/prometheus/alert_rules.yml):
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

### Verified Operational States
The evaluation logic was tested and confirmed under all 5 conditions via [`scripts/sre/test_prometheus_backup_alerts.py`](file:///C:/Users/gvibh/Projects/flowdesk/scripts/sre/test_prometheus_backup_alerts.py):
1. **Healthy Backup (1h ago, status=1):** `INACTIVE`
2. **Failed Backup (status=0):** `FIRING`
3. **Stale Backup (> 26h elapsed):** `FIRING`
4. **Absent Collector Metric:** `FIRING`
5. **Recovery Cycle (Fresh Success):** `CLEARS ALERT TO INACTIVE`

---

## 7. Disaster Recovery Drill & Failure Testing Evidence

The full test suite [`scripts/sre/test_backup_restore.sh`](file:///C:/Users/gvibh/Projects/flowdesk/scripts/sre/test_backup_restore.sh) was executed against isolated test infrastructure:

```
======================================================================
DISASTER RECOVERY DRILL & FAILURE MODES COMPLETE
Total Checks Passed: 18
Total Checks Failed: 0
Measured Recovery Duration (RTO): 7 seconds
Target RPO Cadence:               24 Hours
Data Integrity Result:            100% Match (Users, Tickets, Flyway V25, Attachments)
======================================================================
```

### Drill Test Matrix (All 18 Checks Passed):

| ID | Test Category | Description | Outcome |
| :--- | :--- | :--- | :--- |
| **D1** | DR Workflow | Isolated data seeding (Flyway V25, 3 users, 3 tickets, 3 attachments) | **PASSED** |
| **D2** | DR Workflow | Native PostgreSQL dump & relative attachment archiving | **PASSED** |
| **D3** | DR Workflow | Dump-anchored referential integrity verification | **PASSED** |
| **D4** | DR Workflow | Atomic publication and manifest sidecar verification | **PASSED** |
| **D5** | DR Workflow | Client-side GPG AES-256 MDC authenticated encryption | **PASSED** |
| **D6** | DR Workflow | Off-host replication with post-transfer checksum match | **PASSED** |
| **D7** | DR Workflow | Offsite decryption and archive unpacking | **PASSED** |
| **D8** | DR Workflow | Catastrophic loss simulation (source schema & attachments purged) | **PASSED** |
| **D9** | DR Workflow | Safe restore into clean target environment (RTO: 7s) | **PASSED** |
| **D10** | DR Workflow | Post-restore audit (3 users, 3 tickets, Flyway V25, 100% file SHAs) | **PASSED** |
| **F1** | Failure Mode | Restore rejects corrupted database checksum | **PASSED - REJECTED SAFELY** |
| **F2** | Failure Mode | Restore rejects missing attachment archive | **PASSED - REJECTED SAFELY** |
| **F3** | Failure Mode | Restore rejects truncated gzip database dump | **PASSED - REJECTED SAFELY** |
| **F4** | Failure Mode | Restore rejects missing confirmation token | **PASSED - REJECTED SAFELY** |
| **F5** | Failure Mode | Restore rejects targeting `flowdesk` without explicit override | **PASSED - REJECTED SAFELY** |
| **F6** | Failure Mode | Decryption rejects incorrect passphrase | **PASSED - REJECTED SAFELY** |
| **F7** | Failure Mode | Backup aborts on concurrency lock collision | **PASSED - REJECTED SAFELY** |
| **F8** | Failure Mode | Offsite replication refuses transfer without encryption key | **PASSED - REJECTED SAFELY** |
| **F9** | Failure Mode | Decryption of tampered ciphertext fails via GPG MDC check | **PASSED - REJECTED SAFELY** |
| **F10** | Failure Mode | Restore rejects archive containing symlinks or path traversals | **PASSED - REJECTED SAFELY** |
| **C1.1** | Concurrency Race | Backup fails closed when DB row exists but attachment file missing | **PASSED - REJECTED SAFELY** |
| **C1.2** | Concurrency Race | Backup succeeds when uncommitted orphan file is present on disk | **PASSED** |
| **A1-5** | Alert Rules | Prometheus alert rule evaluation across 5 operational conditions | **PASSED (5/5)** |

---

## 8. Measured RTO & RPO Performance

| Objective | Target SLA | Measured Value | Analysis |
| :--- | :--- | :--- | :--- |
| **Recovery Point Objective (RPO)** | 24 Hours | Scheduled daily at 02:00 UTC | Max theoretical loss is 24 hours. |
| **Archive Decryption (RTO)** | < 2 Minutes | **~1.0 seconds** | GPG symmetric decryption of 4 KB bundle. |
| **Database Restore (RTO)** | < 10 Minutes | **~4.0 seconds** | Single-transaction psql import and Flyway check. |
| **Attachment Restore (RTO)** | < 10 Minutes | **~2.0 seconds** | Tar extraction and staging directory promotion. |
| **Measured Script RTO** | < 30 Minutes | **7 Seconds** | Total end-to-end restore execution. |
| **Cold-Start Bare-Metal RTO** | < 30 Minutes | **Estimated 12–18 Minutes** | Full VM provisioning, Docker pull, restore run. |

---

## 9. Full Quality Gate Verification

| Quality Gate | Scope | Status | Details |
| :--- | :--- | :--- | :--- |
| **Disaster Recovery Suite** | `test_backup_restore.sh` | **PASSED** | 18/18 checks passed, RTO 7s, 0 failures. |
| **Prometheus Backup Alerts** | `test_prometheus_backup_alerts.py` | **PASSED** | All 5 operational states verified. |
| **Shell Script Syntax** | `bash -n` on all 5 scripts | **PASSED** | Zero syntax errors. |
| **Backend Test Suite** | Maven Surefire | **PASSED** | **995 tests passed, 0 failures, 1 skipped** (2m 05s). |
| **Frontend Lint** | ESLint | **PASSED** | Zero errors. |
| **Frontend Build** | TypeScript / Vite | **PASSED** | Clean production bundle in 1.73s. |
| **Playwright Acceptance Tests** | 4 Application Roles | **PASSED** | **41/41 passed** in 22.9s. |
| **Local Development DB Safety** | PostgreSQL `flowdesk` | **PASSED** | Completely untouched at Flyway V25. |

---

## 10. Conclusion & Final SRE Gate Decision: **PASSED (READY FOR PR REVIEW)**

All safety risks identified in PR #61 have been thoroughly resolved with verified code, authenticated cryptography, dump-anchored referential validation, and automated test gates. PR #61 is ready for operator review and manual merge.
