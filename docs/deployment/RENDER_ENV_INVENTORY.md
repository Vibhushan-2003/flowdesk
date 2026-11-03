# FlowDesk — Render Free Deployment Environment Variable Inventory

**Target Environment:** Render Free Tier (Temporary Public Demo)  
**Security Posture:** Zero Secret Leakage, Minimal Footprint, Fail-Closed  
**Spring Boot Profile:** `render`  
**Frontend Type:** Render Static Site  
**Backend Type:** Render Docker Web Service  
**Database Options:** Neon Serverless PostgreSQL (Recommended, No Credit Card Required) or Render Managed PostgreSQL  

---

## 1. Overview & Constraint Analysis

Render Free provides:
- **Web Service:** 0.1 CPU, 512 MB RAM, ephemeral filesystem, dynamic `$PORT` binding, automatic TLS termination, 15-minute inactivity spin-down (cold boot ~50–60s).
- **PostgreSQL Database Options:**
  - **Neon PostgreSQL (Recommended):** Serverless PostgreSQL 16, 100% free with **no payment card required**, permanent storage, automatic sleep/wake, strict TLS (`sslmode=require`).
  - **Render PostgreSQL:** 1 GB storage, expires after 30 days, payment card verification required by Render on most new workspaces.
- **Static Site:** Fast CDN global hosting, custom SPA rewrites, zero server compute costs.
- **Network Boundaries (SMTP Block):** **Render Free completely blocks outbound SMTP traffic on ports 25, 465, and 587** to prevent email abuse and spam. FlowDesk's JavaMail SMTP transport cannot connect to external mail servers on Render Free. Therefore, email is **strictly disabled** for this demo (`FLOWDESK_MAIL_ENABLED=false`).
- **Cross-Site Cookies:** Cross-subdomain requests between `https://<frontend>.onrender.com` and `https://<backend>.onrender.com` are treated as cross-site by modern browsers due to `onrender.com` being registered on the Public Suffix List (PSL). Refresh cookies are configured with `SameSite=None; Secure=true; HttpOnly`.

To prevent OOM kills (137 exit code) inside the 512 MB cgroup limit and satisfy security guarantees:
1. Java heap and metaspace are constrained using `JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xmx224m -Xms96m -XX:MaxMetaspaceSize=128m -Xss512k -XX:+UseStringDeduplication"`.
2. HikariCP connection pool is bounded to `DB_POOL_MAX_SIZE=4` and `DB_POOL_MIN_IDLE=1`.
3. Refresh session cookies use `SameSite=None; Secure=true; HttpOnly`.
4. Attachment uploads are explicitly disabled (`app.storage.attachment.upload-enabled=false`) because persistent volumes and ClamAV (>1.2 GB RAM) are not viable on Render Free. ClamAV fail-closed security is 100% maintained with zero mock scanning bypass.
5. Ingress reverse proxy headers (`X-Forwarded-For`, `X-Forwarded-Proto`) are trusted via native Tomcat configuration.
6. Public Actuator Prometheus and metric endpoints are disabled; only `/api/health` is exposed.
7. Initial administrator bootstrap executes in-service without calling `System.exit()`, checks `hasExistingUsers()` to remain idempotent, and immediately scrubs credentials from memory in `finally`.

---

## 2. Complete Environment Variable Inventory Matrix

