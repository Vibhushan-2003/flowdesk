import { test, expect } from '@playwright/test'
import {
  assertLiveStackReady,
  ensureE2eAccountsProvisioned,
  getResolvedBackendUrl,
  loginAndGetToken,
  loginViaUi,
} from './fixtures/seedData'
import {
  E2E_EMPLOYEE_A,
  E2E_EMPLOYEE_B,
} from './fixtures/testAccounts'

test.describe('Real-Stack Live: Cross-Employee Data Privacy & Isolation', () => {
  test.beforeAll(async () => {
    const isReady = await assertLiveStackReady()
    if (!isReady) {
      test.skip(true, 'Local live-stack environment is not reachable. Start via docker compose -f docker-compose.e2e.yml up')
      return
    }
    await ensureE2eAccountsProvisioned()
  })

  test("Scenario 8: Employee cannot view another employee's private ticket information", async ({
    page,
  }) => {
    const backendUrl = getResolvedBackendUrl()

    // 1. Employee B creates a confidential ticket via real API
    const tokenB = await loginAndGetToken(backendUrl, E2E_EMPLOYEE_B)

    const confidentialTitle = `Confidential Payroll Item - ${Date.now()}`
    const createRes = await fetch(`${backendUrl}/api/tickets`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${tokenB}`,
      },
      body: JSON.stringify({
        type: 'INCIDENT',
        title: confidentialTitle,
        description: 'Private payroll issue that must never be visible to other employees',
      }),
    })

    expect(createRes.status).toBe(201)
    const ticketB = (await createRes.json()) as { ticketNumber: string }

    // 2. Employee A logs in via UI
    await loginViaUi(page, E2E_EMPLOYEE_A)

    // 3. Employee A attempts to navigate directly to Employee B's private ticket
    await page.goto(`/tickets/${ticketB.ticketNumber}`)

    // 4. Verify backend returns 404/403 and UI displays access error / ticket not found alert
    await expect(page.getByRole('alert')).toBeVisible()
    await expect(page.getByRole('alert')).toContainText(/not found|unable to load/i)

    // Ensure confidential title is NEVER displayed to Employee A
    await expect(page.getByText(confidentialTitle)).not.toBeVisible()
  })
})
