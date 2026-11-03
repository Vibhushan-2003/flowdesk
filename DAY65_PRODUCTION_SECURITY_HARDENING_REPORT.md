# FlowDesk Day 65 — Production Security Hardening & Configuration Report

**Report Date:** November 3, 2026  
**Repository:** `C:\Users\gvibh\Projects\flowdesk`  
**Active Working Branch:** `feat/day65-production-security-hardening`  
**Base Commit:** `b44e962` (`main` containing Day 64 PR #57 and Flyway V25)  
**Status:** Complete — Security Hardened, Embedded Tomcat Verified, Ready for Cloud Deployment Architecture (Day 66)

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

## 3. Exact Client-IP Resolution & Trust Model

### Architectural Problem & Reviewer Findings
FlowDesk enforces per-IP and per-account rate limiting on authentication endpoints (`/api/auth/login`) via `LoginRateLimitingFilter.java`. An independent security review established the following core constraints:
1. **Tomcat RemoteIpValve Rewrite:** When `server.forward-headers-strategy=native` is active, Tomcat's `RemoteIpValve` inspects incoming TCP connections. If the peer matches `internal-proxies`, Tomcat rewrites `request.getRemoteAddr()` to the untrusted client IP *before* custom application filters run. The application filter must not assume `request.getRemoteAddr()` is the original TCP peer.
2. **Risk of Blind Private Network Trust:** Blindly trusting all RFC 1918 subnets (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`) as reverse proxies allows any malicious container on the Docker bridge or any adjacent machine in the VPC to inject arbitrary `X-Forwarded-For` headers and spoof client identities.
3. **Internal Client Preservations:** Corporate employees accessing the service over private IP addresses (e.g. VPN `10.0.1.50`) through the ingress reverse proxy must have their client IP recognized, rather than being discarded by Tomcat as an "internal proxy" and lumped into a shared `127.0.0.1` bucket.

### Coherent Trust Model Implementation
FlowDesk implements an explicit, unified trust model across both Tomcat and Spring Security:

#### 1. Ingress Boundary & Default Trust (Loopback Only)
- In the production single-VM architecture (Day 66), Caddy terminates TLS on host ports 80/443 and reverse proxies traffic to `http://127.0.0.1:8080`.
- Therefore, by default, **ONLY loopback addresses** (`127.0.0.1`, `::1`, `0:0:0:0:0:0:0:1`, `localhost`, `127.0.0.0/8`) are trusted proxies.
- Containers on internal Docker networks (`172.18.0.x`) and machines on local LANs (`10.x.x.x`, `192.168.x.x`) are **strictly UNTRUSTED by default**.
- If a containerized proxy topology is used, the operator explicitly declares the proxy IP or CIDR via `RATE_LIMIT_TRUSTED_PROXIES` and `TOMCAT_INTERNAL_PROXIES`.

#### 2. Native Tomcat RemoteIpValve (`application-prod.properties`)
```properties
server.forward-headers-strategy=${SERVER_FORWARD_HEADERS_STRATEGY:native}
server.tomcat.remoteip.remote-ip-header=x-forwarded-for
server.tomcat.remoteip.protocol-header=x-forwarded-proto
server.tomcat.remoteip.internal-proxies=${TOMCAT_INTERNAL_PROXIES:127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|::1|0:0:0:0:0:0:0:1}
```

#### 3. Application Resolver Defense-in-Depth (`ClientIpResolver.java`)
`ClientIpResolver.java` provides defense-in-depth across both embedded Tomcat runtime and MockMvc integration tests:
- Evaluates `request.getRemoteAddr()`.
- If `request.getRemoteAddr()` is NOT a trusted proxy: returns `remoteAddr` immediately; all forwarded headers are discarded.
- If `request.getRemoteAddr()` IS a trusted proxy: traverses `X-Forwarded-For` from **right to left**, skipping intermediate trusted proxies and stopping at the first untrusted IP.
- Supports CIDR subnet matching (e.g. `172.18.0.0/16`) and exact IP matching for declared `trusted-proxies`.
- Normalizes IPv4 and IPv6 without performing blocking DNS lookups.

#### 4. Filter Wiring (`RateLimiterProperties.java` & Filters)
All rate limiters (`LoginRateLimitingFilter`, `OAuthRateLimitingFilter`, `PasswordResetRateLimitingFilter`, `AcceptInvitationRateLimitingFilter`) configure `trustedProxies` via:
```properties
app.security.rate-limit.login.trusted-proxies=${RATE_LIMIT_TRUSTED_PROXIES:}
```
and invoke:
```java
String clientIp = ClientIpResolver.resolveClientIp(request, properties.isTrustProxyHeaders(), properties.getTrustedProxies());
```

---

## 4. Proxy Spoofing Defence

### Threat Scenarios and Mitigations

| Threat | Attack Vector | FlowDesk Mitigation |
| :--- | :--- | :--- |
| **Direct Header Injection** | Attacker connects directly to port 8080 (bypassing reverse proxy) and sends `X-Forwarded-For: 1.2.3.4`. | Neither Tomcat nor `ClientIpResolver` trusts the caller IP. Forwarded headers are discarded; the socket address is returned. |
| **Malicious Internal Container** | Attacker container on Docker bridge (`172.18.0.5`) connects directly to `backend:8080` sending `X-Forwarded-For: 8.8.8.8`. | `172.18.0.5` is NOT trusted by default. Both Tomcat and `ClientIpResolver` reject the header and rate-limit under `172.18.0.5`. |
| **Pre-pended Header Spoofing** | Attacker connects through reverse proxy sending `X-Forwarded-For: 203.0.113.195`. Proxy appends real client: `X-Forwarded-For: 203.0.113.195, 198.51.100.24`. | Right-to-left traversal identifies `198.51.100.24` (the real client); the forged prefix `203.0.113.195` is ignored. |
| **Corporate VPN / LAN Clients** | Corporate user with private IP (`10.0.1.50`) accesses app through reverse proxy. | Because `10.0.0.0/8` is not treated as an internal proxy, Tomcat and `ClientIpResolver` identify `10.0.1.50` as the genuine client, preserving individual rate limit quotas. |
| **Syntax / Injection Attacks** | Attacker sends malformed tokens: `X-Forwarded-For: <script>, 198.51.100.24`. | `isValidIp()` verifies IPv4/IPv6 syntax without DNS lookups. Malformed tokens are stripped safely. |

---

## 5. Production Configuration Changes (`application-prod.properties`)

1. **Native Tomcat Reverse Proxy Valve:**
   - Defaulted `internal-proxies` strictly to loopback (`127.0.0.0/8`, `::1`), overridable via `TOMCAT_INTERNAL_PROXIES`.
2. **Standardized Database Username Mapping:**
   - Configured `spring.datasource.username=${DB_USERNAME:${DB_USER}}` in both `application.properties` and `application-prod.properties`, enabling seamless interoperation between Spring convention (`DB_USERNAME`) and Docker convention (`DB_USER`).
3. **Optional AI & Google OAuth Dormant Startup:**
   - Configured `app.ai.gemini.api-key=${GEMINI_API_KEY:}` with empty default. All four Gemini AI clients check `apiKey.isBlank()` on request, allowing production startup with clean zero-error initialization when AI is omitted.
   - Configured `app.security.oauth2.google.enabled=${GOOGLE_OAUTH_ENABLED:false}`.
4. **Graceful Shutdown & Lifecycle Timeouts:**
   - Configured `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=30s`.
5. **Fail-Fast Production Secrets:**
   - Database credentials (`DB_URL`, `DB_PASSWORD`), mandatory 256-bit Base64 JWT secret (`JWT_SECRET`), and mandatory HTTPS frontend origin (`FRONTEND_ORIGIN`).
6. **Mandatory Production ClamAV Scanner:**
   - Configured `app.attachment.malware-scanner.type=clamav`. `MalwareScannerConfig.java` enforces that mock/noop scanners cannot be loaded under the `prod` profile.
7. **Zero Stack Trace & Error Disclosure:**
   - `server.error.include-stacktrace=never`, `server.error.include-message=never`, `server.error.include-exception=false`.
8. **Production Mail Configuration:**
   - Explicit SMTP properties for port 587 with STARTTLS. `EmailProperties` enforces encryption and non-localhost host when enabled, and cleanly bypasses when disabled.

---

## 6. Secrets and Environment Handling

1. **Created Template `.env.prod.example`:**
   - Documents every production environment variable required to launch FlowDesk:
     - `DB_NAME`, `DB_USER`, `DB_USERNAME`, `DB_PASSWORD`, `DB_URL`
     - `JWT_SECRET` (instructions on generating via `openssl rand -base64 32`)
     - `FRONTEND_ORIGIN` (must be `https://flowdesk.yourdomain.com`)
     - `RATE_LIMIT_LOGIN_TRUST_PROXY`, `RATE_LIMIT_TRUSTED_PROXIES`, `TOMCAT_INTERNAL_PROXIES`
     - `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `FLOWDESK_MAIL_FROM_ADDRESS`, `FLOWDESK_MAIL_FROM_NAME`
     - `GEMINI_API_KEY`, `GOOGLE_OAUTH_ENABLED`
     - `CLAMAV_HOST`, `CLAMAV_PORT`
     - `ATTACHMENT_STORAGE_DIR`
2. **Hardened `.gitignore`:**
   - Whitelisted `.env.prod.example` via `!.env.prod.example`.
   - Ignored all `.env*` files containing actual values (`.env`, `.env.prod`, `.env.local`).
   - Added explicit patterns to prevent accidental staging of SSL/TLS certificates and SSH keys (`*.pem`, `*.key`, `*.p12`, `*.pkcs12`, `id_rsa*`, `id_ed25519*`).

---

## 7. Security Tests and Results

### Test Suite 1: Embedded Tomcat Integration Tests (`EmbeddedTomcatRemoteIpIntegrationTest.java`)
**Location:** `backend/src/test/java/com/flowdesk/auth/controller/EmbeddedTomcatRemoteIpIntegrationTest.java`  
**Execution:** 3 passed, 0 failed, 0 skipped.
- Tests executed against real embedded Tomcat on a random port over actual TCP loopback HTTP connections:
  1. `distinctForwardedIpsGetIndependentBucketsOverRealTomcat`: Validates that Tomcat's `RemoteIpValve` rewrites `getRemoteAddr()` to the forwarded client IP, and two distinct clients through the same proxy receive independent rate-limit quotas.
  2. `forgedPrefixCannotBypassRateLimitOverRealTomcat`: Validates that forged `X-Forwarded-For` prefixes sent through the proxy are ignored; right-to-left traversal extracts the genuine client IP and enforces rate limiting.
  3. `ipv6ClientAddressRateLimitedOverRealTomcat`: Validates that an IPv6 client address in `X-Forwarded-For` is extracted and rate-limited.

### Test Suite 2: Production Environment Contracts (`ProductionEnvironmentContractsTest.java`)
**Location:** `backend/src/test/java/com/flowdesk/config/ProductionEnvironmentContractsTest.java`  
**Execution:** 7 passed, 0 failed, 0 skipped.
- Validates:
  1. `productionStartsWithoutGeminiApiKey`: Verifies production context starts cleanly with empty `GEMINI_API_KEY`.
  2. `productionRejectsNonHttpsPasswordResetOrigin`: Verifies `PasswordResetProperties` rejects `http://` origins in production mode.
  3. `productionRejectsNonHttpsInvitationOrigin`: Verifies `UserInvitationProperties` rejects `http://` origins in production mode.
  4. `productionAcceptsValidHttpsOrigins`: Verifies HTTPS origins pass and trailing slashes are normalized.
  5. `productionEmailEnforcesTlsAndNonLocalhost`: Verifies `EmailProperties` requires STARTTLS/SSL and non-localhost host when enabled.
  6. `productionEmailBypassesValidationWhenDisabled`: Verifies `EmailProperties` cleanly bypasses validation when `app.mail.enabled=false`.
  7. `databaseUsernameMappingSupportsFallback`: Verifies `${DB_USERNAME:${DB_USER}}` fallback behavior.

### Test Suite 3: Unit Tests (`ClientIpResolverTest.java`)
**Location:** `backend/src/test/java/com/flowdesk/auth/ratelimit/ClientIpResolverTest.java`  
**Execution:** 14 passed, 0 failed, 0 skipped.
- Verifies:
  1. `trustProxyHeadersDisabledAlwaysReturnsRemoteAddr`
  2. `directUntrustedConnectionIgnoresForwardedHeaders`
  3. `defaultTrustedProxyExtractsClientIp`
  4. `unconfiguredInternalContainerCannotForgeClientIdentity` (malicious Docker container defense)
  5. `explicitlyConfiguredProxyIsTrusted` (exact IP and CIDR subnet matching)
  6. `corporateVpnClientBehindTrustedProxyIsNotTreatedAsProxy` (private IP client preservation)
  7. `spoofedForwardedHeadersDiscarded` (right-to-left parsing)
  8. `proxyChainingTraversesIntermediateProxies`
  9. `ipv6AddressExtractedCleanly`
  10. `malformedForwardedHeadersFallbackSafely`
  11. `xRealIpFallback`
  12. `customTrustedProxiesRespected`
  13. `handleNullOrEmptyRemoteAddr`
  14. `isTrustedProxyClassification`

### Test Suite 4: Integration Tests (`ProxyAwareLoginRateLimitingIntegrationTest.java`)
**Location:** `backend/src/test/java/com/flowdesk/auth/controller/ProxyAwareLoginRateLimitingIntegrationTest.java`  
**Execution:** 5 passed, 0 failed, 0 skipped.

### Test Suite 5: Production Startup & Malware Scanner Tests
- `ProductionProfileStartupTest.java`: 1 test passed.
- `ClamAvMalwareScannerTest.java`: 5 tests passed.

---

## 8. Full Regression Test Totals

| Suite | Scope | Run / Total | Passed | Failed | Skipped | Duration |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Backend Maven Suite** | Full Spring Boot test suite (`mvn test`) | 973 | 973 | 0 | 1 | 1m 36s |
| **Frontend ESLint** | TypeScript / React linting (`npm run lint`) | Clean | Clean | 0 | 0 | 1.8s |
| **Frontend Production Build** | Vite production compilation (`npm run build`) | Built | Built | 0 | 0 | 1.69s |
| **Frontend Playwright E2E** | Mocked E2E acceptance suite (`npm run test:e2e`) | 41 | 41 | 0 | 0 | 21.7s |

---

## 9. Files Changed Summary

```text
.env.prod.example
DAY65_PRODUCTION_SECURITY_HARDENING_REPORT.md
DAY65_SRE_DEPLOYMENT_READINESS_AUDIT.md
backend/src/main/java/com/flowdesk/auth/ratelimit/AcceptInvitationRateLimitingFilter.java
backend/src/main/java/com/flowdesk/auth/ratelimit/ClientIpResolver.java
backend/src/main/java/com/flowdesk/auth/ratelimit/ForgotPasswordRateLimiterProperties.java
backend/src/main/java/com/flowdesk/auth/ratelimit/InvitationRateLimiterProperties.java
backend/src/main/java/com/flowdesk/auth/ratelimit/LoginRateLimitingFilter.java
backend/src/main/java/com/flowdesk/auth/ratelimit/OAuthRateLimiterProperties.java
backend/src/main/java/com/flowdesk/auth/ratelimit/OAuthRateLimitingFilter.java
backend/src/main/java/com/flowdesk/auth/ratelimit/PasswordResetRateLimitingFilter.java
backend/src/main/java/com/flowdesk/auth/ratelimit/RateLimiterProperties.java
backend/src/main/resources/application-prod.properties
backend/src/main/resources/application.properties
backend/src/test/java/com/flowdesk/attachment/malware/ClamAvMalwareScannerTest.java
backend/src/test/java/com/flowdesk/auth/controller/EmbeddedTomcatRemoteIpIntegrationTest.java
backend/src/test/java/com/flowdesk/auth/controller/ProxyAwareLoginRateLimitingIntegrationTest.java
backend/src/test/java/com/flowdesk/auth/ratelimit/ClientIpResolverTest.java
backend/src/test/java/com/flowdesk/config/ProductionEnvironmentContractsTest.java
backend/src/test/java/com/flowdesk/config/ProductionProfileStartupTest.java
```

---

## 10. Database Safety Confirmation

- **Primary Development Database:** `flowdesk` on local PostgreSQL was completely protected and untouched throughout testing.
- **Flyway Version:** Verified Flyway V25 (`V25__add_ticket_comment_visibility.sql`).
- **Test Database Isolation:** All tests executed against isolated disposable databases (`flowdesk_test` / H2 / MockMvc / embedded Tomcat).

---

## 11. PR Link and GitHub CI Status

- **Pull Request:** [GitHub PR #58](https://github.com/Vibhushan-2003/flowdesk/pull/58)
- **Target Branch:** `main`
- **Head Branch:** `feat/day65-production-security-hardening`
- **Latest Commit SHA:** `3202b2d` (*feat(security): refine client IP trust model and validate production contracts*)
- **GitHub Actions CI Status:** **PASS** (100% Green, 3/3 jobs passed)
  - Workflow Run: `41565065161`
  - Backend Build & Test: `PASS` in 1m 53s
  - Frontend Build & Lint: `PASS` in 1m 32s
  - Real-Stack E2E Acceptance: `PASS` in 2m 31s

---

## 12. Remaining Day 66 Deployment Dependencies

The following tasks depend on the Day 66 live provisioning phase and are explicitly documented:
1. **Host-Level Caddy Stack:** Verification of live external TLS handshake, automatic Let's Encrypt certificate renewal, and public-to-localhost proxy routing (`127.0.0.1:8080`) requires the actual Day 66 VM / Caddy container.
2. **ClamAV Live Signature Mirror:** Initial signature definition download via `freshclam` during first compose startup.
3. **OCI Host Firewall & VCN Ingress:** Configuring Oracle Cloud VCN Security Lists (ports 80, 443, 22 only).
4. **Disaster Recovery Replication:** Implementing the off-host backup script syncing snapshots to remote storage.

---

## 13. Final GO / NO-GO Assessment for PR #58

**Verdict: GO — PR #58 IS READY FOR MERGE**

- Coherent, documented client-IP trust model implemented and verified over embedded Tomcat.
- No blind trust of private subnets or internal Docker containers; IP spoofing is eliminated.
- Database username interoperability (`DB_USERNAME:${DB_USER}`) resolved.
- Optional AI and OAuth features cleanly start dormant without mandatory secrets.
- Full regression suite (973 backend tests, 41 E2E tests, clean lint and build) is 100% passing.
- Database safety guaranteed at Flyway V25.
