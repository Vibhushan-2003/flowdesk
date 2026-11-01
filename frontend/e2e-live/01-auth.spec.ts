import { test, expect } from '@playwright/test'
import {
  assertLiveStackReady,
  ensureE2eAccountsProvisioned,
} from './fixtures/seedData'
import { E2E_EMPLOYEE_A } from './fixtures/testAccounts'

test.describe('Real-Stack Live: Authentication & Access Control', () => {
  test.beforeAll(async () => {
    const isReady = await assertLiveStackReady()
    if (!isReady) {
      test.skip(true, 'Local live-stack environment is not reachable. Start via docker compose -f docker-compose.e2e.yml up')
      return
    }
    await ensureE2eAccountsProvisioned()
  })

  test('Scenario 1: Real employee login, protected-route access, and clean logout', async ({
    page,
  }) => {
    // Navigate to login
    await page.goto('/login')
    await expect(page.getByRole('heading', { name: /welcome back/i })).toBeVisible()

    // Real form fill
    await page.getByLabel(/email/i).fill(E2E_EMPLOYEE_A.email)
    await page.locator('#login-password').fill(E2E_EMPLOYEE_A.password)
    await page.getByRole('button', { name: /sign in/i }).click()

    // Real backend authentication completes and navigates to dashboard
    await expect(page).toHaveURL(/.*dashboard/)
    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible()

    // Verified real token saved in sessionStorage
    const storedToken = await page.evaluate(() =>
      sessionStorage.getItem('flowdesk_access_token'),
    )
    expect(storedToken).toBeTruthy()

    // Voluntary logout
    await page.getByRole('button', { name: /sign out/i }).click()
    await expect(page).toHaveURL(/.*login/)
    await expect(page.getByRole('heading', { name: /welcome back/i })).toBeVisible()

    // Stored token is cleared from storage
    const tokenAfterLogout = await page.evaluate(() =>
      sessionStorage.getItem('flowdesk_access_token'),
    )
    expect(tokenAfterLogout).toBeNull()
  })

  test('Scenario 2: Invalid credentials rejected with real error notice, protected route rejection', async ({
    page,
  }) => {
    await page.goto('/login')

    // Submit invalid password
    await page.getByLabel(/email/i).fill(E2E_EMPLOYEE_A.email)
    await page.locator('#login-password').fill('CompletelyWrongPass999!')
    await page.getByRole('button', { name: /sign in/i }).click()

    // Real HTTP 401 returns and triggers UI error alert
    await expect(page.getByRole('alert')).toBeVisible()
    await expect(page.getByRole('alert')).toContainText(/invalid/i)

    // Email remains preserved
    await expect(page.getByLabel(/email/i)).toHaveValue(E2E_EMPLOYEE_A.email)

    // Unauthenticated access to protected route redirects back to login
    await page.goto('/tickets/new')
    await expect(page).toHaveURL(/.*login/)
    await expect(page.getByRole('heading', { name: /welcome back/i })).toBeVisible()
  })
})
