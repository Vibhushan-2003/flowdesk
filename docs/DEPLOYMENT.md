# FlowDesk V1 — Docker Deployment & Operations Guide

This guide describes how to run FlowDesk as a fully containerized, reproducible, single-instance deployment using Docker Compose.

---

## 1. Architecture Overview

The containerized topology runs four dedicated services inside an isolated bridge network (`flowdesk_network`):

| Service | Container Name | Image / Base | Internal Port | Host Port | Purpose |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Frontend** | `flowdesk-frontend` | Node 22 build → Nginx Alpine | `80` | `3000` | Serves React SPA, reverse-proxies `/api` and `/ws`, enforces security headers |
| **Backend** | `flowdesk-backend` | Maven 3.9 JDK 21 build → Temurin JRE 21 Alpine | `8080` | *(Internal)* | Spring Boot REST API & WebSocket service (runs unprivileged as `flowdesk:10001`) |
| **PostgreSQL** | `flowdesk-postgres` | `postgres:16-alpine` | `5432` | *(Internal)* | Primary relational store for users, tickets, comments, KB articles, audit logs |
| **Mailpit** | `flowdesk-compose-mailpit` | `axllent/mailpit:latest` | `1025` (SMTP) / `8025` (Web) | `8026` (Web) | Mock SMTP server and web inbox for testing password resets & user invitations |

```
[ Browser Client ]
        │
        ├─ HTTP (Port 3000) ──► [ flowdesk-frontend (Nginx) ]
        │                             │
        │                             ├─ /api/* ──► [ flowdesk-backend (Spring Boot:8080) ]
        │                             └─ /ws    ──►           │
        │                                                     ├─ JDBC ──► [ flowdesk-postgres (5432) ]
        │                                                     ├─ SMTP ──► [ flowdesk-compose-mailpit (1025) ]
        │                                                     └─ Disk ──► [ volume: flowdesk_attachments ]
        │
        └─ HTTP (Port 8026) ──► [ flowdesk-compose-mailpit (Web UI) ]
```

---

## 2. Critical Isolation & Safety Guarantees

1. **Host Database Isolation**:
   - The PostgreSQL container is **not** bound to host port `5432` (avoiding any collision with local Windows PostgreSQL instances).
   - Container database data is strictly stored in the named Docker volume `flowdesk_db_data`.
   - Host PostgreSQL data directories and host database files are **never** mounted.
2. **File Storage Isolation**:
   - Ticket attachment uploads are stored in the named Docker volume `flowdesk_attachments`.
   - Local development folders (`data/attachments`) are **not** mounted.
3. **Host Port Harmony & Least Privilege**:
   - Frontend is mapped to host port `3000` (safe from Vite dev server on `5173`).
   - Backend API port `8080` remains **purely internal** to `flowdesk_network`; all external API and WebSocket traffic routes through Nginx.
   - Mailpit UI is mapped to host port `8026` (safe from standard port `8025`).
4. **Environment File Separation**:
   - Docker Compose operations use a dedicated `.env.docker` file loaded via `--env-file .env.docker`.
   - The host development `.env` is **never** overwritten or loaded by Docker Compose.

---

## 3. Prerequisites

- **Docker Engine** 24.0+ and **Docker Compose** v2.20+
- At least 4 GB of available system memory
- Free host ports: `3000` (Frontend) and `8026` (Mailpit Web UI)

---

## 4. First-Time Setup & Secret Generation

### Step 1: Create your dedicated Docker environment file
Copy `.env.docker.example` to `.env.docker`:
```bash
cp .env.docker.example .env.docker
```

### Step 2: Generate strong secrets
FlowDesk requires a high-entropy 256-bit cryptographic secret for signing HMAC-SHA256 JWT tokens.

- **On Linux / macOS / Git Bash**:
  ```bash
  openssl rand -base64 32
  ```
- **On Windows PowerShell**:
  ```powershell
  [Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 }))
  ```

Update `.env.docker` with:
- A strong database password: `DB_PASSWORD`
- The generated JWT secret: `JWT_SECRET`

---

## 5. Lifecycle Management

### Build and Start (Detached mode)
```bash
docker compose --env-file .env.docker up --build -d
```
Docker Compose builds the backend and frontend multi-stage images, initializes the network and volumes, and starts services in dependency order:
1. `postgres` (healthchecked via `pg_isready`)
2. `mailpit` (starts mock SMTP server)
3. `backend` (waits for `postgres` health, executes Flyway migrations, starts Spring Boot, healthchecked via `/api/health`)
4. `frontend` (waits for `backend` health, starts Nginx reverse proxy, healthchecked via `/healthz`)

### Monitor Logs
- **All services**:
  ```bash
  docker compose --env-file .env.docker logs -f
  ```
- **Backend only**:
  ```bash
  docker compose --env-file .env.docker logs -f backend
  ```
- **Frontend only**:
  ```bash
  docker compose --env-file .env.docker logs -f frontend
  ```
- **Database only**:
  ```bash
  docker compose --env-file .env.docker logs -f postgres
  ```

