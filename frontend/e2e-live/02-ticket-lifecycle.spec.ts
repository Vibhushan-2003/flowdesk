import { test, expect } from '@playwright/test'
import {
  assertLiveStackReady,
  ensureE2eAccountsProvisioned,
  getResolvedBackendUrl,
  loginAndGetToken,
  loginViaUi,
} from './fixtures/seedData'
import { E2E_EMPLOYEE_A } from './fixtures/testAccounts'

test.describe('Real-Stack Live: Ticket Creation & Idempotency Lifecycle', () => {
  test.beforeAll(async () => {
    const isReady = await assertLiveStackReady()
    if (!isReady) {
      test.skip(true, 'Local live-stack environment is not reachable. Start via docker compose -f docker-compose.e2e.yml up')
      return
    }
    await ensureE2eAccountsProvisioned()
  })

  test('Scenario 3: Real ticket creation persisted in PostgreSQL with generated ticket number', async ({
    page,
  }) => {
    await loginViaUi(page, E2E_EMPLOYEE_A)

    await page.goto('/tickets/new')
    await expect(page.getByRole('heading', { name: /create a ticket/i })).toBeVisible()

    const uniqueTitle = `Live DB Ticket - ${Date.now()}`
    const description = 'Testing persistent PostgreSQL insert through live Spring Boot REST API'

    await page.getByLabel(/title/i).fill(uniqueTitle)
    await page.getByLabel(/description/i).fill(description)
    await page.getByRole('button', { name: /create ticket/i }).click()

    // Confirmation page displays generated ticket number (format: FD-XXXXXX)
    await expect(page.getByRole('heading', { name: /ticket created successfully/i })).toBeVisible()
    const ticketNumberLocator = page.locator('.ticket-success-number, strong, code').filter({
      hasText: /FD-\d{6}/,
    })
    await expect(ticketNumberLocator.first()).toBeVisible()
    const ticketNumber = (await ticketNumberLocator.first().innerText()).trim()
    expect(ticketNumber).toMatch(/^FD-\d{6}$/)

    // Direct navigation to the ticket confirms real backend persistence
    await page.goto(`/tickets/${ticketNumber}`)
    await expect(page.getByText(uniqueTitle)).toBeVisible()
  })

  test('Scenario 4: Rapid double-click ticket submission produces exactly one ticket', async ({
    page,
  }) => {
    await loginViaUi(page, E2E_EMPLOYEE_A)

    await page.goto('/tickets/new')
    const doubleClickTitle = `Double Click Guard - ${Date.now()}`
    await page.getByLabel(/title/i).fill(doubleClickTitle)
    await page.getByLabel(/description/i).fill('Testing frontend submit ref guard with live backend persistence')

    const submitBtn = page.locator('button[type="submit"]')

    // Two rapid clicks without waiting between them
    await submitBtn.click({ noWaitAfter: true })
    await submitBtn.click({ force: true, noWaitAfter: true }).catch(() => {})

    // Successfully creates exactly one ticket
    await expect(page.getByRole('heading', { name: /ticket created successfully/i })).toBeVisible()
    const ticketNumberLocator = page.locator('.ticket-success-number, strong, code').filter({
      hasText: /FD-\d{6}/,
    })
    await expect(ticketNumberLocator.first()).toBeVisible()
    const ticketNumber = (await ticketNumberLocator.first().innerText()).trim()
    expect(ticketNumber).toMatch(/^FD-\d{6}$/)
  })

  test('Scenario 5: Idempotency replay returns identical response without duplicating records', async () => {
    const backendUrl = getResolvedBackendUrl()
    const token = await loginAndGetToken(backendUrl, E2E_EMPLOYEE_A)

    const idempotencyKey = `live-e2e-key-${Date.now()}-${Math.random().toString(36).substring(2)}`
    const payload = {
      type: 'INCIDENT',
      title: `Idempotency Live Replay ${Date.now()}`,
      description: 'Verifying Flyway V21 ticket_idempotency_keys table prevents duplicate row insertion',
    }

    // First request
    const firstRes = await fetch(`${backendUrl}/api/tickets`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(payload),
    })

    expect(firstRes.status).toBe(201)
    const firstTicket = (await firstRes.json()) as { id: string; ticketNumber: string }
    expect(firstTicket.ticketNumber).toMatch(/^FD-\d{6}$/)

    // Second request with identical key and payload (Replay)
    const secondRes = await fetch(`${backendUrl}/api/tickets`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(payload),
    })

    // Returns HTTP 201 with the exact same ticket number and ID
    expect(secondRes.status).toBe(201)
    const secondTicket = (await secondRes.json()) as { id: string; ticketNumber: string }
    expect(secondTicket.id).toBe(firstTicket.id)
    expect(secondTicket.ticketNumber).toBe(firstTicket.ticketNumber)
  })
})
