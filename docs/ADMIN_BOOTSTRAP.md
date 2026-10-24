# FlowDesk First-ADMIN Bootstrap & Initial Provisioning

## Overview

FlowDesk uses an explicit, one-time initial provisioning mechanism to create the very first administrator account on a fresh database.

Flyway migration `V1__create_user_role_schema.sql` seeds the system roles (`EMPLOYEE`, `SUPPORT_ENGINEER`, `TEAM_LEAD`, `ADMIN`), but purposefully inserts zero user accounts. All user creation endpoints (`POST /api/admin/users` and `POST /api/users`) require an active administrator with `ROLE_ADMIN`. The bootstrap mechanism solves this initial chicken-and-egg constraint securely.

---

## Security Architecture

1. **Explicit Operator Invocation Only:**
   Bootstrap is **disabled by default**. Ordinary application startup (`.\mvnw spring-boot:run` or `java -jar backend.jar`) ignores bootstrap completely.
2. **Zero HTTP/WebSocket Exposure (Non-Web Process):**
   When bootstrap is explicitly requested (via CLI option `--flowdesk.bootstrap.admin` or environment variable `FLOWDESK_BOOTSTRAP_ADMIN_ENABLED=true`), `BackendApplication` configures `WebApplicationType.NONE` before launching the Spring context. No embedded Tomcat server, HTTP port, or WebSocket listener is ever initialized during provisioning.
3. **Guaranteed Process Termination:**
   Every bootstrap invocation terminates immediately after execution:
   - On success: logs confirmation and terminates with **exit code 0**.
   - On failure (validation error, existing users, missing roles): logs the safe failure reason and terminates with **exit code 1**.
   No long-running background process is left running with bootstrap credentials in its environment.
4. **Zero Credential Defaults & No Secrets in Process Arguments:**
   There are no hardcoded usernames or passwords in code, properties, or migrations. Credentials must be provided via environment variables (`FLOWDESK_BOOTSTRAP_ADMIN_EMAIL`, `FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD`), avoiding exposure in shell history or process listings (`ps`).
5. **Strict Initial-State Guard:**
   Bootstrap executes only when the database `users` table contains **0 users**. If any user exists (whether active, inactive, employee or administrator), the operation is refused and aborts immediately without modifying any records.
6. **Pessimistic Row Locking Concurrency Protection:**
   The provisioning transaction acquires a `PESSIMISTIC_WRITE` lock on the database `ADMIN` role row. In clustered or multi-container startup environments, concurrent attempts are strictly serialized at the PostgreSQL transaction level, guaranteeing at most one administrator is ever created.
7. **Atomic Audit Logging:**
   The user creation, `ADMIN` role assignment, and a `SYSTEM` `USER_CREATED` immutable audit record are written within a single database transaction. If the audit fails, the user creation is rolled back.

---

## Configuration Properties

The following properties configure the bootstrap runner in `backend/src/main/resources/application.properties`:

| Property Key | Environment Variable Equivalent | Default Value | Description |
| :--- | :--- | :---: | :--- |
| `app.bootstrap.admin.enabled` | `FLOWDESK_BOOTSTRAP_ADMIN_ENABLED` | `false` | Must be set to `true` to enable bootstrap execution. |
| `app.bootstrap.admin.email` | `FLOWDESK_BOOTSTRAP_ADMIN_EMAIL` | *(none)* | Administrator email. Must be a valid email format. |
| `app.bootstrap.admin.password` | `FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD` | *(none)* | Administrator password (8 to 72 characters). |
| `app.bootstrap.admin.first-name` | `FLOWDESK_BOOTSTRAP_ADMIN_FIRST_NAME` | `System` | Optional first name. |
| `app.bootstrap.admin.last-name` | `FLOWDESK_BOOTSTRAP_ADMIN_LAST_NAME` | `Administrator` | Optional last name. |

---

## Operating Procedures