| Variable Name | Component | Classification | Default in Render Profile | Render Value / Recommended Value | Description & Security Notes |
|---|---|---|---|---|---|
| `VITE_API_BASE_URL` | Frontend Build | **Required** | *(None)* | `https://<your-backend-service>.onrender.com` | Public HTTPS origin of the backend web service. Baked into frontend static bundle at build time. Automatically resolved for REST and WSS (`wss://.../ws`). |
| `SPRING_PROFILES_ACTIVE` | Backend Runtime | **Required** | `render` | `render` | Activates `application-render.properties`. |
| `PORT` | Backend Runtime | **Provided by Render** | `8080` | *(Injected dynamically by Render, e.g. 10000)* | Port on which the Spring Boot Tomcat container must bind. `server.port=${PORT:8080}` maps this automatically. |
| `DB_URL` | Backend Runtime | **Required** | *(None)* | `jdbc:postgresql://<host>/<dbname>?sslmode=require` | JDBC connection URL with required TLS encryption. For Neon: `jdbc:postgresql://<neon-endpoint>.neon.tech/<dbname>?sslmode=require`. For Render Postgres: `jdbc:postgresql://<internal-host>:5432/<dbname>?sslmode=require`. |
| `DB_USERNAME` (or `DB_USER`) | Backend Runtime | **Required** | *(None)* | `<db-user>` | PostgreSQL database username (e.g., `flowdesk_owner` for Neon, or `flowdesk_user` for Render). |
| `DB_PASSWORD` | Backend Runtime | **Required** | *(None)* | `<db-password>` | PostgreSQL database password. |
| `JWT_SECRET` | Backend Runtime | **Required** | *(None)* | *(Fresh 256-bit Base64 secret)* | Cryptographic HMAC-SHA256 signing key for access tokens. Must be at least 32 bytes (44 Base64 chars). |
| `FRONTEND_ORIGIN` | Backend Runtime | **Required** | *(None)* | `https://<your-static-site>.onrender.com` | Allowed CORS origin. Also whitelisted for security headers. Comma-separated origins are supported if multiple are needed. |
| `JAVA_TOOL_OPTIONS` | Backend Runtime | **Required** | *(None)* | `-XX:+UseSerialGC -Xmx224m -Xms96m -XX:MaxMetaspaceSize=128m -Xss512k -XX:+UseStringDeduplication` | Essential JVM ergonomics tuning. Prevents JVM from allocating default 75% RAM and exceeding 512 MB cgroup. |
| `FLOWDESK_BOOTSTRAP_ADMIN_ENABLED` | Backend Runtime | **One-Shot Boot** | `false` | `true` (for initial boot, then `false`) | When `true`, provisions the initial ADMIN account if the database has no users (`hasExistingUsers() == false`). Does NOT exit the web service. Memory credentials wiped in `finally`. After first boot, set back to `false`. |
| `FLOWDESK_BOOTSTRAP_ADMIN_EMAIL` | Backend Runtime | **Conditional** | *(Empty)* | `admin@flowdesk.demo` | Email for the initial administrator account. Required if bootstrap enabled. |
| `FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD` | Backend Runtime | **Conditional** | *(Empty)* | *(Fresh strong password, min 12 chars)* | Password for the initial administrator account. Required if bootstrap enabled. |
| `FLOWDESK_BOOTSTRAP_ADMIN_FIRST_NAME` | Backend Runtime | **Optional** | `Demo` | `System` | First name for the initial administrator. |
| `FLOWDESK_BOOTSTRAP_ADMIN_LAST_NAME` | Backend Runtime | **Optional** | `Admin` | `Administrator` | Last name for the initial administrator. |
| `REFRESH_TOKEN_COOKIE_SAMESITE` | Backend Runtime | **Optional** | `None` | `None` | SameSite attribute for the refresh token cookie. Required to be `None` for cross-site static frontend requests. |
| `REFRESH_TOKEN_COOKIE_SECURE` | Backend Runtime | **Optional** | `true` | `true` | Enforces `Secure` flag on refresh token cookie. Required when `SameSite=None`. |
| `DB_POOL_MAX_SIZE` | Backend Runtime | **Optional** | `4` | `4` | Maximum HikariCP pool connections. Keep <= 5 on Render Free. |
| `DB_POOL_MIN_IDLE` | Backend Runtime | **Optional** | `1` | `1` | Minimum HikariCP idle connections. |
| `FLOWDESK_MAIL_ENABLED` | Backend Runtime | **Enforced Disabled** | `false` | `false` | **Must remain `false`.** Render Free blocks all outbound SMTP ports (25, 465, 587). Direct JavaMail SMTP will fail. |
| `GEMINI_API_KEY` | Backend Runtime | **Optional / Safe to Disable** | *(Empty)* | `<your-google-ai-studio-api-key>` | Optional Google Gemini API key for AI Copilot, triage suggestions, and ticket summarization. |
| `GEMINI_MODEL` | Backend Runtime | **Optional** | `gemini-3.1-flash-lite` | `gemini-3.1-flash-lite` | Gemini model name. |
| `GOOGLE_OAUTH_ENABLED` | Backend Runtime | **Optional / Safe to Disable** | `false` | `false` (or `true` if OAuth configured) | Enables Google Single Sign-On and account linking. |
| `GOOGLE_CLIENT_ID` | Backend Runtime | **Conditional** | *(Empty)* | `<google-oauth-client-id>` | Google Cloud OAuth 2.0 Web Application Client ID. |
| `GOOGLE_CLIENT_SECRET` | Backend Runtime | **Conditional** | *(Empty)* | `<google-oauth-client-secret>` | Google Cloud OAuth 2.0 Client Secret. |
| `GOOGLE_REDIRECT_URI` | Backend Runtime | **Conditional** | *(Empty)* | `https://<your-static-site>.onrender.com/auth/google/callback` | Authorized redirect URI configured in Google Cloud Console. |
| `RATE_LIMIT_LOGIN_ENABLED` | Backend Runtime | **Optional** | `true` | `true` | Enables login rate limiter. |
| `RATE_LIMIT_LOGIN_TRUST_PROXY` | Backend Runtime | **Optional** | `true` | `true` | Trusts proxy client IP resolution from Render edge headers. |