### Stop Services Safely (Preserving All Data)
To stop the application without losing any database records or uploaded files:
```bash
docker compose --env-file .env.docker stop
```
or:
```bash
docker compose --env-file .env.docker down
```
> [!IMPORTANT]
> Running `docker compose down` stops and removes the containers and networks, but **preserves all named volumes** (`flowdesk_db_data` and `flowdesk_attachments`). Your data remains 100% intact.

---

## 6. Initial User Bootstrapping

When starting with a fresh, empty container database, no user accounts exist. To create the initial administrator securely without hardcoding credentials or copying development records:

```bash
docker compose --env-file .env.docker run --rm \
  -e FLOWDESK_BOOTSTRAP_ADMIN_ENABLED=true \
  -e FLOWDESK_BOOTSTRAP_ADMIN_EMAIL=admin@flowdesk.test \
  -e FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD=YourSecureAdminPassword123! \
  backend
```

This command runs FlowDesk's built-in `AdminBootstrapRunner` in non-web mode (`WebApplicationType.NONE`), creates the single `ADMIN` account atomically in PostgreSQL, emits an immutable audit event, and terminates immediately with exit code 0.

Once provisioned:
1. Log in at `http://localhost:3000/login` with your administrator credentials.
2. Navigate to **User Management** (`/admin/users`) to invite additional employees and support engineers.
3. Outbound invitation emails will be captured in the Mailpit inbox at `http://localhost:8026`.

---

## 7. Accessing Services & Verifying Health

| Interface | URL | Health Verification Endpoint |
| :--- | :--- | :--- |
| **FlowDesk Web Application** | `http://localhost:3000` | `http://localhost:3000/healthz` (HTTP 200: "OK") |
| **REST API (via Nginx)** | `http://localhost:3000/api` | `http://localhost:3000/api/health` (HTTP 200: "Backend is running!") |
| **WebSocket STOMP** | `ws://localhost:3000/ws` | Protocol handshake accepted |
| **Mailpit Web Inbox** | `http://localhost:8026` | `http://localhost:8026/api/v1/info` |

### Health Check Inspection
Inspect container health states at any time:
```bash
docker compose --env-file .env.docker ps
```
All four services should report `healthy` in the status column.

---

## 8. Persistent Storage, Backups & Recovery

### Volume Locations
Data persistence is managed via named Docker volumes:
- `flowdesk_db_data`: PostgreSQL database files (`/var/lib/postgresql/data`).
- `flowdesk_attachments`: Uploaded ticket attachments (`/app/data/attachments`).

### Backing Up Data

#### 1. Database SQL Dump (Logical Backup)
Run `pg_dump` directly inside the running database container:
```bash
docker compose --env-file .env.docker exec -T postgres pg_dump -U flowdesk_app flowdesk > flowdesk_backup_$(date +%Y%m%d_%H%M%S).sql
```

#### 2. Attachment Archive Backup
Create a compressed archive of all uploaded files:
```bash
docker run --rm -v flowdesk_attachments:/data -v $(pwd):/backup alpine tar czf /backup/flowdesk_attachments_backup_$(date +%Y%m%d_%H%M%S).tar.gz -C /data .
```

### Restoring Data

#### 1. Database Restore
```bash
cat backup.sql | docker compose --env-file .env.docker exec -T postgres psql -U flowdesk_app -d flowdesk
```

#### 2. Attachment Restore
```bash
docker run --rm -v flowdesk_attachments:/data -v $(pwd):/backup alpine tar xzf /backup/flowdesk_attachments_backup.tar.gz -C /data
```

---

## 9. Destructive Commands Warning

> [!CAUTION]
> **NEVER casually run `docker compose down -v` or `docker volume rm`!**
>
> Adding the `-v` / `--volumes` flag to `docker compose down` will **permanently delete the named Docker volumes**, causing complete, unrecoverable data loss of:
> - All PostgreSQL database tables, user accounts, and ticket histories (`flowdesk_db_data`).
> - All uploaded ticket attachment files (`flowdesk_attachments`).
>
> To safely stop or tear down containers without destroying data, always use:
> ```bash
> docker compose --env-file .env.docker down
> ```
> *(without the `-v` flag)*

---

## 10. Local Demo vs. Internet-Facing Production Review

FlowDesk V1's Docker Compose setup is designed for **reproducible local evaluation, staging, and demo environments**.

For internet-facing production deployments, the following additional controls are recommended:
1. **TLS / HTTPS Termination**: Place a reverse proxy (e.g., Cloudflare, Traefik, or AWS ALB) in front of the application with valid SSL/TLS certificates.
2. **Encrypted Outbound Mail**: Replace Mailpit with an authenticated external SMTP relay (e.g., Amazon SES, SendGrid, or Google Workspace) using STARTTLS or SSL/TLS.
3. **Activate `prod` Profile**: Once TLS is established, set `SPRING_PROFILES_ACTIVE=prod` to enforce HTTPS origin checks and strict production constraints.
4. **Secret Management**: Inject `JWT_SECRET` and `DB_PASSWORD` via Docker Swarm Secrets, Kubernetes Secrets, or HashiCorp Vault rather than a plain file.
