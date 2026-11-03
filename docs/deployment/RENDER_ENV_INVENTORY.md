# FlowDesk — Render Free Deployment Environment Variable Inventory

**Target Environment:** Render Free Tier (Temporary Public Demo)  
**Security Posture:** Zero Secret Leakage, Minimal Footprint, Fail-Closed  
**Spring Boot Profile:** `render`  
**Frontend Type:** Render Static Site  
**Backend Type:** Render Docker Web Service  
**Database Type:** Render PostgreSQL Free Instance  

---

## 1. Overview & Constraint Analysis

Render Free provides:
- **Web Service:** 0.1 CPU, 512 MB RAM, ephemeral filesystem, dynamic `$PORT` binding, automatic TLS termination, 15-minute inactivity spin-down (cold boot ~50–60s).
- **PostgreSQL:** 1 GB storage, connection limits (max ~50 connections across shared infrastructure), expires after 30 days unless upgraded.
- **Static Site:** Fast CDN global hosting, custom SPA rewrites, zero server compute costs.
- **Network Boundaries:** Outbound SMTP port 25 is blocked. Cross-subdomain requests between `https://<frontend>.onrender.com` and `https://<backend>.onrender.com` are treated as cross-site by modern browsers due to `onrender.com` being registered on the Public Suffix List (PSL).

To prevent OOM kills (137 exit code) inside the 512 MB cgroup limit and satisfy security guarantees:
1. Java heap and metaspace are constrained using `JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xmx224m -Xms96m -XX:MaxMetaspaceSize=128m -Xss512k -XX:+UseStringDeduplication"`.
2. HikariCP connection pool is bounded to `maximum-pool-size=4` and `minimum-idle=1`.
3. Refresh session cookies use `SameSite=None; Secure=true; HttpOnly`.
4. Attachment uploads are explicitly disabled (`app.storage.attachment.upload-enabled=false`) because persistent volumes and ClamAV (>1.2 GB RAM) are not viable on Render Free. ClamAV fail-closed security is 100% maintained with zero mock scanning bypass.
5. Ingress reverse proxy headers (`X-Forwarded-For`, `X-Forwarded-Proto`) are trusted via native Tomcat configuration.
6. Public Actuator Prometheus and metric endpoints are disabled; only `/api/health` is exposed.

---

## 2. Complete Environment Variable Inventory Matrix

