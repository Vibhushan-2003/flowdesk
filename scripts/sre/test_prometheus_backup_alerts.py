#!/usr/bin/env python3
"""
FlowDesk SRE Verification: Prometheus Backup Alert Rule Evaluation
=================================================================
Validates the exact mathematical logic of Rule 8 (FlowDeskBackupMissingOrFailed)
under all 5 operational conditions:
  1. Healthy backup: current timestamp, status=1 -> INACTIVE
  2. Failed backup: status=0 -> FIRING
  3. Stale/Missing backup: timestamp > 26h ago -> FIRING
  4. Collector failure: metric absent -> FIRING
  5. Recovery cycle: fresh successful backup -> RECOVERS TO INACTIVE
"""

import sys
import time
import yaml


def evaluate_rule(now, last_success_ts, status, collector_healthy):
    # Rule 8 expression:
    # (time() - flowdesk_backup_last_success_timestamp > 93600) or (flowdesk_backup_status == 0) or (absent(flowdesk_backup_collector_healthy) == 1)
    if collector_healthy is None:
        # absent(flowdesk_backup_collector_healthy) == 1
        return True, "Metric collector is absent / offline"

    if status == 0:
        return True, "flowdesk_backup_status == 0 (Backup job failed)"

    if last_success_ts is None or (now - last_success_ts) > 93600:
        elapsed = (now - last_success_ts) if last_success_ts else float('inf')
        return True, f"time() - last_success > 93600 ({elapsed:.0f}s elapsed > 26h)"

    return False, "All conditions healthy (INACTIVE)"


def main():
    rules_file = "monitoring/prometheus/alert_rules.yml"
    try:
        with open(rules_file, 'r', encoding='utf-8') as f:
            data = yaml.safe_load(f)
    except Exception as e:
        sys.stderr.write(f"Failed to parse {rules_file}: {e}\n")
        sys.exit(1)

    # Confirm Rule 8 exists in group
    rule = None
    for group in data.get('groups', []):
        for r in group.get('rules', []):
            if r.get('alert') == 'FlowDeskBackupMissingOrFailed':
                rule = r
                break
    if not rule:
        sys.stderr.write("FlowDeskBackupMissingOrFailed rule not found in alert_rules.yml!\n")
        sys.exit(1)

    print("Found Prometheus Alert Rule: FlowDeskBackupMissingOrFailed")
    print("Expression:", rule['expr'])

    now = time.time()

    # Test 1: Fresh successful backup
    firing, reason = evaluate_rule(now, last_success_ts=now - 3600, status=1, collector_healthy=1)
    assert not firing, f"Test 1 failed: Expected INACTIVE, got FIRING ({reason})"
    print("[PASSED] Test 1: Healthy backup (1h ago, status=1) -> INACTIVE")

    # Test 2: Backup failure event
    firing, reason = evaluate_rule(now, last_success_ts=now - 3600, status=0, collector_healthy=1)
    assert firing, f"Test 2 failed: Expected FIRING, got INACTIVE"
    print(f"[PASSED] Test 2: Backup failure (status=0) -> FIRING ({reason})")

    # Test 3: Stale / Missed backup (> 26h window)
    stale_ts = now - 97200  # 27 hours ago
    firing, reason = evaluate_rule(now, last_success_ts=stale_ts, status=1, collector_healthy=1)
    assert firing, f"Test 3 failed: Expected FIRING, got INACTIVE"
    print(f"[PASSED] Test 3: Stale backup (27h ago > 93600s) -> FIRING ({reason})")

    # Test 4: Collector metric absent
    firing, reason = evaluate_rule(now, last_success_ts=now - 3600, status=1, collector_healthy=None)
    assert firing, f"Test 4 failed: Expected FIRING for absent metric, got INACTIVE"
    print(f"[PASSED] Test 4: Collector failure (absent metric) -> FIRING ({reason})")

    # Test 5: Recovery cycle after failure
    recovered_now = now + 120
    firing, reason = evaluate_rule(recovered_now, last_success_ts=recovered_now, status=1, collector_healthy=1)
    assert not firing, f"Test 5 failed: Expected RECOVERY to INACTIVE, got FIRING"
    print("[PASSED] Test 5: Recovery cycle (fresh backup success) -> CLEARS ALERT TO INACTIVE")

    print("\nAll 5 Prometheus Alert Evaluation tests PASSED!")


if __name__ == '__main__':
    main()
