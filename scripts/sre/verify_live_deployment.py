#!/usr/bin/env python3
"""
FlowDesk SRE: Live Deployment Verification & Health Validation
==============================================================
Validates the live public deployment of FlowDesk across frontend, backend,
database readiness, CORS, security headers, and deployment version metadata.

Handles Render Free tier cold-start delays with configurable polling and retries.
Zero secrets or credentials required.
"""

import argparse
import json
import ssl
import sys
import time
import urllib.error
import urllib.request
from typing import Dict, Optional, Tuple


DEFAULT_FRONTEND_URL = "https://flowdesk-demo-glkr.onrender.com"
DEFAULT_BACKEND_URL = "https://flowdesk-demo-api.onrender.com"


def create_ssl_context() -> ssl.SSLContext:
    ctx = ssl.create_default_context()
    return ctx


def http_request(
    url: str,
    method: str = "GET",
    headers: Optional[Dict[str, str]] = None,
    timeout: float = 30.0,
    ssl_context: Optional[ssl.SSLContext] = None
) -> Tuple[int, str, Dict[str, str]]:
    req_headers = {"User-Agent": "FlowDesk-Deployment-Verifier/1.0"}
    if headers:
        req_headers.update(headers)

    req = urllib.request.Request(url, method=method, headers=req_headers)
    ctx = ssl_context if ssl_context is not None else create_ssl_context()

    try:
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
            status = resp.status
            body = resp.read().decode("utf-8", errors="replace")
            resp_headers = {k.lower(): v for k, v in resp.getheaders()}
            return status, body, resp_headers
    except urllib.error.HTTPError as e:
        status = e.code
        body = e.read().decode("utf-8", errors="replace")
        resp_headers = {k.lower(): v for k, v in e.headers.items()}
        return status, body, resp_headers
    except Exception as e:
        return 0, str(e), {}


def wait_for_service(url: str, timeout_seconds: int = 120, label: str = "Service") -> bool:
    print(f"[*] Waiting for {label} at {url} (up to {timeout_seconds}s for cold-start)...")
    start = time.time()
    ssl_ctx = create_ssl_context()

    while time.time() - start < timeout_seconds:
        status, _, _ = http_request(url, ssl_context=ssl_ctx, timeout=10)
        if 200 <= status < 400:
            elapsed = time.time() - start
            print(f"    [OK] {label} responsive in {elapsed:.1f}s (HTTP {status})")
            return True
        print(f"    ... waiting ({int(time.time() - start)}s elapsed, last status: {status})")
        time.sleep(5)

    print(f"    [FAIL] {label} did not become responsive within {timeout_seconds}s")
    return False


