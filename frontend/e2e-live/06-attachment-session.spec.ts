import { test, expect } from '@playwright/test'
import {
  assertLiveStackReady,
  ensureE2eAccountsProvisioned,
  getResolvedBackendUrl,
  loginAndGetToken,
} from './fixtures/seedData'
import { E2E_EMPLOYEE_A } from './fixtures/testAccounts'

test.describe('Real-Stack Live: Attachments & Session Continuity', () => {
  test.beforeAll(async () => {
    const isReady = await assertLiveStackReady()
    if (!isReady) {
      test.skip(true, 'Local live-stack environment is not reachable. Start via docker compose -f docker-compose.e2e.yml up')
      return
    }
    await ensureE2eAccountsProvisioned()
  })

  test('Scenario 11: Real attachment upload and authorized download with byte integrity', async () => {
    const backendUrl = getResolvedBackendUrl()
    const token = await loginAndGetToken(backendUrl, E2E_EMPLOYEE_A)

    // 1. Create a ticket
    const ticketRes = await fetch(`${backendUrl}/api/tickets`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        type: 'INCIDENT',
        title: `Attachment Test Ticket ${Date.now()}`,
        description: 'Testing live attachment upload and download byte verification',
      }),
    })
    expect(ticketRes.status).toBe(201)
    const ticket = (await ticketRes.json()) as { ticketNumber: string }

    // 2. Upload an attachment file via Multipart
    const fileContent = `FlowDesk live attachment verification payload: ${Date.now()}`
    const blob = new Blob([fileContent], { type: 'text/plain' })
    const formData = new FormData()
    formData.append('file', blob, 'e2e-test-proof.txt')

    const uploadRes = await fetch(
      `${backendUrl}/api/tickets/${ticket.ticketNumber}/attachments`,
      {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: formData,
      },
    )
    expect(uploadRes.status).toBe(201)
    const attachmentData = (await uploadRes.json()) as { id: string; filename: string }
    expect(attachmentData.filename).toBe('e2e-test-proof.txt')

    // 3. Download the attachment and verify byte content matches exactly
    const downloadRes = await fetch(
      `${backendUrl}/api/tickets/${ticket.ticketNumber}/attachments/${attachmentData.id}/download`,
      {
        method: 'GET',
        headers: { Authorization: `Bearer ${token}` },
      },
    )
    expect(downloadRes.status).toBe(200)
    const downloadedText = await downloadRes.text()
    expect(downloadedText).toBe(fileContent)
  })

  test('Scenario 12: Session expiration detection triggers notice and enables safe reauthentication', async ({
    page,
  }) => {
    // 1. Seed an invalid/expired token in sessionStorage
    const malformedToken =
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTYiLCJleHAiOjE2MDAwMDAwMDB9.signature'

    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, malformedToken)

    // 2. Navigate to deep protected route (/tickets/new)
    await page.goto('/tickets/new')

    // 3. Client auth interceptor detects expired token and redirects to login with alert notice
    await expect(page).toHaveURL(/.*login/)
    await expect(page.getByRole('alert')).toBeVisible()
    await expect(page.getByRole('alert')).toContainText(/session has expired/i)

    // 4. Re-authenticating with valid credentials redirects back to requested deep path (/tickets/new)
    await page.getByLabel(/email/i).fill(E2E_EMPLOYEE_A.email)
    await page.locator('#login-password').fill(E2E_EMPLOYEE_A.password)
    await page.getByRole('button', { name: /sign in/i }).click()

    await expect(page).toHaveURL(/.*tickets\/new/)
    await expect(page.getByRole('heading', { name: /create a ticket/i })).toBeVisible()
  })
})