---

## 3. Secret Generation Guidelines

Never commit or reuse secrets from development, test, or existing repositories. Generate unique credentials using standard cryptographic tools:

### A. JWT Secret Key (HMAC-SHA256)
Generate a 256-bit (32 bytes) random secret encoded in Base64:
```bash
# Linux / macOS / Git Bash
openssl rand -base64 32

# Windows PowerShell
$bytes = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes); [Convert]::ToBase64String($bytes)
```

### B. Administrator Bootstrap Password
Generate a high-entropy password (min 16 characters):
```bash
# Linux / macOS / Git Bash
openssl rand -base64 18 | tr -dc 'a-zA-Z0-9!@#%^&*'

# Windows PowerShell
-join ((33..126) | Get-Random -Count 20 | ForEach-Object {[char]$_})
```

---

## 4. Safe Demo Toggles (Minimal Configuration)

For a fast, zero-cost, temporary public demonstration without configuring third-party providers:
1. **Email Subsystem Enforced Disabled:** Set `FLOWDESK_MAIL_ENABLED=false`. Render Free blocks outbound SMTP traffic on ports 25, 465, and 587. All user management, roles, ticket workflows, SLA monitoring, audit logs, and knowledge base work natively without requiring email delivery.
2. **Disable Google OAuth:** Set `GOOGLE_OAUTH_ENABLED=false`. Standard secure username/password login and JWT refresh session rotation work natively.
3. **Disable Gemini AI:** Leave `GEMINI_API_KEY=` empty. AI triage and reply suggestions will gracefully report unavailable; core ticketing and manual workflows are 100% operational.
4. **Attachment Uploads Blocked:** Uploads are disabled by default in the `render` profile (`app.storage.attachment.upload-enabled=false`) returning HTTP 503 Service Unavailable with an explanation. Existing attachments (if any) can still be inspected, but no unverified uploads occur on ephemeral disks.

---

## 5. Web Service Configuration Paths (Root Directory & Dockerfile)

Render allows setting an optional **Root Directory** when creating a Web Service. The Dockerfile path and build context depend directly on this setting:

### Option 1: Empty Root Directory (Recommended Default)
- **Root Directory:** *(Leave blank)*
- **Dockerfile Path:** `./backend/Dockerfile`
- **Docker Context:** `./backend`
- *Behavior:* Render builds from the repository root. It finds `./backend/Dockerfile` and sets the context to `./backend` so that `pom.xml` and `src` are copied correctly into the Docker build container.

### Option 2: Root Directory set to `backend`
- **Root Directory:** `backend`
- **Dockerfile Path:** `Dockerfile` (or `./Dockerfile`)
- **Docker Context:** `.` (or `./`)
- *Behavior:* Render `cd`s into `backend/` before invoking Docker. The Dockerfile is located right there (`Dockerfile`) and the context is current directory (`.`). **Do not use `./backend/Dockerfile` here**, as Render will look for `backend/backend/Dockerfile` and fail.

---

## 6. Safe Configuration Placeholder Templates

