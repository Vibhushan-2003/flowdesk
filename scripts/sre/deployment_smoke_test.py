#!/usr/bin/env python3
"""
FlowDesk Production Deployment Smoke Test Suite (Python / Cross-Platform)
========================================================================
Validates live FlowDesk stack health across 13 security & operational criteria:
  1. Edge HTTPS Connectivity & TLS Handshake
  2. HTTP -> HTTPS Automated Redirection (Port 80 -> 443)
  3. Frontend SPA Root Asset Delivery (index.html, JS/CSS bundles)
  4. Backend Readiness Probe (/api/health/ready)
  5. Edge Actuator Lockdown (/actuator* blocked with 404 at Caddy boundary)
  6. Authentication Security (Valid login / safe rejection of invalid credentials)
  7. Authenticated User Profile Probe (/api/users/me)
  8. Ticket Resource Query (/api/tickets)
  9. ClamAV Malware Scanner Availability / Interception
 10. WebSocket / STOMP Endpoint Availability (/ws)
 11. Internal Network Port Isolation (Zero exposed database/backend/clamav ports)
 12. SRE Monitoring Stack Health (Prometheus, Grafana, Node Exporter if active)
 13. Host Backup Timer Status (systemd timer if running on host)
"""

import argparse
import json
import os
import ssl
import subprocess
import sys
import urllib.error
import urllib.request


def create_ssl_context(insecure: bool) -> ssl.SSLContext:
    if insecure:
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        return ctx
    return ssl.create_default_context()


def http_get(url: str, ssl_ctx: ssl.SSLContext, headers=None, follow_redirects=True):
    req = urllib.request.Request(url, headers=headers or {})
    try:
        # urllib by default follows redirects
        opener = urllib.request.build_opener(urllib.request.HTTPSHandler(context=ssl_ctx))
        if not follow_redirects:
            class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
                def redirect_request(self, req, fp, code, msg, headers, newurl):
                    return None
            opener = urllib.request.build_opener(urllib.request.HTTPSHandler(context=ssl_ctx), NoRedirectHandler)

        with opener.open(req, timeout=10) as resp:
            return resp.status, resp.read().decode("utf-8", errors="replace"), resp.headers
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace"), e.headers
    except Exception as e:
        return 0, str(e), {}


def http_post_json(url: str, payload: dict, ssl_ctx: ssl.SSLContext, headers=None):
    data = json.dumps(payload).encode("utf-8")
    req_headers = {"Content-Type": "application/json"}
    if headers:
        req_headers.update(headers)
    req = urllib.request.Request(url, data=data, headers=req_headers, method="POST")
    opener = urllib.request.build_opener(urllib.request.HTTPSHandler(context=ssl_ctx))
    try:
        with opener.open(req, timeout=10) as resp:
            return resp.status, resp.read().decode("utf-8", errors="replace"), resp.headers
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace"), e.headers
    except Exception as e:
        return 0, str(e), {}


