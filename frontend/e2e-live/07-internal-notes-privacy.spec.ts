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
  E2E_SUPPORT_ENGINEER,
} from './fixtures/testAccounts'

test.describe('Real-Stack Live: Internal Staff Notes Privacy & Visibility Enforcement', () => {
  test.beforeAll(async () => {
    const isReady = await assertLiveStackReady()
    if (!isReady) {
      test.skip(true, 'Local live-stack environment is not reachable. Start via docker compose -f docker-compose.e2e.yml up')
      return
    }
    await ensureE2eAccountsProvisioned()
  })

  test('Scenario 16: Staff internal notes privacy isolation, visibility enforcement, and requester notification suppression', async ({
    page,
  }) => {
    const backendUrl = getResolvedBackendUrl()

    // 1. Employee A creates an incident ticket via live backend API
    const tokenEmployee = await loginAndGetToken(backendUrl, E2E_EMPLOYEE_A)
    const ticketTitle = `Live DB Staff Note Privacy Test - ${Date.now()}`

    const createTicketRes = await fetch(`${backendUrl}/api/tickets`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${tokenEmployee}`,
      },
      body: JSON.stringify({
        type: 'INCIDENT',
        title: ticketTitle,
        description: 'Verifying real PostgreSQL isolation for confidential staff notes',
      }),
    })

    expect(createTicketRes.status).toBe(201)
    const ticketData = (await createTicketRes.json()) as { ticketNumber: string }
    const ticketNumber = ticketData.ticketNumber
    expect(ticketNumber).toMatch(/^FD-\d{6}$/)

    // 2. Support Engineer claims the ticket
    const tokenSupport = await loginAndGetToken(backendUrl, E2E_SUPPORT_ENGINEER)
    const claimRes = await fetch(`${backendUrl}/api/tickets/${ticketNumber}/claim`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${tokenSupport}`,
      },
    })
    expect(claimRes.status).toBe(200)

    // 3. Support Engineer logs in via UI and navigates to the assigned support ticket
    await loginViaUi(page, E2E_SUPPORT_ENGINEER)
    await page.goto(`/support/tickets/${ticketNumber}`)
    await expect(page.getByText(ticketTitle)).toBeVisible()

    // 4. Create an INTERNAL note via the UI toggle
    const internalCheckbox = page.locator('#comment-is-internal')
    await expect(internalCheckbox).toBeVisible()
    await internalCheckbox.check()

    // Verify submit button updates to show internal note submission
    const submitBtn = page.getByRole('button', { name: /add internal note/i })
    await expect(submitBtn).toBeVisible()

    const internalNoteText = `Confidential Staff Investigation Note - ${Date.now()}`
    await page.locator('#support-comment-body').fill(internalNoteText)
    await submitBtn.click()

    // 5. Confirm the internal note is visible in UI to authorized staff with badge
    await expect(page.getByText(internalNoteText)).toBeVisible()
    await expect(page.getByTestId('internal-note-badge')).toBeVisible()

    // 6. Confirm via Support API that the note was persisted with visibility: INTERNAL
    const staffCommentsRes = await fetch(
      `${backendUrl}/api/support/tickets/${ticketNumber}/comments`,
      {
        headers: { Authorization: `Bearer ${tokenSupport}` },
      },
    )
    expect(staffCommentsRes.status).toBe(200)
    const staffComments = (await staffCommentsRes.json()) as Array<{
      body: string
      visibility?: string
    }>
    const savedInternalComment = staffComments.find((c) => c.body === internalNoteText)
    expect(savedInternalComment).toBeDefined()
    expect(savedInternalComment?.visibility).toBe('INTERNAL')

    // 7. Support Engineer posts a PUBLIC reply
    await expect(internalCheckbox).not.toBeChecked()
    const publicReplyText = `Public customer assistance reply - ${Date.now()}`
    await page.locator('#support-comment-body').fill(publicReplyText)
    const publicSubmitBtn = page.getByRole('button', { name: /send message/i })
    await publicSubmitBtn.click()

    await expect(page.getByText(publicReplyText)).toBeVisible()

    // 8. Navigate to dashboard and sign out Support Engineer cleanly
    await page.goto('/dashboard')
    await page.getByRole('button', { name: /sign out/i }).click()
    await expect(page).toHaveURL(/.*login/)

    // 9. Verify Employee-facing API: internal note must NEVER appear; public reply must appear
    const employeeCommentsRes = await fetch(
      `${backendUrl}/api/tickets/${ticketNumber}/comments`,
      {
        headers: { Authorization: `Bearer ${tokenEmployee}` },
      },
    )
    expect(employeeCommentsRes.status).toBe(200)
    const employeeComments = (await employeeCommentsRes.json()) as Array<{
      body: string
      visibility?: string
    }>

    // Confirms internal note is absent from employee-facing API responses
    const leakedNoteInApi = employeeComments.find((c) => c.body === internalNoteText)
    expect(leakedNoteInApi).toBeUndefined()

    // Confirms PUBLIC support messages remain visible in employee-facing API
    const publicReplyInApi = employeeComments.find((c) => c.body === publicReplyText)
    expect(publicReplyInApi).toBeDefined()

    // 10. Authenticate ticket-owning Employee via UI
    await loginViaUi(page, E2E_EMPLOYEE_A)
    await page.goto(`/tickets/${ticketNumber}`)

    // Confirms PUBLIC support message is visible in browser UI
    await expect(page.getByText(publicReplyText)).toBeVisible()

    // Confirms internal note and internal note badge are ABSENT from browser UI
    await expect(page.getByText(internalNoteText)).not.toBeVisible()
    await expect(page.getByTestId('internal-note-badge')).not.toBeVisible()

    // 11. Confirms NO requester notification was generated for the internal note
    const notifsRes = await fetch(`${backendUrl}/api/notifications?page=0&size=50`, {
      headers: { Authorization: `Bearer ${tokenEmployee}` },
    })
    expect(notifsRes.status).toBe(200)
    const notifsData = (await notifsRes.json()) as {
      content: Array<{ ticketNumber?: string; message?: string }>
    }

    const leakedInternalNotification = notifsData.content.find(
      (n) => n.message && n.message.includes(internalNoteText),
    )
    expect(leakedInternalNotification).toBeUndefined()

    // Confirms public message DOES generate a notification
    const publicNotification = notifsData.content.find(
      (n) =>
        n.ticketNumber === ticketNumber &&
        n.message &&
        n.message.includes(publicReplyText),
    )
    expect(publicNotification).toBeDefined()
  })
})

