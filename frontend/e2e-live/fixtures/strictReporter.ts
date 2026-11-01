import type { Reporter, FullResult, TestCase, TestResult } from '@playwright/test/reporter'

export default class StrictReleaseGateReporter implements Reporter {
  private passed = 0
  private skipped = 0
  private failed = 0

  onTestEnd(_test: TestCase, result: TestResult) {
    if (result.status === 'passed') this.passed++
    if (result.status === 'skipped') this.skipped++
    if (result.status === 'failed' || result.status === 'timedOut') this.failed++
  }

  onEnd(result: FullResult) {
    const isStrict =
      Boolean(process.env.CI) ||
      process.env.STRICT_LIVE_E2E?.trim().toLowerCase() === 'true'

    if (isStrict) {
      if (this.skipped > 0) {
        console.error(
          `\n[Strict Release Gate Violation]: ${this.skipped} live test(s) were skipped. CI release gate requires all required live scenarios to execute!`,
        )
        result.status = 'failed'
        process.exitCode = 1
      }
      if (this.passed < 12) {
        console.error(
          `\n[Strict Release Gate Violation]: Expected 12 passing live tests, but only ${this.passed} passed (failed: ${this.failed})!`,
        )
        result.status = 'failed'
        process.exitCode = 1
      }
    }
  }
}
