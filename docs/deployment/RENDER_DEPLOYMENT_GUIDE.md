# FlowDesk — Render Free Temporary Demo Deployment Guide

## 1. Executive Summary

This guide provides operational instructions for deploying and running a temporary, publicly accessible demonstration of **FlowDesk** on the **Render Free Tier**.

The recommended configuration uses:
- A **React/Vite Static Site** for zero-cost, high-speed frontend hosting with SPA routing.
- A **Spring Boot Docker Web Service** tuned to execute within Render Free's 512 MB memory boundary.
- A free **PostgreSQL Database** — either **Neon PostgreSQL** (100% free serverless Postgres, **no credit card required**) or **Render PostgreSQL** (free tier, payment card verification required by Render in certain workspaces), running schema migrations automatically via Flyway (v1 through v25).

The production Oracle Cloud Infrastructure (OCI) architecture (`docker-compose.prod.yml`, Caddy reverse proxy, ClamAV, Prometheus/Grafana stack) remains completely untouched.

---

## 2. Render Free Operational Characteristics & Trade-offs

| Characteristic | Render Free Behavior | FlowDesk Mitigation / Design |
|---|---|---|
| **Memory Limit** | 512 MB RAM strict cgroup. Container killed if RSS exceeds limit (exit code 137). | Tuned JVM via `JAVA_TOOL_OPTIONS`: Serial GC (`-XX:+UseSerialGC`), 224 MB heap max (`-Xmx224m`), 128 MB Metaspace (`-XX:MaxMetaspaceSize=128m`), 512 KB thread stack (`-Xss512k`). Runtime RSS sits comfortably at ~360 MB. HikariCP pool capped at 4 connections (`DB_POOL_MAX_SIZE=4`). |
| **Inactivity Spin-down** | Free web services automatically spin down to 0 instances after 15 minutes of inactivity. | When a request arrives, Render triggers a cold start (~50–60s). Health probe `/api/health` returns within ~8s of container start. In the frontend, users may see a brief initial loading delay on first visit. |
| **Storage & Ephemeral Disk** | File system changes are wiped on every container restart, re-deploy, or sleep/wake cycle. | ClamAV scanner (>1.2 GB RAM) cannot run on Free tier. To prevent unverified malware uploads and data loss, **attachment uploads are explicitly disabled** (`app.storage.attachment.upload-enabled=false`) with an immediate HTTP 503 Service Unavailable response. ClamAV fail-closed security is preserved without compromising safety. |
| **Database Provisioning & Card Gate** | Render requires payment card verification to create a Render PostgreSQL database on many accounts, and free Render DBs expire after 30 days. | **Neon PostgreSQL** (serverless Postgres, 100% free, **no credit card required**) is fully supported and recommended. Standard TLS connection (`sslmode=require`) connects securely to either Neon or Render PostgreSQL. |
| **Network Boundaries & SMTP** | **Render Free completely blocks outbound SMTP traffic on ports 25, 465, and 587** to prevent spam abuse. | Direct JavaMail SMTP will fail on Render Free. Email is **strictly disabled** for the demo (`FLOWDESK_MAIL_ENABLED=false`). User registration, password resets, ticket workflows, and approvals operate fully without email. |
| **Cross-Site Cookies** | `onrender.com` is on the Public Suffix List (PSL). `app.onrender.com` and `api.onrender.com` are cross-site. | Refresh token cookies are configured with `SameSite=None; Secure=true; HttpOnly`. Browser cross-site `fetch` with `credentials: 'include'` works smoothly. |
| **Initial Admin Bootstrap** | Free web services run continuously (not via one-shot CLI commands). Calling `System.exit()` would kill the web service. | `AdminBootstrapRunner` provides an idempotent in-service bootstrap. If the database has no users, it provisions the initial administrator and allows Tomcat to continue running. If users already exist, it skips cleanly. Plaintext credentials are wiped from memory immediately after execution. |

---

## 3. Architecture Blueprint

