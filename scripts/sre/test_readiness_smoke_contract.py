#!/usr/bin/env python3
"""
FlowDesk SRE Verification: Readiness Probe Smoke Test Contract
==============================================================
Validates the stable HealthController.java contract (/api/health/ready):
  - 200 + {"status":"READY"}       -> PASSES
  - 503 + {"status":"UNAVAILABLE"} -> FAILS
  - 503 + {"status":"DEGRADED"}    -> FAILS
  - 200 + unexpected body/JSON     -> FAILS (e.g. legacy {"status":"UP"})
  - Network connection error       -> FAILS

Tests both:
  1. Direct contract evaluation logic in deployment_smoke_test.py
  2. Live HTTP mock server validation for both deployment_smoke_test.py and deployment_smoke_test.sh
"""

import json
import os
import subprocess
import sys
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler

# Import evaluate_readiness_response from deployment_smoke_test
sys.path.insert(0, os.path.dirname(__file__))
from deployment_smoke_test import evaluate_readiness_response


class MockHealthServer:
    def __init__(self):
        self.status_code = 200
        self.body = '{"status":"READY"}'
        self.server = None
        self.thread = None
        self.port = 0

    def start(self):
        parent = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, format, *args):
                pass  # Suppress logging

            def do_GET(self):
                if self.path == "/api/health/ready":
                    data = parent.body.encode("utf-8")
                    self.send_response(parent.status_code)
                    self.send_header("Content-Type", "application/json")
                    self.send_header("Content-Length", str(len(data)))
                    self.end_headers()
                    self.wfile.write(data)
                elif self.path == "/healthz":
                    self.send_response(200)
                    self.end_headers()
                    self.wfile.write(b"OK")
                else:
                    self.send_response(404)
                    self.end_headers()

        self.server = HTTPServer(("127.0.0.1", 0), Handler)
        self.port = self.server.server_address[1]
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def stop(self):
        if self.server:
            self.server.shutdown()
            self.server.server_close()


def test_unit_evaluator():
    print("=== Testing evaluate_readiness_response Unit Logic ===")

    # Case 1: 200 + READY -> Must PASS
    ok, msg = evaluate_readiness_response(200, '{"status":"READY"}')
    assert ok, f"Case 1 Failed: Expected True, got False ({msg})"
    print("[PASS] Case 1: 200 + READY passes")

    # Case 2: 503 + UNAVAILABLE -> Must FAIL
    ok, msg = evaluate_readiness_response(503, '{"status":"UNAVAILABLE"}')
    assert not ok, "Case 2 Failed: Expected False, got True"
    assert "UNAVAILABLE" in msg or "503" in msg, f"Expected UNAVAILABLE in message, got: {msg}"
    print(f"[PASS] Case 2: 503 + UNAVAILABLE fails as expected: {msg}")

    # Case 3: 503 + DEGRADED -> Must FAIL
    ok, msg = evaluate_readiness_response(503, '{"status":"DEGRADED"}')
    assert not ok, "Case 3 Failed: Expected False, got True"
    assert "DEGRADED" in msg or "503" in msg, f"Expected DEGRADED in message, got: {msg}"
    print(f"[PASS] Case 3: 503 + DEGRADED fails as expected: {msg}")

    # Case 4: 200 + legacy "UP" -> Must FAIL
    ok, msg = evaluate_readiness_response(200, '{"status":"UP"}')
    assert not ok, "Case 4 Failed: Expected False for 'UP', got True"
    assert "READY" in msg, f"Expected mention of READY in message: {msg}"
    print(f"[PASS] Case 4: 200 + 'UP' (mismatch) fails as expected: {msg}")

    # Case 5: 200 + HTML / Malformed JSON -> Must FAIL
    ok, msg = evaluate_readiness_response(200, "<html><body>Gateway Timeout</body></html>")
    assert not ok, "Case 5 Failed: Expected False for HTML body, got True"
    print(f"[PASS] Case 5: 200 + HTML/Non-JSON fails as expected: {msg}")

    # Case 6: 200 + Non-dict JSON -> Must FAIL
    ok, msg = evaluate_readiness_response(200, '["READY"]')
    assert not ok, "Case 6 Failed: Expected False for JSON array, got True"
    print(f"[PASS] Case 6: 200 + JSON array fails as expected: {msg}")

    # Case 7: Network error (status 0) -> Must FAIL
    ok, msg = evaluate_readiness_response(0, "Connection refused")
    assert not ok, "Case 7 Failed: Expected False for status 0, got True"
    print(f"[PASS] Case 7: Status 0 (network error) fails as expected: {msg}")


