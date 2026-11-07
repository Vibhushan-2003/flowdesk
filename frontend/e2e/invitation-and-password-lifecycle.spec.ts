import { test, expect } from '@playwright/test'
import {
  MOCK_EMPLOYEE,
  setupApiMocks,
} from './fixtures/mockApi'

test.describe('Invitation Activation & Password Recovery E2E Journeys', () => {
  test.beforeEach(async ({ page }) => {
    await setupApiMocks(page, MOCK_EMPLOYEE)
  })

  test('Accept Invitation: valid token renders form, strips token from URL, validates password, and activates account', async ({ page }) => {
    let acceptRequestPayload: Record<string, unknown> | null = null

    await page.route('**/api/auth/accept-invitation', async (route) => {
      acceptRequestPayload = (await route.request().postDataJSON()) as Record<string, unknown>
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'Invitation accepted successfully. You can now log in.',
        }),
      })
    })

    await page.goto('/accept-invitation?token=secure-entropy-token-xyz123')

    // 1. Heading and form elements are visible
    await expect(page.getByRole('heading', { name: /set up your account/i })).toBeVisible()
    const passwordInput = page.locator('#accept-new-password')
    const confirmInput = page.locator('#accept-confirm-password')
    const submitBtn = page.getByRole('button', { name: /activate account/i })

    await expect(passwordInput).toBeVisible()
    await expect(confirmInput).toBeVisible()
    await expect(submitBtn).toBeVisible()

    // 2. Token must be stripped from address bar to prevent leaking in history/referrers
    await expect.poll(() => page.url()).not.toContain('secure-entropy-token-xyz123')

    // 3. Validation: passwords mismatch
    await passwordInput.fill('ValidPassword123!')
    await confirmInput.fill('DifferentPassword123!')
    await submitBtn.click()
    await expect(page.getByRole('alert')).toContainText(/passwords do not match/i)

    // 4. Validation: password too short (< 8 chars)
    await passwordInput.fill('Short1!')
    await confirmInput.fill('Short1!')
    await submitBtn.click()
    await expect(page.getByRole('alert')).toContainText(/must be between 8 and 72 characters/i)

    // 5. Successful submission
    await passwordInput.fill('SecureEnterprisePass123!')
    await confirmInput.fill('SecureEnterprisePass123!')
    await submitBtn.click()

    // Verify payload sent with original token
    expect(acceptRequestPayload).not.toBeNull()
    expect(acceptRequestPayload).toMatchObject({
      token: 'secure-entropy-token-xyz123',
      newPassword: 'SecureEnterprisePass123!',
      confirmPassword: 'SecureEnterprisePass123!',
    })

    // Verify success confirmation notice
    await expect(page.getByText(/account activated successfully/i)).toBeVisible()

    // Verify auto-redirect or link to login
    await expect(page.getByRole('link', { name: /sign in now/i })).toBeVisible()
  })

  test('Accept Invitation: missing or empty token displays clear error card with return to login', async ({ page }) => {
    await page.goto('/accept-invitation')

    await expect(page.getByRole('heading', { name: /invalid invitation link/i })).toBeVisible()
    await expect(page.getByText(/this account activation link is invalid/i)).toBeVisible()
    await expect(page.getByRole('link', { name: /return to sign in/i })).toBeVisible()
  })

  test('Forgot Password: submitting email shows generic anti-enumeration success message', async ({ page }) => {
    let forgotRequestPayload: Record<string, unknown> | null = null

    await page.route('**/api/auth/forgot-password', async (route) => {
      forgotRequestPayload = (await route.request().postDataJSON()) as Record<string, unknown>
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'If an account with that email exists, password reset instructions have been sent.',
        }),
      })
    })

    await page.goto('/forgot-password')

    await expect(page.getByRole('heading', { name: /forgot password/i })).toBeVisible()
    await page.getByLabel(/email/i).fill('user@flowdesk.test')
    await page.getByRole('button', { name: /send reset instructions/i }).click()

    expect(forgotRequestPayload).toMatchObject({
      email: 'user@flowdesk.test',
    })

    await expect(page.getByRole('status')).toContainText(/if an account with that email exists, password reset instructions have been sent/i)
    await expect(page.getByText(/expire in 15 minutes/i)).toBeVisible()
  })

  test('Reset Password: valid token renders form, strips token from URL, and resets password', async ({ page }) => {
    let resetRequestPayload: Record<string, unknown> | null = null

    await page.route('**/api/auth/reset-password', async (route) => {
      resetRequestPayload = (await route.request().postDataJSON()) as Record<string, unknown>
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'Password reset successfully. You can now log in with your new password.',
        }),
      })
    })

    await page.goto('/reset-password?token=mock-reset-token-777')

    await expect(page.getByRole('heading', { name: /reset (your )?password/i })).toBeVisible()
    // Token stripped from URL
    await expect.poll(() => page.url()).not.toContain('mock-reset-token-777')

    const passwordInput = page.locator('#reset-new-password')
    const confirmInput = page.locator('#reset-confirm-password')
    const submitBtn = page.getByRole('button', { name: /reset password/i })

    await passwordInput.fill('BrandNewPassword123!')
    await confirmInput.fill('BrandNewPassword123!')
    await submitBtn.click()

    expect(resetRequestPayload).toMatchObject({
      token: 'mock-reset-token-777',
      newPassword: 'BrandNewPassword123!',
      confirmPassword: 'BrandNewPassword123!',
    })

    await expect(page.getByText(/password has been reset successfully/i)).toBeVisible()
  })

  test('Reset Password: missing token displays invalid link error card', async ({ page }) => {
    await page.goto('/reset-password')

    await expect(page.getByRole('heading', { name: /invalid reset link/i })).toBeVisible()
    await expect(page.getByRole('link', { name: /request a new reset link/i })).toBeVisible()
  })
})
