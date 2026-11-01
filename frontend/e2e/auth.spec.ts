import { test, expect } from '@playwright/test'
import {
  MOCK_EMPLOYEE,
  createMockToken,
  setupApiMocks,
} from './fixtures/mockApi'

test.describe('Authentication & Session Continuity Journeys', () => {
  test.beforeEach(async ({ page }) => {
    await setupApiMocks(page, MOCK_EMPLOYEE)
  })

  test('Journey 1: Login with valid credentials and log out cleanly with safe redirect', async ({ page }) => {
    await page.goto('/login')

    // Verify safe redirect sanitization: external or protocol-relative target falls back to dashboard
    await page.evaluate(() => {
      window.history.replaceState({ from: '//evil.com/phish' }, '', '/login')
    })

    await expect(page.getByRole('heading', { name: /welcome back/i })).toBeVisible()

    await page.getByLabel(/email/i).fill(MOCK_EMPLOYEE.email)
    await page.locator('#login-password').fill('ValidPass123!')
    await page.getByRole('button', { name: /sign in/i }).click()

    // Navigates safely to dashboard, rejecting external redirect
    await expect(page).toHaveURL(/.*dashboard/)
    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible()

    // Log out
    await page.getByRole('button', { name: /sign out/i }).click()
    await expect(page).toHaveURL(/.*login/)
    await expect(page.getByRole('heading', { name: /welcome back/i })).toBeVisible()
  })

  test('Journey 2: Invalid credentials display error without clearing email', async ({ page }) => {
    await page.goto('/login')

    await page.getByLabel(/email/i).fill('wrong@flowdesk.test')
    await page.locator('#login-password').fill('BadPassword123!')
    await page.getByRole('button', { name: /sign in/i }).click()

    await expect(page.getByRole('alert')).toContainText(/invalid email or password/i)
    await expect(page.getByLabel(/email/i)).toHaveValue('wrong@flowdesk.test')
  })

  test('Journey 3: Accessing protected route without authentication redirects to login', async ({ page }) => {
    await page.goto('/dashboard')

    await expect(page).toHaveURL(/.*login/)
    await expect(page.getByRole('heading', { name: /welcome back/i })).toBeVisible()
  })

  test('Journey 4: Expired session triggers clear notification on login page and preserves target destination', async ({
    page,
  }) => {
    // Seed expired token in sessionStorage
    const expiredToken = createMockToken(MOCK_EMPLOYEE, -300) // expired 5 minutes ago

    await page.addInitScript((token) => {
      sessionStorage.setItem('flowdesk_access_token', token)
    }, expiredToken)

    // Navigate to a deep protected path
    await page.goto('/tickets/new')

    // Expect automatic redirect to login with session expired notice
    await expect(page).toHaveURL(/.*login/)
    await expect(page.getByRole('alert')).toContainText(/session has expired/i)

    // Re-authenticate and verify redirection to the original target (/tickets/new)
    await page.getByLabel(/email/i).fill(MOCK_EMPLOYEE.email)
    await page.locator('#login-password').fill('ValidPass123!')
    await page.getByRole('button', { name: /sign in/i }).click()

    await expect(page).toHaveURL(/.*tickets\/new/)
  })
})