| Variable Name | Component | Classification | Default in Render Profile | Render Value / Recommended Value | Description & Security Notes |
|---|---|---|---|---|---|
| `VITE_API_BASE_URL` | Frontend Build | **Required** | *(None)* | `https://<your-backend-service>.onrender.com` | Public HTTPS origin of the backend web service. Baked into frontend static bundle at build time. Automatically resolved for REST and WSS (`wss://.../ws`). |
| `SPRING_PROFILES_ACTIVE` | Backend Runtime | **Required** | `render` | `render` | Activates `application-render.properties`. |
| `PORT` | Backend Runtime | **Provided by Render** | `8080` | *(Injected dynamically by Render, e.g. 10000)* | Port on which the Spring Boot Tomcat container must bind. `server.port=${PORT:8080}` maps this automatically. |
| `DB_URL` | Backend Runtime | **Required** | *(None)* | `jdbc:postgresql://<render-db-host>:5432/<render-db-name>?sslmode=require` | JDBC connection URL. Use the Internal Database URL provided by Render PostgreSQL for lower latency. |
| `DB_USERNAME` (or `DB_USER`) | Backend Runtime | **Required** | *(None)* | `<render-db-user>` | PostgreSQL database user provisioned by Render. |
| `DB_PASSWORD` | Backend Runtime | **Required** | *(None)* | `<render-db-password>` | PostgreSQL database password provisioned by Render. |
| `JWT_SECRET` | Backend Runtime | **Required** | *(None)* | *(Fresh 256-bit Base64 secret)* | Cryptographic HMAC-SHA256 signing key for access tokens. Must be at least 32 bytes (44 Base64 chars). |
| `FRONTEND_ORIGIN` | Backend Runtime | **Required** | *(None)* | `https://<your-static-site>.onrender.com` | Allowed CORS origin. Also whitelisted for password recovery and invitation links. Multiple comma-separated origins are supported if needed. |
| `JAVA_TOOL_OPTIONS` | Backend Runtime | **Required** | *(None)* | `-XX:+UseSerialGC -Xmx224m -Xms96m -XX:MaxMetaspaceSize=128m -Xss512k -XX:+UseStringDeduplication` | Essential JVM ergonomics tuning. Prevents JVM from allocating default 75% RAM and exceeding 512 MB cgroup. |
| `FLOWDESK_BOOTSTRAP_ADMIN_ENABLED` | Backend Runtime | **Optional / One-Shot** | `false` | `true` (for initial boot, then `false`) | When set to `true` with credentials below, creates the first ADMIN account and exits or starts. Toggle back to `false` after first boot. |
| `FLOWDESK_BOOTSTRAP_ADMIN_EMAIL` | Backend Runtime | **Conditional** | *(Empty)* | `admin@flowdesk.demo` | Email for the initial administrator account. Required if bootstrap enabled. |
| `FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD` | Backend Runtime | **Conditional** | *(Empty)* | *(Fresh strong password, min 12 chars)* | Password for the initial administrator account. Required if bootstrap enabled. |
| `FLOWDESK_BOOTSTRAP_ADMIN_FIRST_NAME` | Backend Runtime | **Optional** | `Demo` | `System` | First name for the initial administrator. |
| `FLOWDESK_BOOTSTRAP_ADMIN_LAST_NAME` | Backend Runtime | **Optional** | `Admin` | `Administrator` | Last name for the initial administrator. |
| `REFRESH_TOKEN_COOKIE_SAMESITE` | Backend Runtime | **Optional** | `None` | `None` | SameSite attribute for the refresh token cookie. Required to be `None` for cross-site static frontend requests. |
| `REFRESH_TOKEN_COOKIE_SECURE` | Backend Runtime | **Optional** | `true` | `true` | Enforces `Secure` flag on refresh token cookie. Required when `SameSite=None`. |
| `DB_POOL_MAX_SIZE` | Backend Runtime | **Optional** | `4` | `4` | Maximum HikariCP pool connections. Keep <= 5 on Render Free. |
| `DB_POOL_MIN_IDLE` | Backend Runtime | **Optional** | `1` | `1` | Minimum HikariCP idle connections. |
| `FLOWDESK_MAIL_ENABLED` | Backend Runtime | **Optional / Safe to Disable** | `false` | `false` (or `true` if external SMTP configured) | Enables outbound emails (invitations, password resets). Set `false` for demo to run safely dormant. |
| `SMTP_HOST` | Backend Runtime | **Conditional** | `localhost` | `smtp.sendgrid.net` / `smtp.resend.com` | External SMTP server hostname. Port 25 is blocked on Render Free; use port 587 with STARTTLS. |
| `SMTP_PORT` | Backend Runtime | **Conditional** | `587` | `587` | External SMTP submission port. |
| `SMTP_USERNAME` | Backend Runtime | **Conditional** | *(Empty)* | `<smtp-api-username>` | SMTP authenticated username. |
| `SMTP_PASSWORD` | Backend Runtime | **Conditional** | *(Empty)* | `<smtp-api-key-or-password>` | SMTP authenticated password / API key. |
| `SMTP_AUTH` | Backend Runtime | **Conditional** | `true` | `true` | Enforces SMTP authentication. |
| `SMTP_STARTTLS` | Backend Runtime | **Conditional** | `true` | `true` | Enforces STARTTLS encryption over port 587. |
| `FLOWDESK_MAIL_FROM_ADDRESS` | Backend Runtime | **Conditional** | `noreply@flowdesk.demo` | `noreply@yourdomain.com` | Verified outbound sender email address. |
| `FLOWDESK_MAIL_FROM_NAME` | Backend Runtime | **Optional** | `FlowDesk Demo` | `FlowDesk Demo` | Display name for outbound emails. |
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
1. **Disable Email Subsystem:** Set `FLOWDESK_MAIL_ENABLED=false`. All user management, roles, ticket workflows, SLA monitoring, audit logs, and knowledge base work natively without requiring email delivery.
2. **Disable Google OAuth:** Set `GOOGLE_OAUTH_ENABLED=false`. Standard secure username/password login and JWT refresh session rotation work natively.
3. **Disable Gemini AI:** Leave `GEMINI_API_KEY=` empty. AI triage and reply suggestions will gracefully report unavailable; core ticketing and manual workflows are 100% operational.
4. **Attachment Uploads Blocked:** Uploads are disabled by default in the `render` profile (`app.storage.attachment.upload-enabled=false`) returning HTTP 503 Service Unavailable with an explanation. Existing attachments (if any) can still be inspected, but no unverified uploads occur on ephemeral disks.