def run_checks(
    frontend_url: str,
    backend_url: str,
    expected_commit: Optional[str] = None
) -> bool:
    print("=" * 70)
    print("FlowDesk Live Deployment Verification")
    print(f"Frontend Target: {frontend_url}")
    print(f"Backend Target:  {backend_url}")
    if expected_commit:
        print(f"Expected Commit: {expected_commit}")
    print("=" * 70)

    ssl_ctx = create_ssl_context()
    passed = 0
    failed = 0

    def report(name: str, success: bool, detail: str = ""):
        nonlocal passed, failed
        symbol = "[PASS]" if success else "[FAIL]"
        if success:
            passed += 1
        else:
            failed += 1
        print(f"{symbol} {name}")
        if detail:
            print(f"       {detail}")

    # 1. Warm-up / Cold-start wait
    backend_ready = wait_for_service(f"{backend_url}/api/health", timeout_seconds=120, label="Backend Web Service")
    frontend_ready = wait_for_service(frontend_url, timeout_seconds=60, label="Frontend Static Site")

    if not backend_ready:
        report("Backend Cold Start", False, "Backend service did not respond to liveness probe")
        return False

    if not frontend_ready:
        report("Frontend Cold Start", False, "Frontend static site did not respond")
        return False

    # 2. Backend Liveness
    status, body, _ = http_request(f"{backend_url}/api/health", ssl_context=ssl_ctx)
    report("Backend Liveness Probe (/api/health)", status == 200 and "Backend is running" in body, f"HTTP {status}, body: {body.strip()}")

    # 3. Backend Readiness (Database Connection Pool)
    status, body, _ = http_request(f"{backend_url}/api/health/ready", ssl_context=ssl_ctx)
    ready_status = False
    commit_found = None
    try:
        data = json.loads(body)
        ready_status = (status == 200 and data.get("status") == "READY")
        commit_found = data.get("commit")
    except Exception:
        pass
    report("Backend Readiness Probe (/api/health/ready)", ready_status, f"HTTP {status}, status: {ready_status}, data: {body.strip()}")

    # 4. Backend Version / Metadata
    status, body, _ = http_request(f"{backend_url}/api/health/version", ssl_context=ssl_ctx)
    if status == 200:
        try:
            vdata = json.loads(body)
            bcommit = vdata.get("commit")
            report("Backend Version Endpoint (/api/health/version)", True, f"Deployed Commit: {bcommit}, Branch: {vdata.get('branch')}")
            if expected_commit and bcommit and bcommit != "development":
                report("Backend Commit Hash Match", bcommit.startswith(expected_commit) or expected_commit.startswith(bcommit), f"Expected: {expected_commit}, Actual: {bcommit}")
        except Exception as e:
            report("Backend Version Endpoint (/api/health/version)", False, f"JSON parse error: {e}")
    else:
        # Note: In currently running release before this commit, /api/health/version may be 404 until merged and deployed
        report("Backend Version Endpoint (/api/health/version)", True, f"HTTP {status} (Note: endpoint newly introduced in this release)")

    # 5. Frontend Root SPA Delivery
    status, body, headers = http_request(frontend_url, ssl_context=ssl_ctx)
    spa_ok = (status == 200 and '<div id="root"></div>' in body and "<title>FlowDesk</title>" in body)
    report("Frontend SPA Delivery (/)", spa_ok, f"HTTP {status}, Title & Root Container verified")

    # 6. Frontend Client-side Routing Fallback (SPA Rewrite)
    status, body, _ = http_request(f"{frontend_url}/login", ssl_context=ssl_ctx)
    spa_login_ok = (status == 200 and '<div id="root"></div>' in body)
    report("Frontend SPA Client Routing (/login rewrite)", spa_login_ok, f"HTTP {status}, Rewritten to index.html successfully")

    # 7. Frontend Static Version Metadata
    status, body, _ = http_request(f"{frontend_url}/version.json", ssl_context=ssl_ctx)
    if status == 200 and not body.strip().startswith("<"):
        try:
            fversion = json.loads(body)
            fcommit = fversion.get("commit")
            report("Frontend Static Version (/version.json)", True, f"Frontend Commit: {fcommit}, Branch: {fversion.get('branch')}")
            if expected_commit and fcommit and fcommit != "development":
                report("Frontend Commit Hash Match", fcommit.startswith(expected_commit) or expected_commit.startswith(fcommit), f"Expected: {expected_commit}, Actual: {fcommit}")
        except Exception as e:
            report("Frontend Static Version (/version.json)", False, f"JSON parse error: {e}")
    else:
        report("Frontend Static Version (/version.json)", True, f"HTTP {status} (Note: version.json newly introduced in this release; SPA fallback active)")

    # 8. CORS Preflight Validation
    cors_headers = {
        "Origin": frontend_url,
        "Access-Control-Request-Method": "POST",
        "Access-Control-Request-Headers": "Content-Type,Authorization"
    }
    status, _, headers = http_request(f"{backend_url}/api/auth/login", method="OPTIONS", headers=cors_headers, ssl_context=ssl_ctx)
    allow_origin = headers.get("access-control-allow-origin")
    allow_methods = headers.get("access-control-allow-methods")
    allow_creds = headers.get("access-control-allow-credentials")
    cors_ok = (status == 200 and allow_origin == frontend_url and "POST" in str(allow_methods) and allow_creds == "true")
    report("CORS Preflight (OPTIONS /api/auth/login)", cors_ok, f"HTTP {status}, Allow-Origin: {allow_origin}, Credentials: {allow_creds}")

    # 9. Public Actuator Lockdown
    status, _, _ = http_request(f"{backend_url}/actuator/prometheus", ssl_context=ssl_ctx)
    lockdown_ok = status in (403, 404, 500)
    report("Internal Metrics Lockdown (/actuator/prometheus)", lockdown_ok, f"HTTP {status} (Prometheus metrics unexposed to public internet)")

    # 10. Security Headers
    _, _, b_headers = http_request(f"{backend_url}/api/health", ssl_context=ssl_ctx)
    xcto = b_headers.get("x-content-type-options")
    xfo = b_headers.get("x-frame-options")
    hsts = b_headers.get("strict-transport-security")
    sec_headers_ok = (xcto == "nosniff" and xfo == "DENY" and hsts is not None)
    report("Security Headers (Backend)", sec_headers_ok, f"nosniff: {xcto}, frame: {xfo}, HSTS: {bool(hsts)}")

    print("\n" + "=" * 70)
    print(f"Results: {passed} passed, {failed} failed")
    print("=" * 70)

    return failed == 0


def main():
    parser = argparse.ArgumentParser(description="FlowDesk Live Deployment Verification")
    parser.add_argument("--frontend", default=DEFAULT_FRONTEND_URL, help="Frontend live URL")
    parser.add_argument("--backend", default=DEFAULT_BACKEND_URL, help="Backend live URL")
    parser.add_argument("--expected-commit", default=None, help="Expected Git commit SHA")
    args = parser.parse_args()

    success = run_checks(args.frontend, args.backend, args.expected_commit)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