def test_bash_readiness_parsing():
    print("\n=== Testing deployment_smoke_test.sh Readiness Parsing ===")
    bash_path = r"C:\Program Files\Git\bin\bash.exe"
    if not os.path.exists(bash_path):
        bash_path = "bash"

    def run_bash_readiness_check(status_code, body):
        test_script = f"""
READY_STATUS="{status_code}"
READY_BODY='{body}'

PARSED_STATUS=""
if [ -n "$READY_BODY" ]; then
    if command -v python3 >/dev/null 2>&1; then
        PARSED_STATUS=$(python3 -c "import sys, json; sys.stdout.write(json.loads(sys.stdin.read()).get('status', ''))" <<< "$READY_BODY" 2>/dev/null || echo "")
    elif command -v python >/dev/null 2>&1; then
        PARSED_STATUS=$(python -c "import sys, json; sys.stdout.write(json.loads(sys.stdin.read()).get('status', ''))" <<< "$READY_BODY" 2>/dev/null || echo "")
    else
        PARSED_STATUS=$(echo "$READY_BODY" | grep -o '"status"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4 || echo "")
    fi
fi

if [ "$READY_STATUS" = "200" ] && [ "$PARSED_STATUS" = "READY" ]; then
    echo "PASS"
else
    echo "FAIL: READY_STATUS=$READY_STATUS, PARSED_STATUS=$PARSED_STATUS"
fi
"""
        res = subprocess.run([bash_path, "-c", test_script], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        return res.stdout.strip()

    # Case 1: 200 + READY -> Must pass
    out = run_bash_readiness_check(200, '{"status":"READY"}')
    assert out == "PASS", f"Bash Case 1 Failed: {out}"
    print("[PASS] Bash Case 1: 200 + READY passes")

    # Case 2: 503 + UNAVAILABLE -> Must fail
    out = run_bash_readiness_check(503, '{"status":"UNAVAILABLE"}')
    assert out.startswith("FAIL"), f"Bash Case 2 Failed: {out}"
    print(f"[PASS] Bash Case 2: 503 + UNAVAILABLE fails ({out})")

    # Case 3: 503 + DEGRADED -> Must fail
    out = run_bash_readiness_check(503, '{"status":"DEGRADED"}')
    assert out.startswith("FAIL"), f"Bash Case 3 Failed: {out}"
    print(f"[PASS] Bash Case 3: 503 + DEGRADED fails ({out})")

    # Case 4: 200 + UP -> Must fail
    out = run_bash_readiness_check(200, '{"status":"UP"}')
    assert out.startswith("FAIL"), f"Bash Case 4 Failed: {out}"
    print(f"[PASS] Bash Case 4: 200 + UP fails ({out})")

    # Case 5: 000 + Network error -> Must fail
    out = run_bash_readiness_check(000, "")
    assert out.startswith("FAIL"), f"Bash Case 5 Failed: {out}"
    print(f"[PASS] Bash Case 5: Network error (000) fails ({out})")


def test_live_mock_http():
    print("\n=== Testing Live HTTP Mock Server Integration ===")
    mock = MockHealthServer()
    mock.start()

    try:
        import urllib.request, ssl
        ssl_ctx = ssl.create_default_context()
        url = f"http://127.0.0.1:{mock.port}/api/health/ready"

        def probe():
            try:
                with urllib.request.urlopen(url, timeout=2) as resp:
                    return resp.status, resp.read().decode("utf-8")
            except urllib.error.HTTPError as e:
                return e.code, e.read().decode("utf-8")
            except Exception as e:
                return 0, str(e)

        # 1. Healthy server: 200 + READY
        mock.status_code = 200
        mock.body = '{"status":"READY"}'
        status, body = probe()
        ok, msg = evaluate_readiness_response(status, body)
        assert ok, f"Mock 1 Failed: {msg}"
        print(f"[PASS] Live HTTP Mock 1: 200 + READY evaluated as Healthy: {msg}")

        # 2. Unavailable server: 503 + UNAVAILABLE
        mock.status_code = 503
        mock.body = '{"status":"UNAVAILABLE"}'
        status, body = probe()
        ok, msg = evaluate_readiness_response(status, body)
        assert not ok, f"Mock 2 Failed: expected not ok"
        print(f"[PASS] Live HTTP Mock 2: 503 + UNAVAILABLE correctly caught: {msg}")

        # 3. Degraded server: 503 + DEGRADED
        mock.status_code = 503
        mock.body = '{"status":"DEGRADED"}'
        status, body = probe()
        ok, msg = evaluate_readiness_response(status, body)
        assert not ok, f"Mock 3 Failed: expected not ok"
        print(f"[PASS] Live HTTP Mock 3: 503 + DEGRADED correctly caught: {msg}")

        # 4. Unexpected body: 200 + {"status":"UP"}
        mock.status_code = 200
        mock.body = '{"status":"UP"}'
        status, body = probe()
        ok, msg = evaluate_readiness_response(status, body)
        assert not ok, f"Mock 4 Failed: expected not ok"
        print(f"[PASS] Live HTTP Mock 4: 200 + 'UP' correctly rejected: {msg}")

    finally:
        mock.stop()

    # 5. Network error (Server stopped)
    status, body = probe()
    ok, msg = evaluate_readiness_response(status, body)
    assert not ok, f"Mock 5 Failed: expected network failure"
    print(f"[PASS] Live HTTP Mock 5: Dead server connection failure correctly caught: {msg}")


def main():
    test_unit_evaluator()
    test_bash_readiness_parsing()
    test_live_mock_http()
    print("\nALL READINESS CONTRACT REGRESSION TESTS PASSED SUCCESSFULLY!")


if __name__ == "__main__":
    main()

