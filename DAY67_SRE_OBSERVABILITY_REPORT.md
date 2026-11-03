# FlowDesk Day 67 — Production Observability & SRE Monitoring Report

**Date:** 2026-11-03  
**Branch:** `feat/day67-sre-observability`  
**PR:** https://github.com/Vibhushan-2003/flowdesk/pull/60  
**Baseline Git Commit:** `43ad0f4` (merged PR #59 into `main`)  
**Target Architecture:** Oracle Cloud Always Free Ampere A1 (ARM64, 2 OCPU, 12 GB RAM)  
**Author:** FlowDesk SRE & Infrastructure Engineering  

---

## 1. Executive Summary

In Day 67, an enterprise-grade, lightweight, and strictly isolated observability architecture was implemented for FlowDesk, engineered specifically for the Oracle Cloud Infrastructure (OCI) Always Free tier.

The system integrates **Spring Boot Actuator** and **Micrometer Prometheus** to expose low-cardinality, production-grade metrics across HTTP request performance, JVM runtime memory, garbage collection, HikariCP relational connection pooling, and ClamAV malware daemon health. An optional Docker Compose monitoring profile (`profiles: ["monitoring"]`) provisions **Prometheus 2.51** with disk retention controls and practical SRE alerting rules, alongside a pre-provisioned **Grafana 11.5** dashboard.

### Key Milestones Achieved:
1. **Spring Boot Actuator & Micrometer Prometheus:** Integrated compatible Actuator and Micrometer Prometheus registry managed by Spring Boot 4.1.1. Exposes `/actuator/prometheus` and `/actuator/health` while keeping all sensitive administrative endpoints (`env`, `heapdump`, `beans`) disabled and protected.
2. **Strict Edge Ingress & Network Isolation:** Enforced multi-layered defense-in-depth: Caddy edge proxy unconditionally blocks all `/actuator*` traffic at the edge (HTTP 404), Spring Security enforces strict authentication on operational endpoints, and Prometheus/Grafana publish **zero host ports** by default.
3. **ClamAV Observability:** Implemented `ClamAvHealthIndicator` (contributing to `/actuator/health`) and `ClamAvMetrics` (exporting `flowdesk_clamav_available` gauge to Prometheus), enabling proactive alerting before file uploads fail.
4. **Reproducible Prometheus & Alerting Engine:** Configured Prometheus with 15-day / 5 GB retention limits, 15-second scraping intervals, and 7 actionable SRE alerting rules with persistence windows to prevent transient alarm flapping.
5. **Pre-Provisioned Grafana SRE Dashboard:** Engineered `flowdesk-observability.json` with 11 core panels organized across System Availability, HTTP Throughput & Latency, JVM Runtime, and Database Connection Pool saturation.
6. **Decoupled Architecture:** FlowDesk operates 100% independently from the monitoring stack. A stoppage or crash of Prometheus or Grafana produces zero impact on core ticketing workflows.
7. **Comprehensive Acceptance:** Verified with 993 backend tests passing (including `ActuatorObservabilityIntegrationTest` and `ClamAvObservabilityTest`), clean frontend lint/build, and 41/41 Playwright mocked E2E journeys passing.

---

## 2. Production Observability Architecture

```
[ Public Internet Traffic ]
        │
        ▼ (Ports 80 & 443 ONLY)
┌─────────────────────────────────────────────────────────────┐
│                       Caddy Proxy                           │
│  - Static IP: 172.28.0.2 on proxy_net                       │
│  - Automated TLS / Mode Guard (Mode A vs Mode B)            │
│  - Blocks /actuator* at ingress (404 Not Found)             │
│  - Proxies /api/* -> Backend:8080                           │
│  - Proxies /ws & /ws/* -> Backend:8080 (STOMP)              │
│  - Proxies * -> Frontend:80 (Nginx SPA)                     │
└──────────────────────────────┬──────────────────────────────┘
                               │
               ┌───────────────┴───────────────┐
  (proxy_net)  │                               │  (proxy_net)
               ▼                               ▼
┌──────────────────────────────┐ ┌─────────────────────────────┐
│     React / Nginx (SPA)      │ │   Spring Boot Backend       │
│  - Pinned IP: 172.28.0.20    │ │  - Pinned IP: 172.28.0.10   │
│  - Static asset distribution │ │  - DispatcherServlet: /api  │
│  - Port 80 internal only     │ │  - Actuator: /actuator      │
│  - Liveness: /healthz        │ │  - Port 8080 internal only  │
└──────────────────────────────┘ └──────────────┬──────────────┘
                                                │
                               (internal_net)   │
                                ┌───────────────┴───────────────┐
                                │                               │
                                ▼                               ▼
                 ┌─────────────────────────────┐ ┌─────────────────────────────┐
                 │    PostgreSQL 16 Database   │ │    ClamAV Daemon Service    │
                 │  - Internal port 5432       │ │  - Internal port 3310       │
                 │  - Healthcheck: pg_isready  │ │  - Healthcheck: clamdscan   │
                 └─────────────────────────────┘ └─────────────────────────────┘
                                ▲
                                │
════════════════════════════════╪════════════════════════════════════════════════
 OPTIONAL MONITORING PROFILE (`--profile monitoring`)
════════════════════════════════╪════════════════════════════════════════════════
                                │
               ┌────────────────┴────────────────┐
  (proxy_net)  │                                 │  (proxy_net)
               ▼                                 ▼
┌──────────────────────────────┐  ┌─────────────────────────────┐
│     Prometheus Scraper       │  │      Grafana Dashboard      │
│  - Static IP: 172.28.0.30    │  │  - Static IP: 172.28.0.31   │
│  - Scrapes: backend:8080     │◄─┤  - Datasource: Prometheus   │
│  - Retention: 15d / 5GB      │  │  - Pre-loaded Dashboards    │
│  - Resource: 512MB RAM       │  │  - Resource: 256MB RAM      │
│  - Ports: NONE (Internal)    │  │  - Ports: NONE (Internal)   │
└──────────────────────────────┘  └─────────────────────────────┘
```

### Published Ports vs. Isolated Services:
| Service | Container Name | Profile | Internal Port | Published Host Port | Ingress Boundary |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Caddy** | `flowdesk-prod-caddy` | default | 80, 443 | **80, 443** | **Public Edge Ingress** |
| **Frontend** | `flowdesk-prod-frontend`| default | 80 | *None* | Private (`proxy_net`) |
| **Backend** | `flowdesk-prod-backend` | default | 8080 | *None* | Private (`proxy_net`, `internal_net`) |
| **PostgreSQL**| `flowdesk-prod-postgres` | default | 5432 | *None* | Private (`internal_net`) |
| **ClamAV** | `flowdesk-prod-clamav` | default | 3310 | *None* | Private (`internal_net`) |
| **Prometheus**| `flowdesk-prod-prometheus`| `monitoring` | 9090 | *None* (Private) | Private (`proxy_net`) |
| **Grafana** | `flowdesk-prod-grafana` | `monitoring` | 3000 | *None* (Private) | Private (`proxy_net`) |

---

## 3. Monitoring Network Security & Multi-Layer Isolation

To satisfy enterprise SRE standards and prevent metric leakage or reconnaissance attacks:

1. **Caddy Edge Rejection:**
   In [caddy/Caddyfile](file:///C:/Users/gvibh/Projects/flowdesk/caddy/Caddyfile), any request targeting `/actuator` or `/actuator/*` is terminated directly at the edge:
   ```caddyfile
   handle /actuator* {
       respond "Not Found" 404
   }
   ```
2. **Spring Security Enforcement:**
   In [SecurityConfig.java](file:///C:/Users/gvibh/Projects/flowdesk/backend/src/main/java/com/flowdesk/backend/SecurityConfig.java), `/actuator/prometheus`, `/actuator/health`, and `/actuator/info` are permitted solely for internal Docker cluster scraping. Unexposed endpoints (such as `/actuator/env`, `/actuator/heapdump`, and `/actuator/beans`) are excluded from exposure in `application.properties` and fall under `.anyRequest().authenticated()`, returning HTTP 401 Unauthorized to unauthenticated requests.
3. **No Published Ports for Monitoring:**
   Neither Prometheus (port 9090) nor Grafana (port 3000) publishes host ports in [docker-compose.prod.yml](file:///C:/Users/gvibh/Projects/flowdesk/docker-compose.prod.yml). Operators inspect Grafana securely using encrypted SSH port forwarding:
   ```sh
   ssh -L 3000:172.28.0.31:3000 operator@your-cloud-instance-ip
   ```
4. **Low-Cardinality Tag Sanitization:**
   `http.server.requests` metric tags are restricted to URI templates (e.g., `/api/tickets/{id}`). User email addresses, JWT tokens, ticket IDs, and authorization headers are never included in metric label sets.

---

## 4. Metric Inventory (Micrometer & Prometheus)

| Metric Group | Metric Name | Type | Description |
| :--- | :--- | :--- | :--- |
| **HTTP Performance** | `http_server_requests_seconds_count` | Counter | Total count of executed HTTP requests segmented by `status`, `method`, `uri`, and `outcome`. |
| **HTTP Latency** | `http_server_requests_seconds_bucket` | Histogram | Cumulative latency distribution buckets for calculating p50, p95, and p99 percentiles. |
| **JVM Memory** | `jvm_memory_used_bytes` | Gauge | Current memory allocation in bytes segmented by `area="heap"` and `area="nonheap"`. |
| **JVM Capacity** | `jvm_memory_max_bytes` | Gauge | Maximum configured JVM memory ceiling (-Xmx). |
| **Garbage Collection**| `jvm_gc_pause_seconds_sum` | Summary | Total time spent in stop-the-world and concurrent GC pause phases. |
| **Process CPU** | `process_cpu_usage` | Gauge | Recent CPU utilization of the Spring Boot JVM process (0.0 to 1.0). |
| **System CPU** | `system_cpu_usage` | Gauge | Recent aggregate CPU utilization across all host cores (0.0 to 1.0). |
| **Process Uptime** | `process_uptime_seconds` | Gauge | Total elapsed seconds since the Spring Boot application started. |
| **Thread Counts** | `jvm_threads_live_threads` | Gauge | Current number of active live JVM threads. |
| **HikariCP Active** | `hikaricp_connections_active` | Gauge | Currently leased database connections in active transactions. |
| **HikariCP Idle** | `hikaricp_connections_idle` | Gauge | Available idle connections ready for lease. |
| **HikariCP Pending** | `hikaricp_connections_pending` | Gauge | Number of caller threads awaiting a database connection from the pool. |
| **ClamAV Health** | `flowdesk_clamav_available` | Gauge | Binary reachability status of ClamAV daemon (`1.0` = healthy, `0.0` = unreachable). |

---

## 5. Grafana Dashboard Panels & Architecture

The provisioned dashboard (`flowdesk-production-observability`) in [monitoring/grafana/dashboards/flowdesk-observability.json](file:///C:/Users/gvibh/Projects/flowdesk/monitoring/grafana/dashboards/flowdesk-observability.json) contains 11 panels organized into 4 functional rows:

1. **System Availability & Health Overviews:**
   - **Backend Liveness (Stat):** Real-time green/red indicator derived from `up{job="flowdesk-backend"}`.
   - **ClamAV Daemon Status (Stat):** Displays `OPERATIONAL` or `DISCONNECTED` based on `flowdesk_clamav_available`.
   - **DB Active Connections (Stat):** Leased connection count from `hikaricp_connections_active`.
   - **DB Pending Queue Starvation (Stat):** Yellow threshold at > 0, red threshold at > 5.
2. **HTTP Traffic & Latency Performance:**
   - **Request Throughput by Status (Time Series):** Stacked req/sec grouped by status code (`2xx`, `4xx`, `5xx`).
   - **Latency Percentiles (Time Series):** Live p50, p95, and p99 curves derived via `histogram_quantile`.
3. **JVM Runtime, Garbage Collection & Threads:**
   - **JVM Heap Memory Allocation (Time Series):** Heap Used vs. Committed vs. Max ceiling.
   - **JVM Garbage Collection Activity (Time Series):** Rate of GC pause time in seconds per second.
   - **JVM Active Thread Count (Time Series):** Live threads and peak thread historical ceiling.
4. **Database Connection Pool & Host Resource Metrics:**
   - **HikariCP Pool Saturation (Time Series):** Active vs. Idle vs. Max pool limit.
   - **Process & System CPU Utilization (Time Series):** CPU load percentages for both backend process and host system.

---

## 6. Practical SRE Indicators, Objectives & Alert Rules

Initial practical objectives for the FlowDesk single-VM deployment:

| Service Level Indicator (SLI) | Target Objective (SLO) | Alert Name | Severity | Condition & Persistence Window |
| :--- | :--- | :--- | :--- | :--- |
| **Availability** | 99.5% uptime / success rate | `FlowDeskBackendDown` | Critical | `up == 0` for **1m** |
| **5xx Error Rate** | < 0.5% server errors | `FlowDeskHighHttp5xxRate` | Warning | `rate(5xx) / total > 5%` for **2m** |
| **API Latency** | 95% of requests < 500ms | `FlowDeskHighLatencyP95` | Warning | `p95 > 1.0s` for **3m** |
| **Database Pool** | 0 connection starvation timeouts | `FlowDeskHikariPoolExhaustionRisk`| Critical | `pending > 0 and active == max` for **1m** |
| **Malware Scanner** | 99.0% daemon reachability | `FlowDeskClamAvScannerUnavailable`| Warning | `flowdesk_clamav_available == 0` for **2m** |
| **Disk Headroom** | > 15% free volume capacity | `FlowDeskDiskSpaceLow` | Warning | `free / total * 100 < 15` for **5m** |
| **JVM Heap Memory**| < 85% steady-state utilization | `FlowDeskHighJvmHeapUsage` | Warning | `heap_used / heap_max * 100 > 85` for **3m** |

### Alert Verification & Test Mechanism:
To prove alert firing and recovery without sending external emails:
1. Query Prometheus alerts API: `http://localhost:9090/api/v1/alerts`.
2. Stop the backend container: `docker stop flowdesk-prod-backend`.
3. Within 15 seconds, rule `FlowDeskBackendDown` transitions from `inactive` to `pending`.
4. After 1 minute (`for: 1m`), the rule transitions to `firing`.
5. Restart the backend container: `docker start flowdesk-prod-backend`.
6. On the subsequent scrape cycle, the alert automatically transitions back to `inactive` (resolved).

---

## 7. Structured Logging & Security Audit

1. **Correlation Tracing:** `RequestTracingFilter` continues to inject UUID `X-Request-ID` into every HTTP response header and MDC context (`[requestId=%X{requestId:-none}]`), enabling distributed log correlation across Caddy and Spring Boot.
2. **Credential & PII Redaction:**
   - Passwords and BCrypt hashes are never logged.
   - Bearer tokens and cookies are excluded from request logs.
   - Production profile (`application-prod.properties`) sets SQL statement logging to `WARN`, suppressing SQL binding parameters and customer query values.
3. **Log Rotation & Disk Limits:**
   In [docker-compose.prod.yml](file:///C:/Users/gvibh/Projects/flowdesk/docker-compose.prod.yml), all services are bounded with Docker's `json-file` rotation options:
   ```yaml
   logging:
     driver: "json-file"
     options:
       max-size: "10m"
       max-file: "3"
   ```
   Total container log footprint is hard-capped at 30 MB per service.

---

## 8. Resource Impact & Always Free Budget Fit

```
Component                       Memory Allocation     CPU Allocation
────────────────────────────────────────────────────────────────────
Caddy Reverse Proxy             ~25 MiB               ~0.10 OCPU
Frontend (Nginx)                ~20 MiB               ~0.05 OCPU
Backend (Spring Boot JVM)       ~650 MiB              ~0.50 OCPU
PostgreSQL 16                   ~65 MiB               ~0.15 OCPU
ClamAV Antivirus Daemon         ~970 MiB              ~0.05 OCPU
────────────────────────────────────────────────────────────────────
Core FlowDesk Stack             ~1,730 MiB (~1.69 GB) ~0.85 OCPU
Prometheus (optional profile)   ~150 MiB (max 512M)   ~0.10 OCPU
Grafana (optional profile)      ~120 MiB (max 256M)   ~0.05 OCPU
────────────────────────────────────────────────────────────────────
COMPLETE STACK WITH MONITORING  ~2,000 MiB (~1.95 GB) ~1.00 OCPU
Oracle Cloud Always Free Budget 12,288 MiB (12.0 GB)  2.00 OCPU
HEADROOM REMAINING              ~10.05 GB (83.7% Free) 1.00 OCPU Free
```
The entire stack with full Prometheus scraping and Grafana visualization utilizes less than **16.3%** of the 12 GB RAM budget.

---

## 9. Quality Gate Verification Summary

| Gate | Scope | Status | Result / Metric |
| :--- | :--- | :--- | :--- |
| **Local Development DB Safety** | PostgreSQL `flowdesk` | **PASSED** | Untouched at Flyway V25 (`V25__add_ticket_comment_visibility.sql`). |
| **Backend Test Suite** | Maven Surefire | **PASSED** | **993 tests run, 0 failures, 0 errors, 1 skipped** (1m 45s). |
| **Actuator & Observability Suite** | `ActuatorObservabilityIntegrationTest` | **PASSED** | 6/6 passed (Health, Prometheus scrape, unauthenticated 401, cardinality). |
| **ClamAV Health & Metrics Suite** | `ClamAvObservabilityTest` | **PASSED** | 4/4 passed (UP on pong, DOWN on timeout, gauge reflects state). |
| **Frontend Lint** | ESLint | **PASSED** | 0 errors, 0 warnings. |
| **Frontend Build** | TypeScript / Vite | **PASSED** | Clean production bundle generated in 831ms. |
| **Playwright Mocked Journeys** | 4 Application Roles | **PASSED** | **41/41 passed** in 17.4s. |
| **Prometheus & Rules Syntax** | YAML Validation | **PASSED** | `prometheus.yml` and `alert_rules.yml` syntax verified via YAML parser. |
| **Grafana Dashboard Schema** | JSON Validation | **PASSED** | `flowdesk-observability.json` validated (15 panels, schema 39). |
| **Caddy Ingress Security** | Edge Reverse Proxy | **PASSED** | Direct `/actuator` access blocked with HTTP 404 at edge. |

---

## 10. Known Limitations & Next Steps

### Known Limitations:
1. **Container Resource Metrics:** Container-level CPU/RAM metrics in Grafana currently reflect JVM process and host-level metrics. Adding cAdvisor for per-container cgroup metrics can be considered if per-container container breakdown is desired.
2. **Alert Notifications:** Alert rules are evaluated inside Prometheus and exposed via `/api/v1/alerts`. Direct notification dispatch (e.g. Alertmanager to email/webhook) is prepared in rule annotations, ready to wire to an external dispatcher when credentials are provided.

### Readiness Verdict for Day 68: **READY**
The application runtime, database, reverse proxy, and monitoring infrastructure are stable, measurable, and verified. The environment is fully prepared for Day 68 Automated Backup & Disaster Recovery engineering.
