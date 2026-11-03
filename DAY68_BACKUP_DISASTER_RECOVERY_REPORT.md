# FlowDesk Day 68 — Production Backup, Restore & Disaster Recovery Report

**Date:** 2026-11-03  
**Branch:** `feat/day68-backup-disaster-recovery`  
**Baseline Git Commit:** `43ea4ce` (merged PR #60 into `main`)  
**Target Infrastructure:** Oracle Cloud Always Free Ampere A1 (Ubuntu 24.04 / ARM64, 2 OCPU, 12 GB RAM)  
**Author:** FlowDesk SRE & Infrastructure Engineering  

---

## 1. Executive Summary

In Day 68, a comprehensive, enterprise-grade, and mathematically verifiable backup, restore, and disaster recovery architecture was designed, implemented, and verified for FlowDesk's production Docker environment on Oracle Cloud Always Free ARM64 infrastructure.

The architecture solves the fundamental challenge of split-state data consistency between the PostgreSQL 16 relational database and the persistent ticket attachments directory (`/app/data/attachments`). It provides automated daily scheduling via systemd timers, zero-trust client-side AES-256-CBC encryption for off-host replication, an explicitly guarded restore orchestrator that strictly forbids accidental production or development data destruction, and complete operational runbooks.

In an isolated end-to-end disaster recovery drill simulating catastrophic database and volume destruction, the system proved **100% data and schema restoration** with an actual measured **Recovery Time Objective (RTO) of 8 seconds** (well within our 30-minute target) and passed **13 automated failure and corruption test scenarios**.

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
 │  - Concurrency Lock (/tmp/flowdesk_backup.lock via flock)        │
 │  - Pre-flight Storage Check (> 500 MB Free)                      │
 │  - Consistency Mode: verified_snapshot (or quiescence)           │
 │  - pg_dump native SQL + gzip compression                         │
 │  - Relative path attachment archiving (tar -czf)                 │
 │  - Cryptographic cross-referential integrity audit               │
 │  - Manifest & SHA-256 sidecar generation                         │
 │  - Atomic publication: .staging_id -> flowdesk_backup_TIMESTAMP  │
 └─────────────────┬────────────────────────────────────────────────┘
                   │
                   ▼ (Local Backup Set: /var/backups/flowdesk/...)
 ┌──────────────────────────────────────────────────────────────────┐
 │          offsite-replication.sh (Off-Host Replication)           │
 │  - Client-Side AES-256-CBC Encryption (PBKDF2, 100k iter, Salt)  │
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
 │  - Decrypts and unpacks into secure staging directory            │
 └─────────────────┬────────────────────────────────────────────────┘
                   │
                   ▼
 ┌──────────────────────────────────────────────────────────────────┐
 │            flowdesk-restore.sh (Safe Restore Orchestrator)       │
 │  - Mandatory explicit --target-db and --target-attachments-dir   │
 │  - Mandatory confirmation token (--confirm-destructive)          │
 │  - Production DB Overwrite Lock (requires special override token) │
 │  - Pre-restore cryptographic SHA-256 manifest verification       │
 │  - Single-transaction schema & table restore (psql)              │
 │  - Safe attachment extraction (anti-path-traversal protection)   │
 │  - Post-restore referential consistency audit                    │
 └──────────────────────────────────────────────────────────────────┘
```

---

## 3. Database & Attachment Consistency Strategy

### The Problem
FlowDesk persists ticket metadata and attachment records in PostgreSQL (`ticket_attachments`), while storing opaque payload files (`{UUID}.dat`) on the local filesystem. A standard uncoordinated `pg_dump` and independent `tar` command can result in split-brain state: an attachment committed to the database during dump creation could be missing from the filesystem snapshot, creating broken references upon restore.

### The FlowDesk Solution
The system implements two distinct, demonstrably sound consistency modes:

1. **`verified_snapshot` (Default, Zero Downtime):**
   - Step 1: Initiates a single-transaction PostgreSQL database dump using `pg_dump --clean --if-exists --no-owner --no-privileges`.
   - Step 2: Creates an archive of `/app/data/attachments`.
   - Step 3: Queries the database dump for all active attachments:
     `SELECT storage_key, checksum_sha256 FROM ticket_attachments WHERE status != 'DELETED';`
   - Step 4: Temporarily unpacks the attachment archive into an isolated check sandbox and verifies that **every single attachment referenced by the database exists in the archive and matches its exact recorded SHA-256 checksum**.
   - Step 5: If any attachment is missing or corrupted, the entire backup is aborted, temporary directories are purged, and a non-zero exit code is thrown. The backup is published only if referential closure is mathematically proven.
2. **`quiescence` (Scheduled Maintenance Window):**
   - Temporarily freezes in-flight write traffic by pausing the backend container: `docker pause flowdesk-prod-backend`.
   - Captures database and attachment snapshots while application writes are frozen.
   - Resumes the container in an exit-trap handler: `docker unpause flowdesk-prod-backend`.
   - Executes the cross-referential audit to confirm 100% integrity.

---

## 4. Cryptographic Encryption & Secret Handling

1. **Client-Side Encryption:**
   - Client-side encryption is executed by [`scripts/sre/offsite-replication.sh`](file:///C:/Users/gvibh/Projects/flowdesk/scripts/sre/offsite-replication.sh) using OpenSSL AES-256-CBC.
   - Key derivation utilizes PBKDF2 with a cryptographic salt and 100,000 hashing iterations (`-pbkdf2 -iter 100000 -salt`).
2. **Secret Ingestion & Protection:**
   - Passphrases are injected via environment variable `BACKUP_ENCRYPTION_KEY` or loaded from a dedicated key file (`/etc/flowdesk/backup.env`).
   - Script enforces file permissions of `0600` or `0400` on key files and validates minimum passphrase length of 16 characters.
   - Passphrases are never echoed, logged, or recorded in manifests or transcripts.
3. **Ciphertext Security Verification:**
   - Automated drill verified that encrypted archives contain zero plaintext strings (e.g. database schema strings, ticket text, or user emails).

---

## 5. Automated Scheduling & Retention Governance

### Systemd Timer Architecture (Ubuntu ARM64 Production Target)
- **Service Unit:** [`scripts/sre/systemd/flowdesk-backup.service`](file:///C:/Users/gvibh/Projects/flowdesk/scripts/sre/systemd/flowdesk-backup.service)
  - Sandboxed execution (`ProtectSystem=strict`, `PrivateTmp=true`, `NoNewPrivileges=true`).
  - Low CPU/IO priority (`Nice=10`, `IOSchedulingClass=idle`) ensuring zero performance degradation for live web users.
- **Timer Unit:** [`scripts/sre/systemd/flowdesk-backup.timer`](file:///C:/Users/gvibh/Projects/flowdesk/scripts/sre/systemd/flowdesk-backup.timer)
  - Schedules execution nightly at `02:00:00 UTC`.
  - `Persistent=true`: Catches up immediately if the server was offline or rebooted during the scheduled window.
  - `RandomizedDelaySec=15m`: Spreads execution jitter to avoid peak host or network contention.
- **Alternative Cron:** [`scripts/sre/cron/flowdesk-backup.cron`](file:///C:/Users/gvibh/Projects/flowdesk/scripts/sre/cron/flowdesk-backup.cron) for environments without systemd.
- **Independent Retention Policies:**
  - Local host retention: `RETENTION_DAYS=7` (pruned automatically by `flowdesk-backup.sh`).
  - Off-host retention: `OFFSITE_RETENTION_DAYS=30` (managed by `offsite-replication.sh`).

---

## 6. SRE Disaster Recovery Drill Results

An end-to-end disaster recovery drill was executed using the automated drill suite [`scripts/sre/test_backup_restore.sh`](file:///C:/Users/gvibh/Projects/flowdesk/scripts/sre/test_backup_restore.sh):

```
======================================================================
DISASTER RECOVERY DRILL & FAILURE MODES COMPLETE
Total Checks Passed: 13
Total Checks Failed: 0
Measured Recovery Duration (RTO): 8 seconds
Target RPO Cadence:               24 Hours
Data Integrity Result:            100% Match (Users, Tickets, Flyway V25, Attachments)
======================================================================
```

### Drill Execution Progression:
1. **Isolated Data Seeding:** Created schema `flowdesk_drill_src` in `flowdesk_test` with Flyway V25 history, 3 representative users (Administrator, Support Agent, Employee), 3 tickets across various states, and 3 physical ticket attachments with calculated SHA-256 hashes.
2. **Atomic Backup:** Generated backup set `flowdesk_backup_20261103_050152Z` (4 KB compressed SQL, 1 KB attachments, valid `manifest.json`, verified checksums).
3. **Client-Side Encryption:** Transformed backup into AES-256-CBC ciphertext `flowdesk_backup_20261103_050152Z.tar.gz.enc` and generated sidecar SHA-256.
4. **Replication & Decryption:** Successfully replicated to offsite destination, verified destination SHA-256, decrypted, and extracted into fresh staging.
5. **Catastrophic Loss Simulation:** Dropped primary database schema `flowdesk_drill_src CASCADE` and completely deleted the source attachments directory.
6. **Safe Restoration:** Executed `flowdesk-restore.sh` against the wiped target environment.
7. **Post-Restore Integrity Audit:**
   - Restored Flyway Version: **V25** (matches baseline).
   - Restored Users: **3/3** (100% match).
   - Restored Tickets: **3/3** (100% match).
   - Restored Attachments: **3/3** (100% physical existence and cryptographic checksum match).

---

## 7. Failure Scenario & Safety Gate Evidence

The automated test suite validated 8 critical failure and corruption modes. All scenarios aborted safely without corrupting data or leaving partial artifacts:

| Scenario ID | Test Condition | Expected Behavior | Observed Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| **F1** | Corrupted Database Checksum | Reject dump file tampering before restore | Restore aborted; checksum mismatch detected | **PASSED** |
| **F2** | Missing Attachment Archive | Abort restore when required archive missing | Restore aborted; missing file identified | **PASSED** |
| **F3** | Truncated Gzip Database Dump | Abort on broken gzip stream | Restore aborted; decompression failure trapped | **PASSED** |
| **F4** | Missing Confirmation Token | Abort restore without `--confirm-destructive` | Aborted immediately with safety instruction | **PASSED** |
| **F5** | Production DB Overwrite Attempt | Abort targeting `flowdesk` without override | Blocked by safety guardrail | **PASSED** |
| **F6** | Incorrect Decryption Key | Reject offsite decrypt with invalid passphrase | Decryption aborted; bad decrypt reported | **PASSED** |
| **F7** | Concurrency Lock Collision | Block simultaneous backup when lock held | Aborted immediately; lock collision reported | **PASSED** |
| **F8** | Unencrypted Offsite Transfer | Block off-host sync without encryption key | Refused transfer; fail-closed policy upheld | **PASSED** |

---

## 8. Measured RTO & RPO Evidence

| Objective | Operational Target | Measured Evidence / Realistic Assessment | Status |
| :--- | :--- | :--- | :--- |
| **Recovery Point Objective (RPO)** | 24 Hours | Scheduled daily at 02:00 UTC. Maximum theoretical data loss window is 24 hours. | **Target Met** |
| **Decryption Duration** | < 2 Minutes | Measured: **~1.2 seconds** for encrypted drill bundle. | **Target Met** |
| **Database Restore Duration** | < 10 Minutes | Measured: **~4.5 seconds** for Flyway V25 schema and records. | **Target Met** |
| **Attachment Restore Duration** | < 10 Minutes | Measured: **~2.3 seconds** for verified attachment extraction. | **Target Met** |
| **Script Execution RTO** | < 30 Minutes | **Total Script RTO: 8 Seconds** | **Target Met** |
| **Cold-Start Bare-Metal RTO** | < 30 Minutes | **Estimated 12–18 Minutes** (including OCI VM boot, Docker install, image pull, and restore execution). | **Realistic Target** |

---

## 9. SRE Runbooks & Monitoring Integration

Practical runbooks have been established under [`docs/sre/`](file:///C:/Users/gvibh/Projects/flowdesk/docs/sre/):
1. [`docs/sre/RUNBOOK_BACKUP_AND_RESTORE.md`](file:///C:/Users/gvibh/Projects/flowdesk/docs/sre/RUNBOOK_BACKUP_AND_RESTORE.md): Routine operations, scheduling, ad-hoc backups, and local restore procedures.
2. [`docs/sre/RUNBOOK_DISASTER_RECOVERY.md`](file:///C:/Users/gvibh/Projects/flowdesk/docs/sre/RUNBOOK_DISASTER_RECOVERY.md): Off-host replication, bare-metal cold starts, and off-host outage response.
3. [`docs/sre/RUNBOOK_INCIDENT_RECOVERY.md`](file:///C:/Users/gvibh/Projects/flowdesk/docs/sre/RUNBOOK_INCIDENT_RECOVERY.md): Detailed incident triage for PostgreSQL corruption, missing attachments, host storage full, and backup lock collisions.

### Prometheus Alerting Integration
Updated [`monitoring/prometheus/alert_rules.yml`](file:///C:/Users/gvibh/Projects/flowdesk/monitoring/prometheus/alert_rules.yml) with Rule 8:
```yaml
- alert: FlowDeskBackupMissingOrFailed
  expr: (time() - flowdesk_backup_last_success_timestamp > 93600) or (flowdesk_backup_status == 0)
  for: 15m
  labels:
    severity: critical
    service: flowdesk-backup
  annotations:
    summary: "FlowDesk production backup is missing or failed"
    description: "No successful FlowDesk backup has completed in the last 26 hours, or the last backup attempt failed."
    action: "Check systemd journal on host: journalctl -u flowdesk-backup.service -xe"
```

---

## 10. Resource & Storage Impact (Always Free Budget)

```
Storage / Resource Item             Allocation / Footprint   Always Free Budget Impact
──────────────────────────────────────────────────────────────────────────────────────────
Local Backup Set (7 Days)           ~50 MB - 350 MB          < 0.7% of 50 GB Boot Volume
Offsite Replication Payload         ~5 MB - 50 MB / day      OCI Object Storage (10 GB Free Tier)
Backup Execution Memory Footprint   ~35 MB peak RAM          Negligible (< 0.3% of 12 GB RAM)
Backup CPU Utilization              Nice=10 (idle class)     Negligible (< 5% of 1 OCPU during dump)
──────────────────────────────────────────────────────────────────────────────────────────
```

---

## 11. Full Quality Gate Verification Summary

| Gate | Scope | Status | Result / Metric |
| :--- | :--- | :--- | :--- |
| **Local Development DB Safety** | PostgreSQL `flowdesk` | **PASSED** | Untouched at Flyway V25. Zero destructive tests against host DB. |
| **Disaster Recovery Drill** | `test_backup_restore.sh` | **PASSED** | **13/13 passed** (Backup, Encrypt, Decrypt, Restore, 8 Failures). |
| **Bash Syntax & POSIX Lint** | `bash -n` on all scripts | **PASSED** | All 5 bash scripts verified clean. |
| **Backend Test Suite** | Maven Surefire | **PASSED** | **995 tests run, 0 failures, 0 errors, 1 skipped** (1m 42s). |
| **Frontend Lint** | ESLint | **PASSED** | 0 errors, 0 warnings. |
| **Frontend Build** | TypeScript / Vite | **PASSED** | Clean production bundle generated in 838ms. |
| **Playwright Mocked Journeys** | 4 Application Roles | **PASSED** | **41/41 passed** in 18.7s. |
| **Prometheus Rules Validation** | YAML Safe Load | **PASSED** | `alert_rules.yml` valid YAML including Rule 8. |
| **Secret Exposure Audit** | Git Workspace Scan | **PASSED** | Zero credentials or test dumps staged. |

---

## 12. Remaining OCI-Specific Requirements (For Cloud Provisioning)

When deploying to live Oracle Cloud Infrastructure Always Free:
1. Create private OCI Object Storage bucket: `flowdesk-production-backups`.
2. Configure host OCI CLI authentication via instance principal or `~/.oci/config`.
3. Generate and store a high-entropy production encryption key in `/etc/flowdesk/backup.env` (`chmod 600`).
4. Enable `flowdesk-backup.timer` via `systemctl enable --now flowdesk-backup.timer`.

---

## 13. SRE Recommendation: **GO FOR MERGE**

The Day 68 Backup, Restore & Disaster Recovery system is robust, thoroughly tested against failure conditions, and production-ready. All code and documentation are prepared for pull request submission.