### 1. Initial Local Provisioning

To provision the first administrator on a brand new local database:

```powershell
# Set temporary credentials in your terminal session
$env:FLOWDESK_BOOTSTRAP_ADMIN_ENABLED="true"
$env:FLOWDESK_BOOTSTRAP_ADMIN_EMAIL="admin@flowdesk.test"
$env:FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD="<YourSecurePasswordHere>"

# Run bootstrap command (runs in non-web mode and terminates immediately)
cd backend
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--flowdesk.bootstrap.admin"

# Remove credentials from your terminal environment immediately
$env:FLOWDESK_BOOTSTRAP_ADMIN_ENABLED=""
$env:FLOWDESK_BOOTSTRAP_ADMIN_EMAIL=""
$env:FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD=""
```

The process logs confirmation and exits with code 0:
```
INFO c.flowdesk.BackendApplication       : Starting FlowDesk in non-web bootstrap mode (WebApplicationType.NONE).
INFO c.f.u.b.AdminBootstrapRunner        : Explicit FlowDesk initial administrator bootstrap requested.
INFO c.f.u.b.AdminBootstrapService       : Successfully provisioned initial administrator with email: admin@flowdesk.test
INFO c.f.u.b.AdminBootstrapRunner        : Administrator provisioning completed successfully.
INFO c.f.u.b.AdminBootstrapRunner        : Terminating application process after successful bootstrap (exit code 0).
```

### 2. Production Container Provisioning

In Docker or Kubernetes, run bootstrap as an ephemeral init-container or one-off task with secrets mounted as environment variables:

```bash
docker run --rm \
  --network flowdesk-net \
  --env-file /secure/path/bootstrap.env \
  flowdesk-backend:latest \
  --flowdesk.bootstrap.admin
```

Where `/secure/path/bootstrap.env` contains:
```env
DB_URL=jdbc:postgresql://db:5432/flowdesk
DB_USERNAME=flowdesk_app
DB_PASSWORD=<YourDatabasePassword>
JWT_SECRET=<YourJwtSecret>
FLOWDESK_BOOTSTRAP_ADMIN_ENABLED=true
FLOWDESK_BOOTSTRAP_ADMIN_EMAIL=admin.prod@company.com
FLOWDESK_BOOTSTRAP_ADMIN_PASSWORD=<YourStrongAdminPassword>
```

Once the container exits 0, start your standard long-running backend containers **without** the bootstrap environment variables.

### 3. Verification

Verify the new administrator by starting the web backend normally and logging in:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@flowdesk.test","password":"<YourSecurePasswordHere>"}'
```

A successful response returns an HTTP 200 with JWT access token and user claims showing `ROLE_ADMIN`.

---

## Emergency Recovery (Existing Users But Lost Administrator)

If all administrators are lost or deactivated in an environment that already contains users, the bootstrap runner will deliberately refuse to run (`count() > 0`) to prevent accidental account overwrite or elevation.

### Authorized Operator Recovery Procedure:
1. Connect to PostgreSQL using an authorized database administrator tool (`psql`).
2. Verify existing administrator records:
   ```sql
   SELECT u.id, u.email, u.status, r.code
   FROM users u
   JOIN user_roles ur ON u.id = ur.user_id
   JOIN roles r ON ur.role_id = r.id
   WHERE r.code = 'ADMIN';
   ```
3. If an existing administrator was set to `INACTIVE`, reactivate them:
   ```sql
   UPDATE users
   SET status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
   WHERE email = 'admin@example.com';
   ```
4. If an existing active user needs to be promoted to administrator:
   ```sql
   INSERT INTO user_roles (user_id, role_id)
   VALUES (
       (SELECT id FROM users WHERE email = 'trusted.employee@example.com'),
       '00000000-0000-0000-0000-000000000004'
   ) ON CONFLICT DO NOTHING;
   ```
5. Record the administrative recovery action in your team's change management log.
