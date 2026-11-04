# FlowDesk — Final Project Closure & Engineering Portfolio Summary

**Project Title:** FlowDesk — AI-Assisted IT Service Operations Platform  
**Repository:** [https://github.com/Vibhushan-2003/flowdesk](https://github.com/Vibhushan-2003/flowdesk)  
**Final Release Tag:** `v1.0.0-demo`  
**Target Release Commit:** `5e124957c129777d1b30543b001b6a67b291a2ad`  
**Release URL:** [https://github.com/Vibhushan-2003/flowdesk/releases/tag/v1.0.0-demo](https://github.com/Vibhushan-2003/flowdesk/releases/tag/v1.0.0-demo)  
**Closure Date:** November 4, 2026  
**Project Status:** **COMPLETED & PUBLICLY VERIFIED (`RELEASED`)**

---

## 1. Executive Summary & Final Status

FlowDesk has achieved 100% project completion across all architecture, development, security hardening, automated testing, continuous integration, and live cloud deployment milestones.

The platform is running live in a public demonstration environment backed by a modern distributed monolith architecture, enterprise-grade access control, fail-closed malware security, synchronized rate limiting, human-in-the-loop Google Gemini AI assistance, and full relational persistence with zero data corruption.

---

## 2. Public Live Deployment Targets

| Component | Cloud Platform | URL | Active Operational State |
|---|---|---|---|
| **Frontend SPA** | Render Static Site (CDN) | [https://flowdesk-demo-glkr.onrender.com](https://flowdesk-demo-glkr.onrender.com) | **Operational** (SPA Rewrites Active) |
| **Backend API** | Render Docker Web Service | [https://flowdesk-demo-api.onrender.com](https://flowdesk-demo-api.onrender.com) | **Operational** (Tuned 512 MB cgroup) |
| **Database** | Neon Serverless PostgreSQL | `ep-dry-water-a1...neon.tech` (TLS) | **Operational** (Flyway v1–v25 applied) |
| **Liveness Health** | HTTP GET `/api/health` | [https://flowdesk-demo-api.onrender.com/api/health](https://flowdesk-demo-api.onrender.com/api/health) | `200 OK: Backend is running!` |
| **Readiness Health** | HTTP GET `/api/health/ready` | [https://flowdesk-demo-api.onrender.com/api/health/ready](https://flowdesk-demo-api.onrender.com/api/health/ready) | `200 OK: {"status":"READY","commit":"..."}` |
| **Version Probe** | HTTP GET `/api/health/version` | [https://flowdesk-demo-api.onrender.com/api/health/version](https://flowdesk-demo-api.onrender.com/api/health/version) | `200 OK: {"status":"UP","commit":"...","branch":"main"}` |
| **Frontend Version**| HTTP GET `/version.json` | [https://flowdesk-demo-glkr.onrender.com/version.json](https://flowdesk-demo-glkr.onrender.com/version.json) | `200 OK: {"status":"UP","commit":"...","branch":"main"}` |

---

## 3. Final Release Version & Commit

- **Release Tag:** [`v1.0.0-demo`](https://github.com/Vibhushan-2003/flowdesk/releases/tag/v1.0.0-demo)
- **Release Title:** `FlowDesk v1.0.0 — Public Portfolio Demo`
- **Target Git SHA:** `5e124957c129777d1b30543b001b6a67b291a2ad` (Merged PR #65)
- **Deployment Match:** Verified identical commit SHA deployed across both backend API and frontend CDN.

---

## 4. Architecture Summary

```
                      +------------------------------------------+
                      |         Web & Mobile Browsers            |
                      +------------------------------------------+
                                     |            |
                         HTTPS GET /*|            | HTTPS /api/* + WSS /ws
                                     v            v
           +-------------------------------+  +---------------------------------+
           |      Render Static CDN        |  |  Render Docker Web Service      |
           |  - React 19 / TypeScript SPA  |  |  - Java 21 / Spring Boot 3.4+   |
           |  - Vite 6 Production Bundle   |  |  - Embedded Tomcat ($PORT)      |
           |  - Client Routing: /* -> /    |  |  - Memory-Tuned JVM (512 MB)    |
           +-------------------------------+  +---------------------------------+
                                                              |
                                               +--------------+--------------+
                                               |                             |
                                      JDBC TLS |                    HTTPS API|
                                               v                             v
                              +---------------------------------+  +--------------------+
                              |   Neon Serverless PostgreSQL    |  |  Google Gemini AI  |
                              |  - TLS sslmode=require          |  |  - Flash Lite Model|
                              |  - Flyway Migrations (v1-v25)   |  |  - Copilot & Triage|
                              |  - HikariCP Pool (Cap: 4)       |  +--------------------+
                              +---------------------------------+
```

FlowDesk is architected as a modular distributed monolith:
- **Clean Architecture & Separation of Concerns:** Core domains (`ticket`, `user`, `auth`, `sla`, `approval`, `kb`, `attachment`, `ai`, `audit`) communicate via well-defined domain boundaries and service interfaces.
- **Relational Integrity:** PostgreSQL enforces foreign key constraints, composite indexes, and ACID guarantees.
- **Stateless Web Tier:** Horizontal scalability with sliding-window state stored securely in partitioned persistence.

---

## 5. Major Implemented Capabilities

### 5.1 Incident & Ticket Management
- State machine with strict transition guards (`NEW` → `ASSIGNED` → `IN_PROGRESS` → `RESOLVED` → `CLOSED`).
- Dynamic priority assignment (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`) with automatic SLA target calculation.
- Support queues, agent reassignment, internal private notes vs customer comments, and audit logging.

### 5.2 SLA & Governance Engine
- Background SLA compliance monitor powered by Spring `@Scheduled` synchronized across nodes via ShedLock.
- Automated warning alerts at 75% elapsed SLA and breach flags at 100% threshold.

### 5.3 Multi-Stage Approval Chains
- Configurable approval policies for high-priority or sensitive operations.
- Dedicated Approval Inbox with approve/reject workflows and tamper-evident audit logging.

### 5.4 Knowledge Base & Self-Service Deflection
- Article authoring, categorization, tagging, and publication states (`DRAFT`, `PUBLISHED`, `ARCHIVED`).
- Real-time client-side search with deflection modal prompting users with matching articles prior to ticket creation.

### 5.5 Authentication & Dual-Path Identity
- **JWT Authentication:** 15-minute access tokens paired with cryptographically rotated refresh cookies (`HttpOnly`, `SameSite=None`, `Secure`).
- **Google Cloud OAuth 2.0:** Secure identity federation with state verification, token exchange, and account linking.
- **Invitation-Only Security:** Zero public self-registration. Uninvited Google OAuth logins are rejected with HTTP 403.
- **Zero-SMTP Copyable Invitation Link:** Secure administrator-only invitation link issuance for zero-outbound-email hosting environments.

### 5.6 Human-in-the-Loop Gemini AI Assistance
- **AI Triage:** Analyzes ticket description to recommend category, urgency, and routing.
- **AI Copilot & Draft Replies:** Generates context-aware customer responses for agent review and one-click insertion.
- **Resolution Summaries:** Generates concise executive post-incident summaries synthesizing conversation history.

---

## 6. Comprehensive Test Results

FlowDesk maintains an automated test harness executed continuously on every pull request and push to `main`:

| Test Suite | Framework / Tool | Count | Status | Notes |
|---|---|---|---|---|
| **Backend Unit & Integration** | JUnit 5, Mockito, MockMvc, AssertJ | **1,005** | **PASS** (0 failed, 1 skipped) | Covers auth, tickets, SLAs, rate limits, AI, malware scanners |
| **End-to-End User Journeys** | Playwright (Chromium Headless) | **42** | **PASS** (0 failed) | Full browser tests for login, queues, approvals, tickets, KB |
| **Frontend Code Quality** | ESLint + TypeScript Compiler | **Clean** | **PASS** (0 errors, 0 warnings) | Strict type checking and lint rules |
| **Dependency Security Audit** | npm audit (`--audit-level=high`) | **Clean** | **PASS** (0 vulnerabilities) | High/critical dependency advisories resolved |
| **Readiness Smoke Contract** | Python `unittest` | **17** | **PASS** (0 failed) | Validates HTTP 200 `READY` and 503 `UNAVAILABLE` contracts |
| **Live Deployment SRE Verifier** | Python SRE Verifier | **11** | **PASS** (0 failed) | Live public end-to-end cloud validation |

---

## 7. CI/CD Verification & Quality Gates

The GitHub Actions pipeline ([`.github/workflows/ci.yml`](https://github.com/Vibhushan-2003/flowdesk/blob/main/.github/workflows/ci.yml)) enforces three automated quality gates on every Pull Request:

1. **`Backend Build & Test`:** Runs on Ubuntu Latest with OpenJDK 21 and a live PostgreSQL 18 service container. Executes 1,005 unit and integration tests.
2. **`Frontend Build & Lint`:** Runs on Node 24. Executes ESLint, `npm audit --audit-level=high`, and Vite production bundle generation.
3. **`Real-Stack E2E Acceptance`:** Spins up PostgreSQL, boots the Spring Boot backend JAR, serves the Vite SPA, seeds test data, and runs the complete 42-test Playwright suite.
4. **`verify-live` (Workflow Dispatch):** Manual trigger executing `verify_live_deployment.py` directly against the live cloud infrastructure.

---

## 8. Security Safeguards & Threat Mitigations

1. **Fail-Closed Malware Architecture:** On Render Free (where ClamAV's >1.2 GB daemon exceeds the 512 MB cgroup memory cap), attachment uploads return HTTP 503 Service Unavailable rather than bypassing virus inspection or using unverified mock scanners.
2. **Dual-Quota Sliding-Window Rate Limiting:** Synchronized per-IP (30 req/min) and per-IP+Email (5 req/5min) sliding-window limits protect authentication endpoints.
3. **RFC 1918 Proxy Validation:** `ClientIpResolver` validates upstream proxy headers against trusted CIDR ranges, preventing `X-Forwarded-For` client spoofing.
4. **Internal Actuator Lockdown:** Actuator Prometheus and management metrics endpoints return HTTP 404/403 to the public internet.
5. **Security Headers:** Strict Transport Security (`HSTS`), `X-Content-Type-Options: nosniff`, and `X-Frame-Options: DENY` enforced on all responses.
6. **Payload Guard:** Rejects oversized request payloads (>8 KB) on login with HTTP 413 prior to allocating JSON parser memory buffers.

---

## 9. Current Render Free Operational Characteristics

To maintain engineering transparency:

| Characteristic | Free Tier Behavior | FlowDesk Design Response |
|---|---|---|
| **Memory Limit** | Strict 512 MB cgroup limit | JVM tuned with Serial GC (`-XX:+UseSerialGC`), 224 MB heap (`-Xmx224m`), and HikariCP capped at 4 connections. RSS stable at ~360 MB. |
| **Inactivity Spin-down** | Container sleeps after 15 min idle | Initial cold-start takes ~30–50s. Subsequent requests execute with <50ms latency. SRE verifier includes auto-polling warm-up. |
| **Outbound SMTP Block** | Render blocks ports 25, 465, 587 | Email delivery is disabled (`FLOWDESK_MAIL_ENABLED=false`). Administrator copyable invitation link mechanism enables seamless onboarding. |
| **Ephemeral Disk** | Local files wiped on redeployment | File attachments are gated behind fail-closed HTTP 503 to prevent data loss. |

---

## 10. Known Non-Blocking Limitations

1. **Email Delivery on Free Tier:** Live demo uses the Copy Invitation Link modal due to Render Free platform egress port blocking. Full SMTP email delivery is implemented and verified for standard production environments (OCI / self-hosted).
2. **Attachment Uploads on Free Tier:** Disabled via HTTP 503 by design due to memory constraints preventing ClamAV operation. Full streaming ClamAV malware scanning is verified in the OCI production Docker Compose stack.
3. **Cold-Start Latency:** First request after 15 minutes of inactivity experiences a ~30–50s JVM spin-up delay.

---

## 11. Operational Monitoring & Observability

- **Liveness Endpoint:** `/api/health` returns HTTP 200 with `Backend is running!`.
- **Readiness Endpoint:** `/api/health/ready` validates active database pool connectivity (`isValid(2)`) and reports deployed Git commit SHA.
- **Version Endpoint:** `/api/health/version` exposes active Git commit and branch for deployment tracking.
- **Live SRE Verifier:** `python scripts/sre/verify_live_deployment.py` validates end-to-end health in 0.5s.

---

## 12. Deployment Rollback Runbook

If an operational incident occurs in production:
1. **GitHub Rollback:**
   ```bash
   git revert <commit-sha> -m "revert: rollback due to operational incident"
   git push origin main
   ```
2. **Render Dashboard Instant Rollback:**
   - Navigate to Render Dashboard → `flowdesk-demo-api` → **Deploys**.
   - Select previous stable deploy (`v1.0.0-demo` / commit `5e12495`) and click **Rollback to this deploy**.
3. **Database Schema Safety:**
   - All Flyway migrations (v1–v25) are additive and backward-compatible. No destructive column drops are permitted.

---

## 13. Future OCI Production Deployment Readiness

FlowDesk is fully architected and rehearsed for its primary production target on Oracle Cloud Infrastructure (OCI) Always Free ARM64 (`VM.Standard.A1.Flex`, 4 OCPU, 24 GB RAM):
- **Docker Compose Stack:** Multi-stage production compose configuration (`docker-compose.prod.yml`) managing 9 isolated services.
- **Caddy Edge Ingress:** Automatic Let's Encrypt TLS, HTTP/2, WebSocket proxying, and strict `/actuator` edge blocking.
- **ClamAV Native Streaming:** Live container streaming attachments through ClamAV via TCP `INSTREAM` protocol.
- **Prometheus & Grafana:** Preconfigured dashboards and Prometheus alert rules (`FlowDeskBackupMissingOrFailed`, `FlowDeskHighErrorRate`).
- **Cryptographic Backups:** Automated daily backups with GPG encryption, SHA-256 manifests, and atomic restoration drills verified.

---

## 14. CV-Ready Project Description

**FlowDesk — AI-Assisted IT Service Operations Platform (Full-Stack / DevOps / SRE)**  
*Java 21, Spring Boot 3.4, React 19, TypeScript, PostgreSQL (Neon), Docker, GitHub Actions, Google Gemini AI*
- Architected and deployed an enterprise IT service platform featuring ticket lifecycles, SLA monitoring, multi-stage approval chains, self-service deflection, and role-based access control (RBAC).
- Implemented human-in-the-loop AI workflows using Google Gemini for automated ticket triage, contextual agent copilot response drafting, and post-incident resolution summaries.
- Hardened authentication with dual-path JWT access/refresh rotation cookies, Google OAuth 2.0 federation, invitation-only onboarding, and synchronized sliding-window rate limiting.
- Designed a fail-closed malware security architecture pairing ClamAV TCP streaming with cgroup-constrained memory isolation and RFC 1918 proxy IP extraction.
- Established end-to-end automated CI/CD pipelines in GitHub Actions enforcing 1,005 backend tests, 42 Playwright E2E browser tests, ESLint quality gates, and automated SRE live deployment health verification.

---

## 15. Interview-Ready Technical Highlights

1. **How do you handle memory-bounded containers in production?**  
   *"When deploying on Render Free's 512 MB cgroup limit, a standard JVM will crash with OOM (exit code 137). I tuned the JVM with Serial GC (`-XX:+UseSerialGC`), capped max heap at 224 MB (`-Xmx224m`), Metaspace at 128 MB, and thread stack at 512 KB, while bounding HikariCP to 4 connections. This reduced baseline RSS from >600 MB to ~360 MB, running stably under sustained traffic."*

2. **How does your architecture handle security when a subsystem is unavailable?**  
   *"We enforce a strict fail-closed policy. ClamAV requires >1.2 GB RAM, making it impossible to run within 512 MB. Rather than bypassing virus scans or using an insecure mock, FlowDesk conditionally gates file uploads behind an immediate HTTP 503 Service Unavailable, while decoupling Actuator health checks so the web service remains marked healthy. When running on our full OCI production stack, attachments stream through ClamAV via native TCP INSTREAM chunking."*

3. **How do you prevent IP spoofing in rate-limited authentication?**  
   *"Attackers often spoof the `X-Forwarded-For` header to bypass rate limits. FlowDesk implements `ClientIpResolver`, which checks whether the immediate TCP peer is a trusted reverse proxy (RFC 1918 CIDRs). Only trusted proxy headers are parsed from right to left to identify the true client IP. Dual-quota sliding windows then enforce both per-IP (30 req/min) and per-IP+Email (5 req/5min) quotas with atomic synchronization."*

4. **How did you solve onboarding in a zero-SMTP environment?**  
   *"Render Free blocks outbound SMTP ports (25, 465, 587) to prevent spam. Instead of weakening authentication or opening self-registration, I designed an administrator-only 'Copy Invitation Link' feature. When outbound email is disabled, the invitation service cryptographically generates an activation token, hashes it in PostgreSQL, and securely returns the full URL only to an authorized administrator. Raw tokens are never logged or exposed in audit trails, preserving strict invitation-only Google OAuth linking."*

---

## 16. Verification Sign-Off

- **Lead Software Engineer:** Vibhushan-2003
- **QA & Testing:** 1,005 Unit/Integration Tests PASS | 42 Playwright Tests PASS
- **SRE & DevOps:** 11/11 Live Deployment Verification Checks PASS
- **Release Decision:** **RELEASED — v1.0.0-demo verified and live**