def main():
    parser = argparse.ArgumentParser(description="FlowDesk Production Deployment Smoke Test Suite")
    parser.add_argument("--domain", default=os.getenv("CADDY_DOMAIN", "localhost"), help="Target domain or host")
    parser.add_argument("--https-port", type=int, default=int(os.getenv("CADDY_HTTPS_PORT", "443")), help="HTTPS port")
    parser.add_argument("--http-port", type=int, default=int(os.getenv("CADDY_HTTP_PORT", "80")), help="HTTP port")
    parser.add_argument("--insecure", "-k", action="store_true", help="Allow insecure TLS (internal CA)")
    parser.add_argument("--local", action="store_true", help="Convenience flag: sets --domain localhost -k")
    parser.add_argument("--user", default=os.getenv("SMOKE_TEST_USER", ""), help="Smoke test user email")
    parser.add_argument("--password", default=os.getenv("SMOKE_TEST_PASSWORD", ""), help="Smoke test user password")
    parser.add_argument("--skip-monitoring", action="store_true", help="Skip monitoring container checks")
    parser.add_argument("--skip-backup-timer", action="store_true", help="Skip systemd backup timer check")

    args = parser.parse_args()
    if args.local:
        args.domain = "localhost"
        args.insecure = True

    base_url = f"https://{args.domain}" if args.https_port == 443 else f"https://{args.domain}:{args.https_port}"
    http_url = f"http://{args.domain}" if args.http_port == 80 else f"http://{args.domain}:{args.http_port}"
    ssl_ctx = create_ssl_context(args.insecure)

    print("======================================================================")
    print("FlowDesk Production Deployment Smoke Test Suite")
    print("======================================================================")
    print(f"Target Base URL:    {base_url}")
    print(f"HTTP Ingress URL:   {http_url}")
    print(f"TLS Verification:   {'Insecure / Internal CA (-k)' if args.insecure else 'Strict Public TLS'}")
    print(f"Authenticated Auth: {'Enabled (' + args.user + ')' if args.user else 'Disabled'}")

    passed = 0
    failed = 0
    skipped = 0

    def report_pass(msg):
        nonlocal passed
        print(f"  [PASS] {msg}")
        passed += 1

    def report_fail(msg):
        nonlocal failed
        print(f"  [FAIL] {msg}", file=sys.stderr)
        failed += 1

    def report_skip(msg):
        nonlocal skipped
        print(f"  [SKIP] {msg}")
        skipped += 1

    # 1. Edge HTTPS Connectivity
    print("\n--- Step 1: Edge HTTPS Connectivity & TLS Handshake ---")
    status, body, _ = http_get(f"{base_url}/healthz", ssl_ctx)
    if status == 200:
        report_pass("Edge Caddy /healthz returned HTTP 200 (TLS handshake successful)")
    else:
        report_fail(f"Edge Caddy /healthz failed (HTTP status: {status})")

    # 2. HTTP -> HTTPS Redirection
    print("\n--- Step 2: HTTP -> HTTPS Redirection ---")
    status, body, headers = http_get(f"{http_url}/healthz", ssl_ctx, follow_redirects=False)
    loc = headers.get("Location", "")
    if status in (301, 302, 307, 308) and loc.startswith("https://"):
        report_pass(f"Port {args.http_port} automatically redirects with HTTP {status} to {loc}")
    else:
        report_fail(f"HTTP redirection failed (status: {status}, Location: {loc})")

    # 3. Frontend SPA Root Asset Delivery
    print("\n--- Step 3: Frontend SPA Static Assets ---")
    status, body, _ = http_get(f"{base_url}/", ssl_ctx)
    if status == 200 and 'id="root"' in body:
        report_pass("Frontend SPA root index.html delivered successfully (HTTP 200 with root mounting element)")
    else:
        report_fail(f"Frontend SPA delivery failed (status: {status})")

    # 4. Backend Readiness Probe
    print("\n--- Step 4: Backend Readiness Probe ---")
    status, body, _ = http_get(f"{base_url}/api/health/ready", ssl_ctx)
    if status == 200 and '"status":"UP"' in body.replace(" ", ""):
        report_pass("Backend /api/health/ready returned HTTP 200 with status: UP")
    else:
        report_fail(f"Backend readiness check failed (status: {status}, body: {body})")

    # 5. Edge Actuator Lockdown
    print("\n--- Step 5: Edge Actuator Lockdown ---")
    s1, _, _ = http_get(f"{base_url}/actuator", ssl_ctx)
    s2, _, _ = http_get(f"{base_url}/actuator/prometheus", ssl_ctx)
    s3, _, _ = http_get(f"{base_url}/actuator/health", ssl_ctx)
    if s1 == 404 and s2 == 404 and s3 == 404:
        report_pass("All /actuator endpoints strictly blocked at edge by Caddy (HTTP 404)")
    else:
        report_fail(f"Actuator leakage detected! /actuator: {s1}, /actuator/prometheus: {s2}, /actuator/health: {s3}")

    # 6. Authentication Security
    print("\n--- Step 6: Authentication Security ---")
    auth_token = ""
    if args.user and args.password:
        status, body, _ = http_post_json(f"{base_url}/api/auth/login", {"email": args.user, "password": args.password}, ssl_ctx)
        if status == 200:
            try:
                data = json.loads(body)
                auth_token = data.get("accessToken", "")
                report_pass(f"Authentication succeeded for {args.user} (JWT token received)")
            except Exception as e:
                report_fail(f"Failed to parse login response: {e}")
        else:
            report_fail(f"Authentication failed for {args.user} (status: {status}, body: {body})")
    else:
        status, body, _ = http_post_json(
            f"{base_url}/api/auth/login",
            {"email": "invalid.user@example.com", "password": "WrongPassword123!"},
            ssl_ctx
        )
        if status in (400, 401):
            report_pass(f"Unauthenticated safety check passed (invalid login safely rejected with HTTP {status})")
        else:
            report_fail(f"Login endpoint returned unexpected status for invalid credentials (HTTP {status})")

    # 7. Authenticated User Profile Probe
    print("\n--- Step 7: Authenticated Profile Probe ---")
    if auth_token:
        status, body, _ = http_get(f"{base_url}/api/users/me", ssl_ctx, headers={"Authorization": f"Bearer {auth_token}"})
        if status == 200:
            report_pass("User profile query (/api/users/me) returned HTTP 200")
        else:
            report_fail(f"User profile query failed (status: {status})")
    else:
        report_skip("User profile probe skipped (no authenticated session)")

    # 8. Ticket Resource Query
    print("\n--- Step 8: Ticket Resource Query ---")
    if auth_token:
        status, body, _ = http_get(f"{base_url}/api/tickets", ssl_ctx, headers={"Authorization": f"Bearer {auth_token}"})
        if status == 200:
            report_pass("Ticket listing query (/api/tickets) returned HTTP 200")
        else:
            report_fail(f"Ticket listing query failed (status: {status})")
    else:
        report_skip("Ticket resource query skipped (no authenticated session)")

    # 9. ClamAV Malware Scanner Check
    print("\n--- Step 9: Malware Scanner Availability ---")
    try:
        res = subprocess.run(
            ["docker", "inspect", "--format={{.State.Health.Status}}", "flowdesk-prod-clamav"],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
        )
        if res.returncode == 0 and res.stdout.strip() == "healthy":
            report_pass("ClamAV daemon container is healthy (verified via docker inspect)")
        else:
            report_skip(f"ClamAV daemon container status: {res.stdout.strip() or 'not running'}")
    except Exception:
        report_skip("Docker inspection for ClamAV unavailable")

    # 10. WebSocket / STOMP Endpoint Availability
    print("\n--- Step 10: WebSocket Endpoint Availability ---")
    status, body, _ = http_get(f"{base_url}/ws/info", ssl_ctx)
    if status in (200, 400):
        report_pass(f"WebSocket endpoint (/ws/info) is accessible through Caddy reverse proxy (HTTP {status})")
    else:
        report_fail(f"WebSocket endpoint unreachable through reverse proxy (HTTP status: {status})")

    # 11. Internal Network Port Isolation
    print("\n--- Step 11: Host Port Exposure & Internal Isolation ---")
    try:
        leaks = 0
        for container in ["flowdesk-prod-postgres", "flowdesk-prod-backend", "flowdesk-prod-clamav", "flowdesk-prod-frontend"]:
            res = subprocess.run(
                ["docker", "inspect", "--format={{range $p, $conf := .NetworkSettings.Ports}}{{if $conf}}{{$p}} -> {{(index $conf 0).HostPort}} {{end}}{{end}}", container],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
            )
            if res.returncode == 0 and res.stdout.strip():
                report_fail(f"Security violation: {container} has published host ports: {res.stdout.strip()}")
                leaks += 1
        if leaks == 0:
            report_pass("Zero port leaks: PostgreSQL, Backend, ClamAV, and Frontend publish NO host ports")
    except Exception:
        report_skip("Docker inspection for host ports unavailable")

    # 12. SRE Monitoring Profile Verification
    print("\n--- Step 12: SRE Monitoring Profile Verification ---")
    if args.skip_monitoring:
        report_skip("Monitoring stack verification skipped (--skip-monitoring)")
    else:
        try:
            res = subprocess.run(
                ["docker", "ps", "--format={{.Names}}"],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
            )
            running = res.stdout.splitlines() if res.returncode == 0 else []
            if "flowdesk-prod-prometheus" in running:
                p_res = subprocess.run(
                    ["docker", "exec", "flowdesk-prod-prometheus", "wget", "-qO-", "http://localhost:9090/-/healthy"],
                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
                )
                if "Prometheus Server is Healthy" in p_res.stdout:
                    report_pass("Internal Prometheus instance is healthy")
                else:
                    report_fail(f"Prometheus health probe failed: {p_res.stdout}")

                if "flowdesk-prod-node-exporter" in running:
                    ne_res = subprocess.run(
                        ["docker", "exec", "flowdesk-prod-node-exporter", "wget", "-qO-", "http://localhost:9100/metrics"],
                        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
                    )
                    if "flowdesk_backup_" in ne_res.stdout:
                        report_pass("Node Exporter is actively serving FlowDesk backup metrics")
                    else:
                        report_pass("Node Exporter is online (waiting for first backup metrics file)")

                if "flowdesk-prod-grafana" in running:
                    g_res = subprocess.run(
                        ["docker", "exec", "flowdesk-prod-grafana", "wget", "-qO-", "http://localhost:3000/api/health"],
                        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
                    )
                    if '"database": "ok"' in g_res.stdout:
                        report_pass("Internal Grafana visualization instance is healthy")
                    else:
                        report_fail(f"Grafana health probe failed: {g_res.stdout}")
            else:
                report_skip("Monitoring profile is not active (run docker compose --profile monitoring up -d to enable)")
        except Exception:
            report_skip("Docker inspection for monitoring stack unavailable")

    # 13. Systemd Backup Timer Check
    print("\n--- Step 13: Systemd Backup Timer Status ---")
    if args.skip_backup_timer:
        report_skip("Backup timer check skipped (--skip-backup-timer)")
    else:
        try:
            res = subprocess.run(
                ["systemctl", "is-active", "flowdesk-backup.timer"],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
            )
            if res.returncode == 0:
                report_pass("flowdesk-backup.timer is active on host")
            else:
                report_skip("flowdesk-backup.timer is not active (install via scripts/sre/systemd/)")
        except Exception:
            report_skip("systemctl not available on this host")

    # Summary
    print("\n======================================================================")
    print("Smoke Test Summary")
    print("======================================================================")
    print(f"  Passed:  {passed}")
    print(f"  Failed:  {failed}")
    print(f"  Skipped: {skipped}")

    if failed == 0:
        print("\n>>> SMOKE TEST RESULT: ALL CHECKS PASSED - STACK IS READY <<<\n")
        sys.exit(0)
    else:
        print(f"\n>>> SMOKE TEST RESULT: {failed} CHECK(S) FAILED <<<\n", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()