---

## 5. External Provider Setup (When Enabling Integrations)

### Google OAuth 2.0 (Optional)
If you wish to demo Google Single Sign-On:
1. In the [Google Cloud Console](https://console.cloud.google.com/), navigate to **APIs & Services > Credentials**.
2. Create an **OAuth 2.0 Client ID** of type **Web application**.
3. Under **Authorized JavaScript origins**, add:
   `https://<your-static-site>.onrender.com`
4. Under **Authorized redirect URIs**, add:
   `https://<your-static-site>.onrender.com/auth/google/callback`
5. Copy the Client ID and Client Secret into `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.
6. Set `GOOGLE_OAUTH_ENABLED=true` and `GOOGLE_REDIRECT_URI=https://<your-static-site>.onrender.com/auth/google/callback`.

### Outbound SMTP Email (Optional)
If you wish to demo invitation emails and password recovery:
1. Register for a free transactional email account (e.g., SendGrid free 100 emails/day, Resend, or Brevo).
2. Create an API key with Mail Send permissions.
3. In Render Web Service environment variables:
   - `FLOWDESK_MAIL_ENABLED=true`
   - `SMTP_HOST=smtp.sendgrid.net` (or provider host)
   - `SMTP_PORT=587`
   - `SMTP_USERNAME=apikey` (for SendGrid)
   - `SMTP_PASSWORD=<your-api-key>`
   - `SMTP_STARTTLS=true`
   - `FLOWDESK_MAIL_FROM_ADDRESS=<your-verified-sender-email>`

### Gemini AI Studio (Optional)
If you wish to demo AI Copilot and triage suggestions:
1. Create a free API key at [Google AI Studio](https://aistudio.google.com/).
2. In Render Web Service environment variables:
   - `GEMINI_API_KEY=<your-gemini-api-key>`
   - `GEMINI_MODEL=gemini-3.1-flash-lite`

---

## 6. Safe Configuration Placeholder Templates

### A. Render Web Service (Backend Environment Variables)
```env
SPRING_PROFILES_ACTIVE=render
DB_URL=jdbc:postgresql://<render-db-internal-host>:5432/<render-db-name>?sslmode=require
DB_USERNAME=<render-db-user>
DB_PASSWORD=<render-db-password>
JWT_SECRET=<generated-base64-32-byte-secret>
FRONTEND_ORIGIN=https://<your-frontend-site>.onrender.com
JAVA_TOOL_OPTIONS=-XX:+UseSerialGC -Xmx224m -Xms96m -XX:MaxMetaspaceSize=128m -Xss512k -XX:+UseStringDeduplication
FLOWDESK_BOOTSTRAP_ADMIN_ENABLED=false
FLOWDESK_MAIL_ENABLED=false
GOOGLE_OAUTH_ENABLED=false
GEMINI_API_KEY=
```

### B. Render Static Site (Frontend Environment Variables)
```env
VITE_API_BASE_URL=https://<your-backend-service>.onrender.com
```

---

## 7. Chronological Render Dashboard Deployment Steps

Follow these steps in exact order to deploy FlowDesk to Render Free:

### Step 1: Create the Render PostgreSQL Database
1. In the Render Dashboard, click **New + > PostgreSQL**.
2. **Name:** `flowdesk-db-demo`
3. **Database:** `flowdesk`
4. **User:** `flowdesk_user`
5. **Region:** Choose the region closest to you (e.g., Singapore `singapore`, Oregon `us-west`, or Frankfurt `frankfurt`).
6. **Plan:** Select **Free**.
7. Click **Create Database**.
8. Wait for the database status to become **Available**.
9. In the **Connections** section, locate the **Internal Database URL** (e.g., `postgres://flowdesk_user:xxxx@dpg-xxxx-a:5432/flowdesk`).
   Convert this into JDBC format:
   `jdbc:postgresql://<host>:5432/flowdesk?sslmode=require`

### Step 2: Deploy the Spring Boot Backend (Web Service)
1. In the Render Dashboard, click **New + > Web Service**.
2. Connect your GitHub repository: `Vibhushan-2003/flowdesk`.
3. Configure service details:
   - **Name:** `flowdesk-api-demo`
   - **Region:** Same region as your database in Step 1.
   - **Branch:** `deploy/render-free-demo` (or `main` after PR merge)
   - **Root Directory:** *(Leave blank for root)*
   - **Runtime:** **Docker**
   - **Dockerfile Path:** `./backend/Dockerfile`
   - **Docker Context:** `./backend`
   - **Instance Type:** **Free** (0.1 CPU, 512 MB RAM)
4. Scroll to **Advanced Settings**:
   - **Health Check Path:** `/api/health`
   - **Auto-Deploy:** `Yes`
5. Add Environment Variables:
   - `SPRING_PROFILES_ACTIVE`: `render`
   - `DB_URL`: `jdbc:postgresql://<host>:5432/flowdesk?sslmode=require` (from Step 1)
   - `DB_USERNAME`: `<db-user>` (from Step 1)
   - `DB_PASSWORD`: `<db-password>` (from Step 1)
   - `JWT_SECRET`: `<generated-32-byte-base64-secret>`
   - `FRONTEND_ORIGIN`: `https://flowdesk-demo.onrender.com` (note this name for Step 3)
   - `JAVA_TOOL_OPTIONS`: `-XX:+UseSerialGC -Xmx224m -Xms96m -XX:MaxMetaspaceSize=128m -Xss512k -XX:+UseStringDeduplication`
   - `FLOWDESK_BOOTSTRAP_ADMIN_ENABLED`: `true` (for initial boot only)
   - `FLOWDESK_BOOTSTRAP_ADMIN_EMAIL`: `admin@flowdesk.demo`
   - `FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD`: `<generated-admin-password>`
   - `FLOWDESK_MAIL_ENABLED`: `false`
   - `GOOGLE_OAUTH_ENABLED`: `false`
6. Click **Create Web Service**.
7. Monitor the deployment log. Render will execute Docker multi-stage build, start the container, run Flyway migrations 1 through 25, seed the initial administrator, and pass the `/api/health` probe.
8. Copy the generated Web Service public URL (e.g. `https://flowdesk-api-demo.onrender.com`).
9. In Render Web Service **Environment**, toggle `FLOWDESK_BOOTSTRAP_ADMIN_ENABLED` to `false` and click **Save Changes**.

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
   - **Action:** **Rewrite** (Crucial for React client-side routing so refreshing `/tickets`, `/kb`, or `/login` does not return 404).
6. Click **Create Static Site**.
7. Wait ~1 minute for Vite build and CDN publishing to complete.

### Step 4: Verify Deployment and Features
1. Open the Static Site URL in your browser: `https://flowdesk-demo.onrender.com`.
2. Verify redirect to the `/login` page.
3. Log in with the bootstrapped admin credentials:
   - Email: `admin@flowdesk.demo`
   - Password: `<your-bootstrap-password>`
4. Verify successful authentication, redirection to Admin Dashboard, and receipt of the `flowdesk_refresh_token` cookie with `SameSite=None; Secure`.
5. Test ticket creation, assignments, status transitions, SLA dashboard, and Knowledge Base articles.
6. Verify that trying to attach a file cleanly informs the user that attachments are disabled in the demo environment.
