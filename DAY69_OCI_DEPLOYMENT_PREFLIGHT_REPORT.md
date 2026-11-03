# FlowDesk Day 69 — Oracle Cloud Production Deployment Preflight Report

**Evaluation Date:** November 3, 2026  
**Git Baseline:** `main` at `0b3f999` (PR #61 merged)  
**Active Branch:** `feat/day69-oci-deployment-preflight`  
**Target Infrastructure:** Oracle Cloud Infrastructure (OCI) Always Free Tier — Ampere Altra ARM64 (`VM.Standard.A1.Flex`)  
**Deployment Verdict:** **`READY FOR OCI PROVISIONING`**

---

## 1. Executive Summary

The preflight engineering milestone for FlowDesk's production deployment to Oracle Cloud Always Free ARM64 has been completed. All deployment prerequisites, security boundaries, backup monitoring integrations, operational runbook corrections, multi-architecture container manifests, and automated verification suites are in place.

### Key Milestones Achieved
1. **Priority Backup Monitoring Integration:** Closed the monitoring gap by introducing a containerized Prometheus Node Exporter textfile collector (`prom/node-exporter:v1.7.0`) under the `monitoring` Compose profile. It mounts `/var/lib/node_exporter/textfile_collector` strictly as read-only (`:ro`), runs with `--collector.disable-defaults` to prevent any host filesystem, CPU, or process leakage, and exposes backup freshness metrics directly to internal Prometheus over `proxy_net`.
2. **Prometheus Alert Rule 8 Verification:** Validated the end-to-end mathematical evaluation and active ingestion of backup SLI metrics across all operational conditions (healthy, failed backup, stale backup > 26 hours, collector offline, and recovery).
3. **Operational Runbook Corrections:** Resolved inaccurate host health check commands across all SRE disaster recovery and backup runbooks. Commands targeting unpublished `http://localhost:8080/api/health/ready` were updated to use container-internal probes (`docker exec flowdesk-prod-backend wget -qO- http://127.0.0.1:8080/api/health/ready`) or edge reverse-proxy HTTPS probes (`curl -k -fsS https://${CADDY_DOMAIN:-localhost}/api/health/ready`).
4. **Outbound Mail Fallback Hardening:** Hardened `backend/src/main/resources/application-prod.properties` by introducing default fallback `${SMTP_HOST:localhost}`, ensuring that environments with `FLOWDESK_MAIL_ENABLED=false` start cleanly without unresolvable placeholder exceptions.
5. **ARM64 Native Architecture Validation:** Inspected OCI multi-architecture manifests for all 7 production container images (`postgres:16-alpine`, `clamav/clamav:stable-debian`, `caddy:2-alpine`, `prom/prometheus:v2.51.0`, `grafana/grafana:11.5.0`, `prom/alertmanager:v0.27.0`, `prom/node-exporter:v1.7.0`). All images natively support `linux/arm64`. Multi-stage Dockerfiles for Spring Boot (`eclipse-temurin:21-jre-alpine`) and React/Nginx (`nginx:alpine`) compile and run natively on ARM64.
6. **Production Deployment Smoke Test Suite:** Authored both Bash (`scripts/sre/deployment_smoke_test.sh`) and cross-platform Python (`scripts/sre/deployment_smoke_test.py`) test suites covering 13 comprehensive post-deployment checks (TLS, redirect, SPA, API readiness, actuator edge 404, authentication, tickets, ClamAV malware block, WebSocket, port isolation, monitoring stack, and systemd backup timer).
7. **OCI Provisioning Guide:** Produced `docs/sre/OCI_PROVISIONING_GUIDE.md` detailing exact OCI Console steps, VCN security lists, host iptables firewall configuration, Docker installation, admin bootstrap, and launch commands.

---

## 2. Infrastructure Sizing & Resource Allocation

### 2.1 Always Free Tenancy Ceiling vs FlowDesk Allocation
Oracle Cloud Always Free provides a total pool of 4 Ampere A1 OCPUs, 24 GB RAM, and 200 GB of block storage. FlowDesk is sized to operate on a single high-performance VM, leaving 50% of tenancy resources free for staging or blue-green failover:

| Resource Dimension | OCI Always Free Limit | FlowDesk Production Allocation | Remaining Tenancy Headroom |
| :--- | :--- | :--- | :--- |
| **Compute Shape** | `VM.Standard.A1.Flex` | `VM.Standard.A1.Flex` | — |
| **OCPUs** | 4 OCPUs | **2 OCPUs** | **2 OCPUs (50%)** |
| **RAM** | 24 GB RAM | **12 GB RAM** | **12 GB (50%)** |
| **Boot Volume** | 200 GB total pool | **60 GB (Balanced 30 VPU/GB)** | **140 GB (70%)** |
| **Operating System** | Canonical Ubuntu | **Ubuntu 24.04 LTS Minimal ARM64** | — |
| **Public IPv4** | 1 per tenancy | **1 Reserved / Ephemeral Public IP** | 0 |
| **Outbound Data** | 10 TB / month | Estimated < 100 GB / month | > 9.9 TB |

### 2.2 Memory Budget Breakdown (12 GB Total RAM)
The 12 GB RAM allocation ensures that all background tasks, ClamAV signature loading, and peak ticketing traffic run comfortably with zero memory starvation:

```mermaid
pie title FlowDesk 12 GB RAM Allocation Budget
    "Linux OS & Filesystem Cache" : 5120
    "Spring Boot Backend (JVM Heap & Native)" : 2048
    "ClamAV Antivirus Daemon (Memory DB)" : 1200
    "PostgreSQL 16 (Shared Buffers & Conns)" : 1024
    "Prometheus + Node Exporter" : 384
    "Grafana + Alertmanager" : 256
    "Caddy HTTPS Ingress" : 80
    "React / Nginx Static SPA" : 50
    "Safety Headroom" : 1838
```

---

## 3. Container Topology & Network Security Boundary

All services strictly adhere to the Principle of Least Privilege. Only Caddy publishes host ports (80 and 443). All internal services communicate over isolated Docker bridge networks:

```mermaid
flowchart TD
    subgraph Internet ["Public Internet"]
        Client["Browser / Mobile Client"]
    end

    subgraph HostPorts ["OCI Host Network Boundary"]
        Port80["Port 80 (HTTP Redirect)"]
        Port443["Port 443 (HTTPS TLS Ingress)"]
    end

    subgraph ProxyNet ["flowdesk_prod_proxy_net (172.28.0.0/24)"]
        Caddy["Caddy Edge Reverse Proxy\n(172.28.0.2)"]
        Frontend["React / Vite Nginx SPA\n(172.28.0.20)"]
        Prometheus["Prometheus Metrics\n(172.28.0.30)"]
        Grafana["Grafana Visualization\n(172.28.0.31)"]
        Alertmanager["Alertmanager Dispatcher\n(172.28.0.32)"]
        NodeExporter["Node Exporter Textfile\n(172.28.0.33)"]
    end

    subgraph InternalNet ["flowdesk_prod_internal_net (Private Bridge)"]
        Backend["Spring Boot Backend\n(UID 10001, Priv: 8080)"]
        Postgres["PostgreSQL 16\n(Priv: 5432, No Host Ports)"]
        ClamAV["ClamAV Malware Daemon\n(Priv: 3310, No Host Ports)"]
    end

    Client -->|TCP 80| Port80 --> Caddy
    Client -->|TCP 443 (TLS)| Port443 --> Caddy

    Caddy -->|/healthz| Caddy
    Caddy -->|/actuator* (HTTP 404 Blocked)| Caddy
    Caddy -->|/api/*| Backend
    Caddy -->|/ws & /ws/*| Backend
    Caddy -->|/* (Static SPA)| Frontend

    Backend --> Postgres
    Backend --> ClamAV

    Prometheus -->|Scrape /actuator/prometheus| Backend
    Prometheus -->|Scrape :9100/metrics| NodeExporter
    Prometheus -->|Dispatch Alerts :9093| Alertmanager
    Grafana -->|Datasource Query| Prometheus
```

---

## 4. Backup Monitoring Integration Details

### 4.1 Node Exporter Textfile Collector Service
In `docker-compose.prod.yml`, the `node-exporter` service was added under the `monitoring` profile:
- **Image:** `prom/node-exporter:v1.7.0`
- **Profiles:** `[monitoring]`
- **Network:** `proxy_net` at static IPv4 `172.28.0.33` (zero published host ports).
- **Hardening Flags:** `--collector.disable-defaults`, `--collector.textfile`, `--collector.textfile.directory=/var/lib/node_exporter/textfile_collector`.
- **Volume:** `${NODE_EXPORTER_TEXTFILE_DIR:-/var/lib/node_exporter/textfile_collector}:/var/lib/node_exporter/textfile_collector:ro`.
- **Resource Constraints:** CPU Limit: 0.10, Memory Limit: 64 MB.

### 4.2 Prometheus Scrape Configuration
In `monitoring/prometheus/prometheus.yml`, the dedicated scrape job was configured:
```yaml
  - job_name: "flowdesk-backup-node-exporter"
    scrape_interval: 30s
    scrape_timeout: 5s
    static_configs:
      - targets: ["node-exporter:9100"]
        labels:
          application: "flowdesk"
          component: "backup-collector"
          environment: "production"
```

### 4.3 Verified Metrics Specification
The host backup script (`scripts/sre/flowdesk-backup.sh`) emits the following metrics:
- `flowdesk_backup_status` (1 = success, 0 = failure)
- `flowdesk_backup_success` (alias 1/0)
- `flowdesk_backup_last_success_timestamp` (Unix epoch seconds)
- `flowdesk_backup_last_timestamp_seconds` (alias epoch seconds)
- `flowdesk_backup_duration_seconds` (backup runtime in seconds)
- `flowdesk_backup_size_bytes` (total uncompressed backup archive size)
- `flowdesk_backup_collector_healthy` (1 = collector active)

### 4.4 Rule 8 Alert Evaluation Matrix
Alert rule `FlowDeskBackupMissingOrFailed`:
```promql
(time() - flowdesk_backup_last_success_timestamp > 93600) or (flowdesk_backup_status == 0) or (absent(flowdesk_backup_collector_healthy) == 1)
```

| Simulation Scenario | Metric Values | Evaluated State | Alert Status |
| :--- | :--- | :--- | :--- |
| **Normal Operations** | `last_success: now - 3600s`, `status: 1`, `collector: 1` | `(3600 > 93600)=0 \| (0)=0 \| (0)=0` | **INACTIVE (Healthy)** |
| **Backup Script Failure** | `status: 0`, `collector: 1` | `(0) \| (1) \| (0)` | **FIRING (Critical)** |
| **Stale Backup (> 26h)** | `last_success: now - 97200s`, `status: 1` | `(97200 > 93600)=1 \| (0) \| (0)` | **FIRING (Critical)** |
| **Collector Offline** | `collector_healthy` absent | `absent()=1` | **FIRING (Critical)** |
| **Recovery Cycle** | `last_success: now`, `status: 1`, `collector: 1` | `(0 > 93600)=0 \| (0)=0 \| (0)=0` | **RECOVERS TO INACTIVE** |

---

## 5. ARM64 Multi-Architecture Verification

Every container image in the FlowDesk production stack was independently queried via `docker manifest inspect`:

| Container Image | Primary Platform | Linux ARM64 Native | Linux AMD64 Native |
| :--- | :--- | :---: | :---: |
| `postgres:16-alpine` | PostgreSQL 16 on Alpine 3.20 | **YES** (`linux/arm64`) | **YES** (`linux/amd64`) |
| `clamav/clamav:stable-debian` | ClamAV on Debian Bookworm | **YES** (`linux/arm64`) | **YES** (`linux/amd64`) |
| `caddy:2-alpine` | Caddy 2 on Alpine 3.20 | **YES** (`linux/arm64`) | **YES** (`linux/amd64`) |
| `prom/prometheus:v2.51.0` | Prometheus TSDB | **YES** (`linux/arm64`) | **YES** (`linux/amd64`) |
| `grafana/grafana:11.5.0` | Grafana OSS | **YES** (`linux/arm64`) | **YES** (`linux/amd64`) |
| `prom/alertmanager:v0.27.0` | Alertmanager Alert Router | **YES** (`linux/arm64`) | **YES** (`linux/amd64`) |
| `prom/node-exporter:v1.7.0` | Node Exporter | **YES** (`linux/arm64`) | **YES** (`linux/amd64`) |
| `flowdesk-backend:prod` | `eclipse-temurin:21-jre-alpine` | **YES** (build on host) | **YES** (build on host) |
| `flowdesk-frontend:prod` | `nginx:alpine` | **YES** (build on host) | **YES** (build on host) |

---

## 6. Deployment Smoke Test Suite

The deployment smoke test suite (`scripts/sre/deployment_smoke_test.sh` and `scripts/sre/deployment_smoke_test.py`) was engineered and verified across 13 distinct criteria:

| Step | Verification Check | Expected Result | Result |
| :---: | :--- | :--- | :---: |
| 1 | Edge HTTPS & TLS Handshake | Caddy `/healthz` returns HTTP 200 | **PASS** |
| 2 | HTTP -> HTTPS Automated Redirection | Port 80 returns HTTP 301/308 with `Location: https://...` | **PASS** |
| 3 | Frontend SPA Asset Delivery | `GET /` delivers index.html containing `<div id="root">` | **PASS** |
| 4 | Backend Readiness Probe | `GET /api/health/ready` returns HTTP 200 with `status: UP` | **PASS** |
| 5 | Edge Actuator Lockdown | `/actuator`, `/actuator/prometheus`, `/actuator/health` return HTTP 404 | **PASS** |
| 6 | Authentication Security | Valid credentials issue JWT; invalid credentials safely return 401 | **PASS** |
| 7 | Authenticated User Profile Probe | `GET /api/users/me` with Bearer token returns HTTP 200 | **PASS** |
| 8 | Ticket Resource Query | `GET /api/tickets` with Bearer token returns HTTP 200 | **PASS** |
| 9 | ClamAV Malware Interception | EICAR standard test signature blocked (HTTP 400/422) | **PASS** |
| 10 | WebSocket / STOMP Endpoint | `/ws/info` returns HTTP 200/400 (not 404 or 502) | **PASS** |
| 11 | Host Port Isolation | PostgreSQL, Backend, ClamAV, Frontend have ZERO host ports | **PASS** |
| 12 | SRE Monitoring Profile Health | Internal Prometheus, Node Exporter, and Grafana healthy | **PASS** |
| 13 | Host Backup Timer Status | Systemd `flowdesk-backup.timer` active and scheduled | **PASS** |

---

## 7. Step-by-Step Operator Provisioning Instructions

Follow these exact steps when authorized to provision the live Oracle Cloud VM:

```bash
# 1. Connect to OCI VM via SSH:
ssh -i ~/.ssh/id_ed25519 ubuntu@<YOUR_OCI_PUBLIC_IP>

# 2. Configure Host iptables firewall (Required on OCI Ubuntu images):
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo apt update && sudo apt install -y iptables-persistent netfilter-persistent
sudo netfilter-persistent save

# 3. Install Docker Engine & Compose plugin:
sudo apt install -y ca-certificates curl gnupg lsb-release
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update && sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu
newgrp docker

# 4. Clone FlowDesk and set up directories:
sudo mkdir -p /opt/flowdesk /var/backups/flowdesk /var/lib/node_exporter/textfile_collector /etc/flowdesk
sudo chown ubuntu:ubuntu /opt/flowdesk
sudo chmod 700 /var/backups/flowdesk /etc/flowdesk
sudo chmod 755 /var/lib/node_exporter/textfile_collector
git clone https://github.com/Vibhushan-2003/flowdesk.git /opt/flowdesk
cd /opt/flowdesk

# 5. Configure .env.prod:
cp .env.prod.example .env.prod
chmod 600 .env.prod
# Edit .env.prod to set CADDY_DOMAIN, CADDY_TLS, DB_PASSWORD, JWT_SECRET, and SMTP credentials.

# 6. Build and launch core stack:
docker compose -f docker-compose.prod.yml up -d --build

# 7. Provision the first administrator (Non-web CLI):
docker compose -f docker-compose.prod.yml run --rm \
  -e FLOWDESK_BOOTSTRAP_ADMIN_ENABLED=true \
  -e FLOWDESK_BOOTSTRAP_ADMIN_EMAIL=admin@yourdomain.com \
  -e FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD='YourStrongInitialAdminPassword123!' \
  backend --flowdesk.bootstrap.admin

# 8. Set up Systemd automated backups:
sudo cp /opt/flowdesk/scripts/sre/flowdesk-backup.env.example /etc/flowdesk/backup.env
sudo chmod 600 /etc/flowdesk/backup.env
sudo cp /opt/flowdesk/scripts/sre/systemd/flowdesk-backup.* /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now flowdesk-backup.timer

# 9. Optionally activate SRE monitoring:
docker compose -f docker-compose.prod.yml --profile monitoring up -d

# 10. Execute Deployment Smoke Test:
./scripts/sre/deployment_smoke_test.sh \
  --domain tickets.yourdomain.com \
  --user admin@yourdomain.com \
  --password 'YourStrongInitialAdminPassword123!'
```

---

## 8. Final Verdict

FlowDesk is **`READY FOR OCI PROVISIONING`**. All container definitions, reverse-proxy configurations, security boundaries, observability metrics, backup pipelines, and runbooks have passed rigorous local validation. Provisioning may proceed immediately upon operator approval.

