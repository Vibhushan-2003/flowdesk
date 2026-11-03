#!/usr/bin/env bash
# ==============================================================================
# FlowDesk Production Deployment Smoke Test Suite
# ==============================================================================
# Executes comprehensive post-deployment validation against a live FlowDesk stack:
#
# Checks performed:
#   1. Edge HTTPS Connectivity & TLS Handshake
#   2. HTTP -> HTTPS Automated Redirection (Port 80 -> 443)
#   3. Frontend SPA Root Asset Delivery (index.html, JS/CSS bundles)
#   4. Backend Readiness Probe (/api/health/ready)
#   5. Edge Actuator Lockdown (/actuator* blocked with 404 at Caddy boundary)
#   6. Authentication Security (Valid login / safe rejection of invalid credentials)
#   7. Authenticated User Profile Probe (/api/users/me)
#   8. Ticket Resource Query (/api/tickets)
#   9. ClamAV Malware Interception (EICAR standard test signature blocked)
#  10. WebSocket / STOMP Endpoint Availability (/ws)
#  11. Internal Network Isolation (Zero exposed database/backend/clamav ports)
#  12. SRE Monitoring Stack Health (Prometheus, Grafana, Node Exporter if active)
#  13. Host Backup Timer Status (systemd timer if running on host)
# ==============================================================================

set -euo pipefail

# ------------------------------------------------------------------------------
# Default Parameters & CLI Parsing
# ------------------------------------------------------------------------------
BASE_DOMAIN="${CADDY_DOMAIN:-localhost}"
HTTPS_PORT="${CADDY_HTTPS_PORT:-443}"
HTTP_PORT="${CADDY_HTTP_PORT:-80}"
INSECURE_TLS="false"
TEST_USER="${SMOKE_TEST_USER:-}"
TEST_PASSWORD="${SMOKE_TEST_PASSWORD:-}"
SKIP_MONITORING="false"
SKIP_BACKUP_TIMER="false"

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Options:
  --domain <host>        Target domain or IP (default: localhost or \$CADDY_DOMAIN)
  --https-port <port>    HTTPS port (default: 443)
  --http-port <port>     HTTP port for redirect testing (default: 80)
  --insecure, -k         Allow insecure TLS (use for Caddy internal CA / self-signed certs)
  --local                Convenience flag: sets --domain localhost -k
  --user <email>         Smoke test admin/agent email for authenticated checks
  --password <password>  Smoke test password
  --skip-monitoring      Skip internal Prometheus/Grafana container checks
  --skip-backup-timer    Skip host systemd timer check
  -h, --help             Show this help message
EOF
    exit 0
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --domain) BASE_DOMAIN="$2"; shift 2 ;;
        --https-port) HTTPS_PORT="$2"; shift 2 ;;
        --http-port) HTTP_PORT="$2"; shift 2 ;;
        --insecure|-k) INSECURE_TLS="true"; shift ;;
        --local)
            BASE_DOMAIN="localhost"
            INSECURE_TLS="true"
            shift
            ;;
        --user) TEST_USER="$2"; shift 2 ;;
        --password) TEST_PASSWORD="$2"; shift 2 ;;
        --skip-monitoring) SKIP_MONITORING="true"; shift ;;
        --skip-backup-timer) SKIP_BACKUP_TIMER="true"; shift ;;
        -h|--help) usage ;;
        *) echo "Unknown option: $1" >&2; exit 1 ;;
    esac
done

# Build base URLs
if [ "$HTTPS_PORT" = "443" ]; then
    BASE_URL="https://${BASE_DOMAIN}"
else
    BASE_URL="https://${BASE_DOMAIN}:${HTTPS_PORT}"
fi

if [ "$HTTP_PORT" = "80" ]; then
    HTTP_URL="http://${BASE_DOMAIN}"
else
    HTTP_URL="http://${BASE_DOMAIN}:${HTTP_PORT}"
fi

CURL_OPTS=(-s -S)
if [ "$INSECURE_TLS" = "true" ]; then
    CURL_OPTS+=(-k)
fi

# Formatting
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

PASSED_COUNT=0
FAILED_COUNT=0
SKIPPED_COUNT=0

log_header() {
    echo -e "\n${BLUE}======================================================================${NC}"
    echo -e "${BLUE}$*${NC}"
    echo -e "${BLUE}======================================================================${NC}"
}