```mermaid
flowchart TD
    Client["Browser / Client (Desktop & Mobile)"]
    
    subgraph Render["Render Cloud (Free Tier)"]
        subgraph StaticSite["Render Static Site (CDN)"]
            Frontend["React 19 / Vite SPA\n(Rewrites: /* -> /index.html)"]
        end
        
        subgraph WebService["Render Docker Web Service (512 MB)"]
            Tomcat["Embedded Tomcat\n(Port: $PORT)"]
            SpringApp["Spring Boot 4 / OpenJDK 21\n(Profile: render)"]
            RateLimit["Rate Limiting\n(Trusts X-Forwarded-For)"]
            AdminBoot["Idempotent Admin Bootstrap\n(No exit, memory wiped)"]
            Tomcat --> RateLimit --> SpringApp
            SpringApp -.-> AdminBoot
        end
    end

    subgraph DatabaseOptions["PostgreSQL Database (Choose One)"]
        Neon["Neon Serverless PostgreSQL\n(100% Free, NO Credit Card Required)\nsslmode=require"]
        RenderPG["Render Managed PostgreSQL\n(Free 30-day, Card Verification)\nsslmode=require"]
    end

    Client -->|"HTTPS GET /*"| Frontend
    Client -->|"HTTPS /api/* (REST + JWT)"| Tomcat
    Client -->|"WSS /ws (STOMP Realtime)"| Tomcat
    SpringApp -->|"JDBC TLS (Hikari max 4)"| Neon
    SpringApp -.->|"Alternative JDBC TLS"| RenderPG
```

---

## 4. Feature Matrix for Render Free

| Feature Area | Status on Render Free | Operational Notes |
|---|---|---|
| **User Authentication & Session Rotation** | **Fully Functional** | Full email/password login, JWT access tokens (15m), and rotating refresh tokens via secure cookies (`SameSite=None; Secure`). |
| **Role-Based Access Control (RBAC)** | **Fully Functional** | EMPLOYEE, SUPPORT_ENGINEER, ADMIN role hierarchies and route guards operate normally. |
| **Ticket Lifecycle & Workflows** | **Fully Functional** | Ticket creation, priority management, assignment, resolution, transitions, and history tracking work in PostgreSQL. |
| **Multi-Stage Approval Chains** | **Fully Functional** | Approval workflows, pending approvals inbox, approvals audit trail function natively. |
| **SLA Tracking & Breach Monitoring** | **Fully Functional** | Real-time SLA indicators, countdowns, and distributed ShedLock task execution work properly. |
| **Knowledge Base (KB)** | **Fully Functional** | Public article viewing, search, category management, and authoring operate normally. |
| **Audit Trail** | **Fully Functional** | Durable audit logs record all user, ticket, and authentication actions in PostgreSQL. |
| **Realtime Notifications (WebSocket)** | **Fully Functional** | STOMP over SockJS (`/ws`) connects securely via `wss://.../ws`. Comma-separated CORS origins allow cross-origin handshakes. |
| **First Admin Bootstrap** | **Fully Functional** | Provisions initial admin on first boot via environment variables. Does not terminate the web service; credentials wiped from memory immediately. |
| **AI Copilot & Triage (Gemini)** | **Functional with External Key** | Requires `GEMINI_API_KEY` from Google AI Studio. Gracefully dormant if key is omitted. |
| **Google OAuth 2.0 (SSO)** | **Functional with External Setup** | Requires Client ID and Secret configured in Google Cloud Console with matching Render redirect URIs. Gracefully dormant if disabled. |
| **Transactional Email** | **Disabled (Render Free Limitation)** | **Render Free blocks ports 25, 465, and 587.** Direct JavaMail SMTP will fail. Email is disabled (`FLOWDESK_MAIL_ENABLED=false`). FlowDesk runs completely without email. |
| **Attachment Storage & Uploads** | **Unavailable Safely** | Explicitly blocked with HTTP 503. Preserves fail-closed malware security and avoids data loss on ephemeral storage. |

---

## 5. Database Options: Neon PostgreSQL vs. Render PostgreSQL

Render recently introduced payment card verification to provision free Render PostgreSQL instances. To maintain a truly zero-cost, card-free deployment, **Neon PostgreSQL** is the primary recommended database:

### Option A: Neon Serverless PostgreSQL (Recommended — No Card Required)
1. Sign up at [neon.tech](https://neon.tech) (using GitHub or Google OAuth, no payment card requested).
2. Create a new project named `flowdesk-demo` (PostgreSQL 16, choose the region closest to your Render service, e.g., Singapore `ap-southeast-1` or US East `us-east-2`).
3. Neon immediately generates a connection string:
   ```text
   postgresql://flowdesk_owner:npg_xxxx@ep-cool-cloud-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
   ```
4. Construct the Spring Boot JDBC parameters:
   - `DB_URL`: `jdbc:postgresql://ep-cool-cloud-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require`
   - `DB_USERNAME`: `flowdesk_owner` (or the user provided by Neon)
   - `DB_PASSWORD`: `<your-neon-password>`
   - `DB_POOL_MAX_SIZE`: `4`
   - `DB_POOL_MIN_IDLE`: `1`
5. Neon supports automatic branch suspension and instant resumption, fitting Render Free's sleep-wake cycles perfectly.

### Option B: Render Managed PostgreSQL
1. If your Render account has card verification active, you can click **New + > PostgreSQL** in the Render Dashboard.
2. Select the **Free** tier (expires in 30 days).
3. Use the **Internal Database URL** provided on the database overview page:
   - `DB_URL`: `jdbc:postgresql://<internal-host>:5432/<dbname>?sslmode=require`
   - `DB_USERNAME`: `<db-user>`
   - `DB_PASSWORD`: `<db-password>`

---

## 6. Render Web Service Configuration (Root Directory & Dockerfile Paths)

A frequent point of failure when deploying Docker services on Render is the interaction between **Root Directory**, **Dockerfile Path**, and **Docker Context**.

Ensure you match one of the two consistent configuration patterns below:

### Pattern 1: Empty Root Directory (Recommended Default)
- **Root Directory:** *(Leave completely blank / empty)*
- **Dockerfile Path:** `./backend/Dockerfile`
- **Docker Context:** `./backend`
- *How it works:* Render builds from the root of the Git repository. It resolves `./backend/Dockerfile` and uses `./backend` as the build context for copying `pom.xml` and `src`.

### Pattern 2: Root Directory set to `backend`
- **Root Directory:** `backend`
- **Dockerfile Path:** `Dockerfile` (or `./Dockerfile`)
- **Docker Context:** `.` (or `./`)
- *How it works:* Render changes directory into `backend/` first. Paths must therefore be relative to the `backend` folder. **Do NOT enter `./backend/Dockerfile` here**, as Render would look for `backend/backend/Dockerfile` and abort the build.

---

## 7. In-Service Initial Administrator Provisioning

FlowDesk includes a robust administrator bootstrap mechanism that operates in two modes:

1. **OCI Production CLI Mode:** Triggered by passing `--flowdesk.bootstrap.admin` command-line arguments to the container. Starts Spring Boot in non-web mode (`WebApplicationType.NONE`), creates the administrator, and calls `System.exit(0)`.
2. **Render Web Service In-Service Mode:** Triggered by setting environment variables `FLOWDESK_BOOTSTRAP_ADMIN_ENABLED=true`, `FLOWDESK_BOOTSTRAP_ADMIN_EMAIL`, and `FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD`.

### In-Service Bootstrap Behavior:
- **Starts as Web Service:** The application boots normally as `WebApplicationType.SERVLET` and listens on `$PORT`.
- **Idempotent Check:** `AdminBootstrapRunner` checks if any user accounts already exist in the database (`hasExistingUsers()`).
  - If the database is empty: The administrator account is created with `ADMIN` role and active status. The web service **continues running** without calling `System.exit()`.
  - If users already exist: Provisioning is safely skipped with a log message.
- **Memory Scrubbing:** In all paths, the runner executes a `finally` block that sets `properties.setPassword(null)` and `properties.setEnabled(false)` to scrub plaintext credentials from the Spring environment bean in memory.
- **Post-Deploy Cleanup:** After the initial deployment succeeds and you verify login, navigate to the Render Web Service **Environment** tab, set `FLOWDESK_BOOTSTRAP_ADMIN_ENABLED=false` (or remove the password variable), and click **Save Changes**.

---

## 8. Verification Checklist

After deploying the services on Render, execute these verification checks:

1. **Readiness Probe:**
   ```bash
   curl -i https://<your-backend-service>.onrender.com/api/health/ready
   # Expected: HTTP 200 {"status":"READY"}
   ```

2. **Actuator Lockdown Check:**
   ```bash
   curl -i https://<your-backend-service>.onrender.com/actuator/prometheus
   # Expected: HTTP 404 Not Found (Actuator endpoints disabled in render profile)
   ```

3. **Login and Refresh Cookie Verification:**
   Log in via the frontend (`https://<your-static-site>.onrender.com/login`).
   In browser DevTools > Application > Cookies, verify `flowdesk_refresh_token`:
   - `HttpOnly`: `true`
   - `Secure`: `true`
   - `SameSite`: `None`

4. **Ticket Attachment Guard Verification:**
   In any open ticket, attempt to upload an attachment. Verify that the UI/API returns HTTP 503 Service Unavailable with the clear explanation rather than crashing or bypassing scanner checks.