### Option A: Render Web Service with Neon PostgreSQL (Recommended — No Credit Card Required)
```env
SPRING_PROFILES_ACTIVE=render
DB_URL=jdbc:postgresql://<neon-endpoint>.neon.tech/neondb?sslmode=require
DB_USERNAME=<neon-user>
DB_PASSWORD=<neon-password>
JWT_SECRET=<generated-base64-32-byte-secret>
FRONTEND_ORIGIN=https://<your-static-site>.onrender.com
JAVA_TOOL_OPTIONS=-XX:+UseSerialGC -Xmx224m -Xms96m -XX:MaxMetaspaceSize=128m -Xss512k -XX:+UseStringDeduplication
FLOWDESK_BOOTSTRAP_ADMIN_ENABLED=true
FLOWDESK_BOOTSTRAP_ADMIN_EMAIL=admin@flowdesk.demo
FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD=<generated-admin-password>
FLOWDESK_BOOTSTRAP_ADMIN_FIRST_NAME=System
FLOWDESK_BOOTSTRAP_ADMIN_LAST_NAME=Administrator
FLOWDESK_MAIL_ENABLED=false
GOOGLE_OAUTH_ENABLED=false
GEMINI_API_KEY=
```

### Option B: Render Web Service with Render PostgreSQL
```env
SPRING_PROFILES_ACTIVE=render
DB_URL=jdbc:postgresql://<render-db-internal-host>:5432/<render-db-name>?sslmode=require
DB_USERNAME=<render-db-user>
DB_PASSWORD=<render-db-password>
JWT_SECRET=<generated-base64-32-byte-secret>
FRONTEND_ORIGIN=https://<your-static-site>.onrender.com
JAVA_TOOL_OPTIONS=-XX:+UseSerialGC -Xmx224m -Xms96m -XX:MaxMetaspaceSize=128m -Xss512k -XX:+UseStringDeduplication
FLOWDESK_BOOTSTRAP_ADMIN_ENABLED=true
FLOWDESK_BOOTSTRAP_ADMIN_EMAIL=admin@flowdesk.demo
FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD=<generated-admin-password>
FLOWDESK_BOOTSTRAP_ADMIN_FIRST_NAME=System
FLOWDESK_BOOTSTRAP_ADMIN_LAST_NAME=Administrator
FLOWDESK_MAIL_ENABLED=false
GOOGLE_OAUTH_ENABLED=false
GEMINI_API_KEY=
```

### Render Static Site (Frontend Environment Variables)
```env
VITE_API_BASE_URL=https://<your-backend-service>.onrender.com
```

---

## 7. Chronological Render Dashboard Deployment Steps

Follow these steps in exact order to deploy FlowDesk to Render Free:

### Step 1: Provision the PostgreSQL Database