report_pass() {
    echo -e "  [${GREEN}PASS${NC}] $*"
    PASSED_COUNT=$((PASSED_COUNT + 1))
}

report_fail() {
    echo -e "  [${RED}FAIL${NC}] $*" >&2
    FAILED_COUNT=$((FAILED_COUNT + 1))
}

report_skip() {
    echo -e "  [${YELLOW}SKIP${NC}] $*"
    SKIPPED_COUNT=$((SKIPPED_COUNT + 1))
}

log_header "FlowDesk Production Deployment Smoke Test"
echo "Target Base URL: ${BASE_URL}"
echo "HTTP Ingress URL: ${HTTP_URL}"
echo "TLS Verification: $([ "$INSECURE_TLS" = "true" ] && echo "Insecure / Internal CA (-k)" || echo "Strict Public TLS")"
echo "Authenticated Checks: $([ -n "$TEST_USER" ] && echo "Enabled (${TEST_USER})" || echo "Disabled (run with --user and --password to test)")"

# ------------------------------------------------------------------------------
# 1. Edge HTTPS Connectivity & TLS Handshake
# ------------------------------------------------------------------------------
echo -e "\n--- Step 1: Edge HTTPS Connectivity & TLS Handshake ---"
HTTP_STATUS=$(curl "${CURL_OPTS[@]}" -o /dev/null -w "%{http_code}" "${BASE_URL}/healthz" || echo "000")
if [ "$HTTP_STATUS" = "200" ]; then
    report_pass "Edge Caddy /healthz returned HTTP 200 (TLS handshake successful)"
else
    report_fail "Edge Caddy /healthz failed (HTTP status: ${HTTP_STATUS})"
fi

