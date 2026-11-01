import { test, expect } from '@playwright/test'
import {
  MOCK_EMPLOYEE,
  createMockToken,
  setupApiMocks,
} from './fixtures/mockApi'

test.describe('Ticket Creation & Reliability Journeys', () => {
  test.beforeEach(async ({ page }) => {
    await setupApiMocks(page, MOCK_EMPLOYEE)

    const token = createMockToken(MOCK_EMPLOYEE)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)
  })

  test('Journey 5: Employee creates ticket successfully with generated ticket number', async ({ page }) => {
    let capturedIdempotencyKey: string | null = null

    await page.route('**/api/tickets', async (route) => {
      if (route.request().method() === 'POST') {
        capturedIdempotencyKey = route.request().headers()['idempotency-key'] || null
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            id: '99999999-9999-9999-9999-999999999999',
            ticketNumber: 'FD-000099',
            type: 'INCIDENT',
            title: 'Laptop display flicker',
            description: 'Screen randomly flickers when connected to external monitor',
            priority: 'MEDIUM',
            status: 'OPEN',
            createdAt: new Date().toISOString(),
          }),
        })
      }
    })

    await page.goto('/tickets/new')
    await expect(page.getByRole('heading', { name: /create a ticket/i })).toBeVisible()

    await page.getByLabel(/title/i).fill('Laptop display flicker')
    await page.getByLabel(/description/i).fill('Screen randomly flickers when connected to external monitor')

    await page.getByRole('button', { name: /create ticket/i }).click()

    await expect(page.getByRole('heading', { name: /ticket created successfully/i })).toBeVisible()
    await expect(page.getByText('FD-000099', { exact: true })).toBeVisible()
    expect(capturedIdempotencyKey).toBeTruthy()
  })

  test('Journey 6: Rapid double-click on submit immediately disables button and dispatches single request', async ({
    page,
  }) => {
    let postCount = 0

    await page.route('**/api/tickets', async (route) => {
      if (route.request().method() === 'POST') {
        postCount++
        // Add a slight response delay to ensure in-flight state is testable
        await new Promise((resolve) => setTimeout(resolve, 300))
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            id: '88888888-8888-8888-8888-888888888888',
            ticketNumber: 'FD-000088',
            type: 'INCIDENT',
            title: 'Double click test',
            description: 'Verifying rapid clicks do not double-submit',
            priority: 'MEDIUM',
            status: 'OPEN',
            createdAt: new Date().toISOString(),
          }),
        })
      }
    })

    await page.goto('/tickets/new')

    await page.getByLabel(/title/i).fill('Double click test')
    await page.getByLabel(/description/i).fill('Verifying rapid clicks do not double-submit')

    const submitBtn = page.locator('button[type="submit"]')

    // Perform two clicks in rapid succession
    await submitBtn.click({ noWaitAfter: true })
    // The button must immediately enter disabled and submitting state
    await expect(submitBtn).toBeDisabled()
    await expect(submitBtn).toHaveText(/creating ticket.../i)

    // Second click should be rejected by the disabled attribute and synchronous ref guard
    await submitBtn.click({ force: true, noWaitAfter: true }).catch(() => {})

    // Wait for the single creation to complete
    await expect(page.getByRole('heading', { name: /ticket created successfully/i })).toBeVisible()
    expect(postCount).toBe(1)
  })

  test('Ticket draft is preserved across page navigation and isolated across users', async ({ page }) => {
    await page.goto('/tickets/new')

    await page.getByLabel(/title/i).fill('Draft preserved title')
    await page.getByLabel(/description/i).fill('Draft preserved description')

    // Navigate away to dashboard
    await page.goto('/dashboard')
    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible()

    // Navigate back to create ticket: draft is preserved for current user
    await page.goto('/tickets/new')
    await expect(page.getByLabel(/title/i)).toHaveValue('Draft preserved title')
    await expect(page.getByLabel(/description/i)).toHaveValue('Draft preserved description')

    // Cross-user isolation: if draft belongs to another user, it must not leak
    await page.evaluate(() => {
      sessionStorage.setItem(
        'flowdesk_ticket_draft',
        JSON.stringify({
          userId: 'other-user-uuid-999',
          type: 'INCIDENT',
          title: 'Leaked title from User B',
          description: 'Leaked description from User B',
        }),
      )
    })
    await page.goto('/dashboard')
    await page.goto('/tickets/new')
    await expect(page.getByLabel(/title/i)).toHaveValue('')
    await expect(page.getByLabel(/description/i)).toHaveValue('')

    // Voluntary sign out purges any pending draft
    await page.getByLabel(/title/i).fill('Draft to be purged on logout')
    await page.getByLabel(/description/i).fill('Description to be purged')
    await page.goto('/dashboard')
    await page.getByRole('button', { name: /sign out/i }).click()
    const storedDraft = await page.evaluate(() => sessionStorage.getItem('flowdesk_ticket_draft'))
    expect(storedDraft).toBeNull()
  })
})

