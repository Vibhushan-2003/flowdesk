# FlowDesk Day 65 — Production Security Hardening & Configuration Report

**Report Date:** November 3, 2026  
**Repository:** `C:\Users\gvibh\Projects\flowdesk`  
**Active Working Branch:** `feat/day65-production-security-hardening`  
**Base Commit:** `b44e962` (`main` containing Day 64 PR #57 and Flyway V25)  
**Status:** Complete — Security Hardened, Fully Tested, Ready for Cloud Deployment Architecture (Day 66)

---

## 1. Git Baseline and Branch

- **Repository Root:** `C:\Users\gvibh\Projects\flowdesk`
- **Active Working Branch:** `feat/day65-production-security-hardening`
- **Upstream Tracking / Base:** `origin/main` at commit `b44e962` (*Merge pull request #57 from Vibhushan-2003/feat/day63-flowdesk-horizon-ui*)
- **Flyway Database Version:** Flyway V25 (`V25__add_ticket_comment_visibility.sql`)
- **Workspace Hygiene:** Local maintenance scripts (`repair_postgres.bat`, `db_test_setup.bat`) and scratch directories preserved intact. No uncommitted work overwritten.

---

## 2. Corrected Audit Assumptions

Following operational review during the Day 65 SRE Audit, `DAY65_SRE_DEPLOYMENT_READINESS_AUDIT.md` was amended with Section 1b to correct several foundational operational assumptions:

1. **OCI Always Free A1 Compute Quotas:**
   - *Previous Assumption:* Quoted the raw theoretical upper ceiling of 4 OCPU and 24 GB RAM.
   - *Refined Correction:* The actual Oracle Cloud Infrastructure Always Free allowance for Ampere A1 compute is capped at **1,500 OCPU-hours and 9,000 GB-hours per calendar month**. When run continuously 24/7 (~720–744 hours/month), this quota supports a single VM allocation of approximately **2 OCPU and 12 GB RAM**. Exceeding 2 OCPU / 12 GB RAM on a 24/7 basis exhausts monthly allowances and triggers billing.
2. **Memory Footprint Rigor:**
   - Architectural memory estimates (~4.6 GB active RSS across all containers: backend JVM ~1.2 GB, PostgreSQL ~512 MB, ClamAV ~1.5–2.0 GB, Caddy/Nginx ~64 MB) are derived from component baselines.
   - *Refined Correction:* All memory figures are explicitly documented as **unverified estimates** until empirical measurements are gathered under production workloads in Day 66.
3. **Pay-As-You-Go (PAYG) Upgrade Risks:**
   - *Refined Correction:* Upgrading an OCI tenancy to PAYG (to prevent idle instance reclamation) **is NOT a zero-cost guarantee**. While Always Free resources remain $0.00 if strictly bounded, any accidental over-provisioning (e.g. boot volume resize beyond 200 GB, extra block storage volumes, egress exceeding 10 TB/month, or paid API calls) will automatically charge the credit card on file without a hard billing cutoff.
4. **Provider Free-Tier Volatility:**
   - Documented that free-tier quotas across all external services (Oracle Cloud, Google AI Studio Gemini API, Brevo SMTP, and GitHub Container Registry) are subject to terms of service modifications and quota changes. The system architecture is kept strictly portable (standard Docker Compose) to allow rapid migration if terms evolve.
5. **Disaster Recovery (Same-Host vs. Off-Host):**
   - Distinguishes local snapshot backups (`/opt/flowdesk/backups`) from true disaster recovery (DR). Local backups protect against application corruption or bad migrations, but fail completely if the VM terminates or the tenancy is locked. True DR requires an off-host replication pipeline (e.g., encrypted object storage or remote repository push).
6. **Real Deployment Failure Modes:**
   - Single-node architecture inherently implies maintenance downtime during host kernel patches or VM restarts; zero-downtime rolling updates are not supported without a multi-node ingress balancer.

---

## 3. Exact Client-IP Resolution Strategy

### Architectural Problem
FlowDesk enforces per-IP and per-account rate limiting on authentication endpoints (`/api/auth/login`) via `LoginRateLimitingFilter.java`. In a production deployment behind a reverse proxy (e.g., Caddy or Nginx):
1. Direct inspection of `request.getRemoteAddr()` returns the reverse proxy's internal IP (e.g., `127.0.0.1`, `172.18.0.2`), causing all external users to share a single global rate-limit bucket (noisy-neighbor denial of service).
2. Naive inspection of the leftmost `X-Forwarded-For` header allows malicious clients to spoof arbitrary IP addresses by sending `X-Forwarded-For: 8.8.8.8`, entirely bypassing rate limits or impersonating trusted clients.
3. Spring Web's `ForwardedHeaderFilter` (`server.forward-headers-strategy=framework`) parses the leftmost token without verifying whether the direct TCP remote peer is a trusted proxy, creating a header injection vulnerability.

### Implementation Architecture
FlowDesk implements a dual-layer defense-in-depth approach:

#### Layer 1: Servlet Container RemoteIpValve (`application-prod.properties`)
```properties
server.forward-headers-strategy=native
server.tomcat.remoteip.remote-ip-header=X-Forwarded-For
server.tomcat.remoteip.protocol-header=X-Forwarded-Proto
server.tomcat.remoteip.internal-proxies=127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|::1|0:0:0:0:0:0:0:1|10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|172\\.(1[6-9]|2[0-9]|3[0-1])\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3}|169\\.254\\.\\d{1,3}\\.\\d{1,3}|fc00:.*|fd.*
```
In Tomcat's native `RemoteIpValve`, headers are traversed **from right to left**, trusting only intermediate proxies matching `internal-proxies`.

#### Layer 2: Application-Level Right-to-Left Resolver (`ClientIpResolver.java`)
FlowDesk's `ClientIpResolver.java` provides a fail-safe application-level implementation that functions uniformly across both native Tomcat runtime and Spring MockMvc/test harnesses:

```java
public static String resolveClientIp(HttpServletRequest request, boolean trustProxyHeaders) {
    String remoteAddr = normalizeIp(request.getRemoteAddr());

    // In development or when proxy trusting is disabled: strictly use remote socket address
    if (!trustProxyHeaders) {
        return remoteAddr;
    }

    // Direct connection check: if direct remote address is NOT a trusted proxy,
    // forwarded headers MUST be ignored to prevent direct spoofing
    if (!isTrustedProxy(remoteAddr)) {
        return remoteAddr;
    }

    // Traverse X-Forwarded-For from right to left
    String xff = request.getHeader("X-Forwarded-For");
    if (StringUtils.hasText(xff)) {
        String[] parts = xff.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = normalizeIp(parts[i]);
            if (candidate != null && !isTrustedProxy(candidate)) {
                return candidate; // First untrusted IP from the right is the genuine client
            }
        }
    }

    // Fallback to X-Real-IP if present and valid
    String xRealIp = normalizeIp(request.getHeader("X-Real-IP"));
    if (xRealIp != null && !isTrustedProxy(xRealIp)) {
        return xRealIp;
    }

    return remoteAddr;
}
```

#### Layer 3: Filter Integration (`LoginRateLimitingFilter.java`)
`LoginRateLimitingFilter.java` reads the configured property:
```java
String clientIp = ClientIpResolver.resolveClientIp(wrappedRequest, properties.isTrustProxyHeaders());
```
When `app.security.rate-limit.login.trust-proxy-headers=true` (in `application-prod.properties`), proxy headers are evaluated under strict trusted-proxy rules.

---

## 4. Proxy Spoofing Defence

### Threat Scenarios and Mitigations

| Threat | Attack Vector | FlowDesk Mitigation |
| :--- | :--- | :--- |
| **Direct Header Injection** | Attacker connects directly to port 8080 (bypassing reverse proxy) and sends `X-Forwarded-For: 1.2.3.4`. | `ClientIpResolver.isTrustedProxy(remoteAddr)` evaluates the TCP socket address. Because the attacker's public IP is untrusted, all forwarded headers are discarded. |
| **Pre-pended Header Spoofing** | Attacker connects through reverse proxy and sends `X-Forwarded-For: 203.0.113.195`. Proxy appends attacker's IP: `X-Forwarded-For: 203.0.113.195, 198.51.100.24`. | `ClientIpResolver` traverses from right to left. The rightmost IP `198.51.100.24` (the untrusted attacker) is selected; the forged prefix `203.0.113.195` is ignored. |
| **Proxy Chaining Spoofing** | Request traverses legitimate CDN / proxy chain: `Client -> Proxy1 -> Proxy2 -> App`. Header: `ClientIP, Proxy1IP`. | Right-to-left traversal checks `Proxy1IP` (internal/trusted), skips it, and identifies `ClientIP` as the first untrusted IP. |
| **Header Injection / Syntax Attack** | Attacker sends malformed tokens: `X-Forwarded-For: <script>alert(1)</script>, 198.51.100.24`. | `normalizeIp()` validates IPv4 (`inet_aton` format `0-255.0-255.0-255.0-255`) and IPv6 syntax without DNS lookups. Malformed tokens are stripped safely. |
| **Internal IP Collision** | Multiple users behind distinct NAT gateways connect. | The public gateway IP is resolved; rate limits apply per public origin and per targeted user email account independently. |

---

## 5. Production Configuration Changes (`application-prod.properties`)

The production configuration was reviewed and hardened with the following enhancements:

1. **Native Tomcat Reverse Proxy Valve:**
   - Configured `server.forward-headers-strategy=native` with regex covering loopback (`127.0.0.0/8`, `::1`), Docker bridge (`172.16.0.0/12`), private subnets (`10.0.0.0/8`, `192.168.0.0/16`, `169.254.0.0/16`), and IPv6 ULA (`fc00::/7`).
2. **Graceful Shutdown & Lifecycle Timeouts:**
   - Configured `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=30s` to allow active HTTP requests and ShedLock background jobs to complete cleanly before process exit.
3. **Fail-Fast Production Secrets:**
   - Database: `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}` (no default fallbacks).
   - JWT Secret: `${JWT_SECRET}` (mandatory 256-bit Base64 string; startup fails if invalid or missing).
   - Gemini API: `${GEMINI_API_KEY}` (required when AI triage is enabled).
   - Frontend Origin: `${FRONTEND_ORIGIN}` (must begin with `https://` or `PasswordResetProperties` and `UserInvitationProperties` reject startup with `IllegalStateException`).
4. **Mandatory Production ClamAV Scanner:**
   - Configured `app.security.attachment.malware-scanner=clamav` with `app.security.attachment.clamav.host=${CLAMAV_HOST:clamav}` and `port=${CLAMAV_PORT:3310}`.
   - `MalwareScannerConfig.java` enforces that mock/noop scanners cannot be selected under the `prod` profile.
5. **Zero Stack Trace & Error Disclosure:**
   - `server.error.include-stacktrace=never`
   - `server.error.include-message=never`
   - `server.error.include-exception=false`
   - `server.error.include-binding-errors=never`
6. **Production Mail Configuration:**
   - Explicit SMTP properties for port 587 with STARTTLS:
     ```properties
     spring.mail.host=${SMTP_HOST}
     spring.mail.port=${SMTP_PORT:587}
     spring.mail.username=${SMTP_USERNAME}
     spring.mail.password=${SMTP_PASSWORD}
     spring.mail.properties.mail.smtp.auth=true
     spring.mail.properties.mail.smtp.starttls.enable=true
     spring.mail.properties.mail.smtp.starttls.required=true
     ```

---

## 6. Secrets and Environment Handling

1. **Created Template `.env.prod.example`:**
   - Documents every production environment variable required to launch FlowDesk:
     - `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_URL`
     - `JWT_SECRET` (instructions on generating via `openssl rand -base64 32`)
     - `FRONTEND_ORIGIN` (must be `https://flowdesk.yourdomain.com`)
     - `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `FLOWDESK_MAIL_FROM_ADDRESS`, `FLOWDESK_MAIL_FROM_NAME`
     - `GEMINI_API_KEY`
     - `CLAMAV_HOST`, `CLAMAV_PORT`
     - `ATTACHMENT_STORAGE_DIR`
     - `MAILPIT_WEB_PORT` (disabled/unmapped in production)
2. **Hardened `.gitignore`:**
   - Whitelisted `.env.prod.example` via `!.env.prod.example`.
   - Ignored all `.env*` files containing actual values (`.env`, `.env.prod`, `.env.local`).
   - Added explicit patterns to prevent accidental staging of SSL/TLS certificates and SSH keys:
     ```gitignore
     # Security / Credentials & Private Keys
     *.pem
     *.key
     *.p12
     *.pkcs12
     id_rsa*
     id_ed25519*
     ```

---

## 7. Security Tests and Results

### Test Suite 1: Unit Tests (`ClientIpResolverTest.java`)
**Location:** `backend/src/test/java/com/flowdesk/auth/ratelimit/ClientIpResolverTest.java`  
**Execution:** 11 passed, 0 failed, 0 skipped.

1. `testResolveClientIp_WhenTrustProxyDisabled_ReturnsRemoteAddr`: Verifies that when `trustProxyHeaders=false`, `X-Forwarded-For` is ignored.
2. `testResolveClientIp_WhenTrustedProxy_ReturnsXForwardedFor`: Verifies that trusted proxy `127.0.0.1` properly extracts client IP `203.0.113.195`.
3. `testResolveClientIp_WhenUntrustedDirectConnection_IgnoresXForwardedFor`: Verifies that direct untrusted connection `198.51.100.5` cannot spoof `203.0.113.195`.
4. `testResolveClientIp_WhenMultipleXForwardedFor_PicksFirstUntrustedFromRight`: Verifies right-to-left traversal with internal proxy hop (`203.0.113.195, 10.0.0.1`).
5. `testResolveClientIp_WhenSpoofedPrefixInXForwardedFor_PicksGenuineClient`: Verifies spoofed prefix (`1.2.3.4, 203.0.113.195`) extracts genuine client `203.0.113.195`.
6. `testResolveClientIp_WhenIPv6TrustedProxy_ExtractsClient`: Verifies loopback `::1` extracts client IP.
7. `testResolveClientIp_WhenMalformedXff_FallsBackGracefully`: Verifies invalid syntax (`bad-ip-address`) safely falls back to remote address.
8. `testResolveClientIp_WhenNullOrEmptyXff_ReturnsRemoteAddr`: Verifies blank headers return remote address.
9. `testResolveClientIp_FallbackToXRealIp_WhenTrustedProxy`: Verifies `X-Real-IP` fallback when `X-Forwarded-For` is absent.
10. `testIsTrustedProxy`: Exhaustively verifies loopback, private IPv4 (10/8, 172.16/12, 192.168/16, 169.254/16), IPv6 ULA (fc00::/7), and public IPs.
11. `testResolveClientIp_WhenPortInHeader_StripsPort`: Verifies `203.0.113.195:44321` has its port stripped cleanly.

### Test Suite 2: Integration Tests (`ProxyAwareLoginRateLimitingIntegrationTest.java`)
**Location:** `backend/src/test/java/com/flowdesk/auth/controller/ProxyAwareLoginRateLimitingIntegrationTest.java`  
**Execution:** 5 passed, 0 failed, 0 skipped.

1. **Independent Buckets (Test 1):**  
   *Assertion:* Client A (`198.51.100.10`) exhausts the 5-attempt rate limit and receives `429 Too Many Requests`. Client B (`198.51.100.20`) simultaneously attempts authentication and receives `401 Unauthorized` (independent rate limit bucket unaffected).
2. **Repeat Request Rate Limiting (Test 2):**  
   *Assertion:* Repeat failed login attempts from the same IP behind a trusted proxy increment the counter and trigger HTTP `429` with `Retry-After: 900` header once the threshold is crossed.
3. **Forged Header Spoofing Resistance (Test 3):**  
   *Assertion:* Client at `198.51.100.30` sends forged `X-Forwarded-For: 8.8.8.8, 198.51.100.30`. The system parses right-to-left and buckets the request under `198.51.100.30`. Subsequent requests attempting to vary the leftmost token continue to be blocked with HTTP `429`.
4. **Untrusted Direct Connection Protection (Test 4):**  
   *Assertion:* Direct request from public untrusted socket `198.51.100.50` attempts to bypass rate limits by supplying forged `X-Forwarded-For: 1.1.1.1`. The application discards the header, identifies the client as `198.51.100.50`, and blocks with HTTP `429` after 5 attempts.
5. **Valid Authentication Permitted (Test 5):**  
   *Assertion:* Valid user authentication from IP `198.51.100.70` succeeds with HTTP `200 OK` and a valid JWT bearer token, while exhausted IPs remain blocked with HTTP `429`.

### Test Suite 3: Production Profile Startup & Malware Scanner Tests
- `ProductionProfileStartupTest.java`: Verifies clean Spring context initialization under `@ActiveProfiles("prod")`, verifies `ClamAvMalwareScanner` instantiation, confirms graceful shutdown is enabled, and verifies that stack traces are suppressed in error responses.
- `ClamAvMalwareScannerTest.java`: 5 tests verifying daemon connectivity checks, fail-closed handling on offline daemon, file size limit rejection, and rejection of mock scanners in production profile.

---

## 8. Full Regression Test Totals

All regression test suites executed cleanly across the full stack:

| Suite | Scope | Run / Total | Passed | Failed | Skipped | Duration |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Backend Maven Suite** | Full Spring Boot test suite (`mvn test`) | 960 | 960 | 0 | 1 | 1m 34s |
| **Frontend ESLint** | TypeScript / React linting (`npm run lint`) | Clean | Clean | 0 | 0 | 1.8s |
| **Frontend Production Build** | Vite production compilation (`npm run build`) | Built | Built | 0 | 0 | 821ms |
| **Frontend Playwright E2E** | Mocked E2E acceptance suite (`npm run test:e2e`) | 41 | 41 | 0 | 0 | 19.9s |

*Note on Skipped Test:* `AttachmentIntegrationTest.testUploadExceedsMaxFileSize` is `@Disabled` due to embedded MockMvc multipart container limits (existing baseline behavior from Day 62).

---

## 9. Files Changed Summary

```text
.gitignore
.env.prod.example
DAY65_SRE_DEPLOYMENT_READINESS_AUDIT.md
DAY65_PRODUCTION_SECURITY_HARDENING_REPORT.md
backend/src/main/java/com/flowdesk/auth/ratelimit/ClientIpResolver.java
backend/src/main/java/com/flowdesk/auth/ratelimit/LoginRateLimitingFilter.java
backend/src/main/resources/application-prod.properties
backend/src/test/java/com/flowdesk/attachment/malware/ClamAvMalwareScannerTest.java
backend/src/test/java/com/flowdesk/auth/ratelimit/ClientIpResolverTest.java
backend/src/test/java/com/flowdesk/auth/controller/ProxyAwareLoginRateLimitingIntegrationTest.java
backend/src/test/java/com/flowdesk/config/ProductionProfileStartupTest.java
```

---

## 10. Database Safety Confirmation

- **Primary Development Database:** `flowdesk` on local PostgreSQL was completely protected and untouched throughout testing.
- **Flyway Version:** Verified Flyway V25 (`V25__add_ticket_comment_visibility.sql`). No migrations were executed or modified against `flowdesk`.
- **Test Database Isolation:** All integration and startup tests executed against isolated disposable databases (`flowdesk_test` / H2 / MockMvc), leaving production/dev data strictly preserved.

---

## 11. PR Link and GitHub CI Status

- **Pull Request:** [GitHub PR #58](https://github.com/Vibhushan-2003/flowdesk/pull/58)
- **Target Branch:** `main`
- **Head Branch:** `feat/day65-production-security-hardening`
- **GitHub Actions Status:** CI Build & Test in progress.

---

## 12. Remaining Deployment Prerequisites (Day 66 Live Deployment)

Before executing live containerized deployment to Oracle Cloud Always Free, the following Day 66 components will be assembled:

1. **ClamAV Container Service:**
   - Incorporate `clamav/clamav:alpine` into the production `docker-compose.prod.yml` with health check and freshclam signature volume.
2. **Reverse Proxy Configuration (Caddy / Nginx):**
   - Provide Caddyfile or Nginx configuration with automatic Let's Encrypt TLS certificate provisioning, strict HTTP-to-HTTPS redirect, and WebSocket upgrade proxying for `/ws`.
3. **Automated Host Backup Routine:**
   - Write `/opt/flowdesk/scripts/backup.sh` for automated `pg_dump` and attachment volume archiving, scheduled via cron with 7-day retention and off-host transfer instructions.
4. **OCI Security Lists & Host Firewall:**
   - Configure Oracle Cloud VCN Ingress Rules permitting TCP ports 80, 443, and 22 (SSH with ed25519 key only). Ensure database port 5432 and backend port 8080 are not exposed externally.
5. **Production Actuator Metrics:**
   - Secure `/actuator` endpoints behind internal proxy or basic authentication for health and JVM monitoring.

---

## 13. Clear Readiness Assessment for Day 66

**Verdict: READY FOR DAY 66 PRODUCTION DEPLOYMENT**

The FlowDesk codebase has satisfied all Day 65 security and configuration hardening criteria:
- Reverse proxy client-IP spoofing is eliminated via right-to-left header parsing and trusted proxy CIDR verification.
- Production profile enforces fail-fast secrets, HTTPS origin validation, graceful shutdown, and suppression of stack traces.
- ClamAV scanning is enforced in production with fail-closed semantics.
- Full regression suites (960 backend tests, 41 E2E tests, frontend lint and build) are 100% passing.
- The system is positioned for single-node OCI Always Free deployment in Day 66.