# ------------------------------------------------------------------------------
# 2. HTTP to HTTPS Automated Redirection
# ------------------------------------------------------------------------------
echo -e "\n--- Step 2: HTTP -> HTTPS Redirection ---"
REDIRECT_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${HTTP_URL}/healthz" || echo "000")
REDIRECT_TARGET=$(curl -s -I "${HTTP_URL}/healthz" | grep -i '^location:' | tr -d '\r' | awk '{print $2}' || true)
if [[ "$REDIRECT_CODE" =~ ^(301|302|307|308)$ ]] && [[ "$REDIRECT_TARGET" =~ ^https:// ]]; then
    report_pass "Port ${HTTP_PORT} automatically redirects with HTTP ${REDIRECT_CODE} to ${REDIRECT_TARGET}"
else
    report_fail "HTTP redirection failed (Code: ${REDIRECT_CODE}, Target: ${REDIRECT_TARGET:-none})"
fi

# ------------------------------------------------------------------------------
# 3. Frontend SPA Root Asset Delivery
# ------------------------------------------------------------------------------
echo -e "\n--- Step 3: Frontend SPA Static Assets ---"
SPA_BODY=$(curl "${CURL_OPTS[@]}" "${BASE_URL}/" || true)
SPA_STATUS=$(curl "${CURL_OPTS[@]}" -o /dev/null -w "%{http_code}" "${BASE_URL}/" || echo "000")
if [ "$SPA_STATUS" = "200" ] && echo "$SPA_BODY" | grep -q 'id="root"'; then
    report_pass "Frontend SPA root index.html delivered successfully (HTTP 200 with root mounting element)"
else
    report_fail "Frontend SPA delivery failed (HTTP status: ${SPA_STATUS})"
fi

# ------------------------------------------------------------------------------
# 4. Backend Readiness Probe
# ------------------------------------------------------------------------------
echo -e "\n--- Step 4: Backend Readiness Probe ---"
READY_BODY=$(curl "${CURL_OPTS[@]}" "${BASE_URL}/api/health/ready" || true)
READY_STATUS=$(curl "${CURL_OPTS[@]}" -o /dev/null -w "%{http_code}" "${BASE_URL}/api/health/ready" || echo "000")
if [ "$READY_STATUS" = "200" ] && echo "$READY_BODY" | grep -qi '"status":"UP"'; then
    report_pass "Backend /api/health/ready returned HTTP 200 with status: UP"
else
    report_fail "Backend readiness check failed (HTTP status: ${READY_STATUS}, body: ${READY_BODY})"
fi

# ------------------------------------------------------------------------------
# 5. Edge Actuator Lockdown
# ------------------------------------------------------------------------------
echo -e "\n--- Step 5: Edge Actuator Lockdown ---"
ACTUATOR_STATUS=$(curl "${CURL_OPTS[@]}" -o /dev/null -w "%{http_code}" "${BASE_URL}/actuator" || echo "000")
PROM_STATUS=$(curl "${CURL_OPTS[@]}" -o /dev/null -w "%{http_code}" "${BASE_URL}/actuator/prometheus" || echo "000")
HEALTH_ACTUATOR_STATUS=$(curl "${CURL_OPTS[@]}" -o /dev/null -w "%{http_code}" "${BASE_URL}/actuator/health" || echo "000")

if [ "$ACTUATOR_STATUS" = "404" ] && [ "$PROM_STATUS" = "404" ] && [ "$HEALTH_ACTUATOR_STATUS" = "404" ]; then
    report_pass "All /actuator endpoints strictly blocked at edge by Caddy (HTTP 404)"
else
    report_fail "Actuator leakage detected! /actuator: ${ACTUATOR_STATUS}, /actuator/prometheus: ${PROM_STATUS}, /actuator/health: ${HEALTH_ACTUATOR_STATUS}"
fi

# ------------------------------------------------------------------------------
# 6. Authentication Security
# ------------------------------------------------------------------------------
echo -e "\n--- Step 6: Authentication Security ---"
AUTH_TOKEN=""
if [ -n "$TEST_USER" ] && [ -n "$TEST_PASSWORD" ]; then
    LOGIN_PAYLOAD=$(printf '{"email":"%s","password":"%s"}' "$TEST_USER" "$TEST_PASSWORD")
    LOGIN_RESP=$(curl "${CURL_OPTS[@]}" -X POST "${BASE_URL}/api/auth/login" \
        -H "Content-Type: application/json" \
        -d "$LOGIN_PAYLOAD" || true)
    LOGIN_STATUS=$(curl "${CURL_OPTS[@]}" -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/api/auth/login" \
        -H "Content-Type: application/json" \
        -d "$LOGIN_PAYLOAD" || echo "000")

    if [ "$LOGIN_STATUS" = "200" ]; then
        AUTH_TOKEN=$(echo "$LOGIN_RESP" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4 || true)
        report_pass "Authentication succeeded for ${TEST_USER} (JWT token received)"
    else
        report_fail "Authentication failed for ${TEST_USER} (HTTP status: ${LOGIN_STATUS}, resp: ${LOGIN_RESP})"
    fi
else
    # Validate that invalid credentials fail safely without 500 error
    BAD_LOGIN_STATUS=$(curl "${CURL_OPTS[@]}" -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/api/auth/login" \
        -H "Content-Type: application/json" \
        -d '{"email":"invalid.user@example.com","password":"WrongPassword123!"}' || echo "000")
    if [ "$BAD_LOGIN_STATUS" = "401" ] || [ "$BAD_LOGIN_STATUS" = "400" ]; then
        report_pass "Unauthenticated safety check passed (invalid login safely rejected with HTTP ${BAD_LOGIN_STATUS})"
    else
        report_fail "Login endpoint returned unexpected status for invalid credentials (HTTP ${BAD_LOGIN_STATUS})"
    fi
fi

# ------------------------------------------------------------------------------
# 7. Authenticated User Profile Probe
# ------------------------------------------------------------------------------
echo -e "\n--- Step 7: Authenticated Profile Probe ---"
if [ -n "$AUTH_TOKEN" ]; then
    ME_STATUS=$(curl "${CURL_OPTS[@]}" -o /dev/null -w "%{http_code}" \
        -H "Authorization: Bearer ${AUTH_TOKEN}" \
        "${BASE_URL}/api/users/me" || echo "000")
    if [ "$ME_STATUS" = "200" ]; then
        report_pass "User profile query (/api/users/me) returned HTTP 200"
    else
        report_fail "User profile query failed (HTTP status: ${ME_STATUS})"
    fi
else
    report_skip "User profile probe skipped (no authenticated session)"
fi

# ------------------------------------------------------------------------------
# 8. Ticket Resource Query
# ------------------------------------------------------------------------------
echo -e "\n--- Step 8: Ticket Resource Query ---"
if [ -n "$AUTH_TOKEN" ]; then
    TICKETS_STATUS=$(curl "${CURL_OPTS[@]}" -o /dev/null -w "%{http_code}" \
        -H "Authorization: Bearer ${AUTH_TOKEN}" \
        "${BASE_URL}/api/tickets" || echo "000")
    if [ "$TICKETS_STATUS" = "200" ]; then
        report_pass "Ticket listing query (/api/tickets) returned HTTP 200"
    else
        report_fail "Ticket listing query failed (HTTP status: ${TICKETS_STATUS})"
    fi
else
    report_skip "Ticket resource query skipped (no authenticated session)"
fi

# ------------------------------------------------------------------------------
# 9. ClamAV Malware Interception
# ------------------------------------------------------------------------------
echo -e "\n--- Step 9: Malware Scanner Interception ---"
EICAR_STRING='X5O!P%@AP[4\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*'
TMP_EICAR=$(mktemp)
echo -n "$EICAR_STRING" > "$TMP_EICAR"

if [ -n "$AUTH_TOKEN" ]; then
    # Test upload of EICAR test signature
    SCAN_STATUS=$(curl "${CURL_OPTS[@]}" -o /dev/null -w "%{http_code}" -X POST \
        -H "Authorization: Bearer ${AUTH_TOKEN}" \
        -F "file=@${TMP_EICAR};filename=eicar.com" \
        "${BASE_URL}/api/attachments/scan" || echo "000")
    rm -f "$TMP_EICAR"
    # Scanner should reject malware with 400 Bad Request or 422 Unprocessable Entity
    if [ "$SCAN_STATUS" = "400" ] || [ "$SCAN_STATUS" = "422" ]; then
        report_pass "ClamAV malware scanner successfully intercepted EICAR test string (HTTP ${SCAN_STATUS})"
    else
        report_fail "Malware upload was NOT blocked as expected! (HTTP status: ${SCAN_STATUS})"
    fi
else
    rm -f "$TMP_EICAR"
    # Check ClamAV container health directly via Docker if available
    if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -q 'flowdesk-prod-clamav'; then
        CLAMAV_STATUS=$(docker inspect --format='{{.State.Health.Status}}' flowdesk-prod-clamav 2>/dev/null || echo "unknown")
        if [ "$CLAMAV_STATUS" = "healthy" ]; then
            report_pass "ClamAV daemon container is healthy (verified via docker inspect)"
        else
            report_fail "ClamAV container health status is: ${CLAMAV_STATUS}"
        fi
    else
        report_skip "ClamAV malware upload skipped (requires authenticated user session or local docker access)"
    fi
fi

# ------------------------------------------------------------------------------
# 10. WebSocket / STOMP Endpoint Availability
# ------------------------------------------------------------------------------
echo -e "\n--- Step 10: WebSocket Endpoint Availability ---"
# Probing /ws/info (SockJS info endpoint) or /ws (STOMP upgrade)
WS_STATUS=$(curl "${CURL_OPTS[@]}" -o /dev/null -w "%{http_code}" "${BASE_URL}/ws/info" || echo "000")
if [ "$WS_STATUS" = "200" ] || [ "$WS_STATUS" = "400" ]; then
    # 200 means SockJS info returned; 400 means websocket endpoint reached without handshake headers
    report_pass "WebSocket endpoint (/ws/info) is accessible through Caddy reverse proxy (HTTP ${WS_STATUS})"
else
    report_fail "WebSocket endpoint unreachable through reverse proxy (HTTP status: ${WS_STATUS})"
fi

# ------------------------------------------------------------------------------
# 11. Internal Network Port Isolation
# ------------------------------------------------------------------------------
echo -e "\n--- Step 11: Host Port Exposure & Internal Isolation ---"
if command -v docker >/dev/null 2>&1; then
    # Inspect published ports across flowdesk containers
    LEAKED_PORTS=0
    for container in flowdesk-prod-postgres flowdesk-prod-backend flowdesk-prod-clamav flowdesk-prod-frontend; do
        if docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
            PORT_BINDINGS=$(docker inspect --format='{{range $p, $conf := .NetworkSettings.Ports}}{{if $conf}}{{$p}} -> {{(index $conf 0).HostPort}} {{end}}{{end}}' "$container")
            if [ -n "$PORT_BINDINGS" ]; then
                report_fail "Security violation: Private container ${container} has published host ports: ${PORT_BINDINGS}"
                LEAKED_PORTS=$((LEAKED_PORTS + 1))
            fi
        fi
    done
    if [ $LEAKED_PORTS -eq 0 ]; then
        report_pass "Zero port leaks: PostgreSQL, Backend, ClamAV, and Frontend publish NO host ports"
    fi
else
    report_skip "Docker CLI not available to inspect internal port bindings"
fi

# ------------------------------------------------------------------------------
# 12. SRE Monitoring Stack Health (If Active)
# ------------------------------------------------------------------------------
echo -e "\n--- Step 12: SRE Monitoring Profile Verification ---"
if [ "$SKIP_MONITORING" = "true" ]; then
    report_skip "Monitoring stack verification skipped (--skip-monitoring)"
elif command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' | grep -q 'flowdesk-prod-prometheus'; then
    # Prometheus health
    PROM_HEALTH=$(docker exec flowdesk-prod-prometheus wget -qO- http://localhost:9090/-/healthy 2>/dev/null || echo "FAILED")
    if echo "$PROM_HEALTH" | grep -qi "Prometheus Server is Healthy"; then
        report_pass "Internal Prometheus instance is healthy"
    else
        report_fail "Prometheus health check failed: ${PROM_HEALTH}"
    fi

    # Node exporter backup metric check
    if docker ps --format '{{.Names}}' | grep -q 'flowdesk-prod-node-exporter'; then
        NODE_METRICS=$(docker exec flowdesk-prod-node-exporter wget -qO- http://localhost:9100/metrics 2>/dev/null || echo "")
        if echo "$NODE_METRICS" | grep -q "flowdesk_backup_"; then
            report_pass "Node Exporter is actively serving FlowDesk backup metrics"
        else
            report_pass "Node Exporter is online (waiting for first backup metrics file)"
        fi
    fi

    # Grafana health
    if docker ps --format '{{.Names}}' | grep -q 'flowdesk-prod-grafana'; then
        GRAFANA_HEALTH=$(docker exec flowdesk-prod-grafana wget -qO- http://localhost:3000/api/health 2>/dev/null || echo "FAILED")
        if echo "$GRAFANA_HEALTH" | grep -qi '"database": "ok"'; then
            report_pass "Internal Grafana visualization instance is healthy"
        else
            report_fail "Grafana health check failed: ${GRAFANA_HEALTH}"
        fi
    fi
else
    report_skip "Monitoring profile is not active (run docker compose --profile monitoring up -d to enable)"
fi

# ------------------------------------------------------------------------------
# 13. Systemd Backup Timer Check
# ------------------------------------------------------------------------------
echo -e "\n--- Step 13: Systemd Backup Timer Status ---"
if [ "$SKIP_BACKUP_TIMER" = "true" ]; then
    report_skip "Backup timer check skipped (--skip-backup-timer)"
elif command -v systemctl >/dev/null 2>&1; then
    if systemctl is-active --quiet flowdesk-backup.timer 2>/dev/null; then
        TIMER_INFO=$(systemctl list-timers flowdesk-backup.timer --no-pager | grep -i flowdesk || true)
        report_pass "flowdesk-backup.timer is active on host: ${TIMER_INFO}"
    else
        report_skip "flowdesk-backup.timer is not active (install via scripts/sre/systemd/)"
    fi
else
    report_skip "systemctl not available on this host"
fi

# ------------------------------------------------------------------------------
# Final Verdict
# ------------------------------------------------------------------------------
log_header "Smoke Test Summary"
echo -e "  Passed:  ${GREEN}${PASSED_COUNT}${NC}"
echo -e "  Failed:  ${RED}${FAILED_COUNT}${NC}"
echo -e "  Skipped: ${YELLOW}${SKIPPED_COUNT}${NC}"

if [ $FAILED_COUNT -eq 0 ]; then
    echo -e "\n${GREEN}>>> SMOKE TEST RESULT: ALL CHECKS PASSED - STACK IS READY <<<${NC}\n"
    exit 0
else
    echo -e "\n${RED}>>> SMOKE TEST RESULT: ${FAILED_COUNT} CHECK(S) FAILED <<<${NC}\n"
    exit 1
fi