#### Choice 1A: Neon Serverless PostgreSQL (Recommended — 100% Free, No Credit Card Required)
1. Go to [neon.tech](https://neon.tech) and sign up with GitHub or Google.
2. Click **Create Project**, name it `flowdesk-demo`, select PostgreSQL 16, and choose the closest AWS region to your Render web service (e.g. Singapore `ap-southeast-1` or US East `us-east-2`).
3. Neon immediately displays your connection details. Copy the **Direct** or **Pooled** connection string.
4. Extract the host, database, user, and password:
   - Host: e.g., `ep-patient-cloud-123456.ap-southeast-1.aws.neon.tech`
   - Database: `neondb`
   - User: `flowdesk_owner` (or generated user)
   - Password: `<generated-password>`
5. Your JDBC URL is:
   `jdbc:postgresql://ep-patient-cloud-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require`

#### Choice 1B: Render Managed PostgreSQL (Requires Credit Card on Render)
1. In the Render Dashboard, click **New + > PostgreSQL**.
2. **Name:** `flowdesk-db-demo`, **Database:** `flowdesk`, **User:** `flowdesk_user`, **Plan:** **Free**.
3. Once Available, copy the **Internal Database URL** and format as:
   `jdbc:postgresql://<internal-host>:5432/flowdesk?sslmode=require`

---

### Step 2: Deploy the Spring Boot Backend (Web Service)
1. In the Render Dashboard, click **New + > Web Service**.
2. Connect your GitHub repository: `Vibhushan-2003/flowdesk`.
3. Configure service details:
   - **Name:** `flowdesk-api-demo`
   - **Region:** Same region as your database in Step 1.
   - **Branch:** `deploy/render-free-demo` (or `main` after PR merge)
   - **Root Directory:** *(Leave blank for root)*
   - **Runtime:** **Docker**
   - **Dockerfile Path:** `./backend/Dockerfile` *(Note: if you set Root Directory to `backend`, use `Dockerfile`)*
   - **Docker Context:** `./backend` *(Note: if you set Root Directory to `backend`, use `.`)*
   - **Instance Type:** **Free** (0.1 CPU, 512 MB RAM)
4. Scroll to **Advanced Settings**:
   - **Health Check Path:** `/api/health`
   - **Auto-Deploy:** `Yes`
5. Add Environment Variables:
   - `SPRING_PROFILES_ACTIVE`: `render`
   - `DB_URL`: `jdbc:postgresql://<host>/<dbname>?sslmode=require` (from Step 1)
   - `DB_USERNAME`: `<db-user>` (from Step 1)
   - `DB_PASSWORD`: `<db-password>` (from Step 1)
   - `JWT_SECRET`: `<generated-32-byte-base64-secret>`
   - `FRONTEND_ORIGIN`: `https://flowdesk-demo.onrender.com` (note this name for Step 3)
   - `JAVA_TOOL_OPTIONS`: `-XX:+UseSerialGC -Xmx224m -Xms96m -XX:MaxMetaspaceSize=128m -Xss512k -XX:+UseStringDeduplication`
   - `FLOWDESK_BOOTSTRAP_ADMIN_ENABLED`: `true` (for initial boot)
   - `FLOWDESK_BOOTSTRAP_ADMIN_EMAIL`: `admin@flowdesk.demo`
   - `FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD`: `<generated-admin-password>`
   - `FLOWDESK_MAIL_ENABLED`: `false`
   - `GOOGLE_OAUTH_ENABLED`: `false`
6. Click **Create Web Service**.
7. Monitor the deployment log:
   - Render builds the Docker image and launches Tomcat.
   - Flyway executes migrations V1 through V25.
   - `AdminBootstrapRunner` checks `hasExistingUsers()`, provisions `admin@flowdesk.demo`, scrubs credentials from memory in `finally`, and does NOT terminate the container.
   - Health check `/api/health` succeeds.
8. Copy the Web Service public URL (e.g. `https://flowdesk-api-demo.onrender.com`).
9. In Render Web Service **Environment**, toggle `FLOWDESK_BOOTSTRAP_ADMIN_ENABLED` to `false` and click **Save Changes**.

---

### Step 3: Deploy the React Frontend (Static Site)
1. In the Render Dashboard, click **New + > Static Site**.
2. Connect your GitHub repository: `Vibhushan-2003/flowdesk`.
3. Configure site details:
   - **Name:** `flowdesk-demo` (must match the hostname used in `FRONTEND_ORIGIN` in Step 2)
   - **Branch:** `deploy/render-free-demo` (or `main` after PR merge)
   - **Root Directory:** `frontend`
   - **Build Command:** `npm install && npm run build`
   - **Publish Directory:** `dist`
4. Add Environment Variable:
   - `VITE_API_BASE_URL`: `https://flowdesk-api-demo.onrender.com` (from Step 2)
5. Scroll to **Redirects / Rewrites**:
   - Click **Add Rule**.
   - **Source:** `/*`
   - **Destination:** `/index.html`
   - **Action:** **Rewrite** (Crucial for client-side routing so reloading `/tickets`, `/kb`, or `/login` returns 200).
6. Click **Create Static Site**.
7. Wait ~1 minute for Vite build and CDN publishing to complete.

---

### Step 4: Verify Deployment and Features
1. Open the Static Site URL in your browser: `https://flowdesk-demo.onrender.com`.
2. Verify redirect to the `/login` page.
3. Log in with the bootstrapped admin credentials:
   - Email: `admin@flowdesk.demo`
   - Password: `<your-bootstrap-password>`
4. Verify successful authentication, redirection to Admin Dashboard, and receipt of the `flowdesk_refresh_token` cookie with `SameSite=None; Secure`.
5. Test ticket creation, assignments, status transitions, SLA dashboard, and Knowledge Base articles.
6. Verify that trying to attach a file cleanly informs the user that attachments are disabled in the demo environment.
