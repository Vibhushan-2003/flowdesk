import { test, expect } from '@playwright/test'
import {
  MOCK_EMPLOYEE,
  MOCK_ENGINEER,
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

  test('Support staff internal notes: toggle creates internal note with badge, invisible to employee', async ({
    page,
  }) => {
    // 1. Support engineer view
    await setupApiMocks(page, MOCK_ENGINEER)
    const engToken = createMockToken(MOCK_ENGINEER)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, engToken)

    const mockTicket = {
      ticketId: '11111111-1111-1111-1111-111111111111',
      ticketNumber: 'FD-100200',
      type: 'INCIDENT',
      title: 'Database connection spike',
      description: 'Spike observed during peak hours',
      priority: 'HIGH',
      status: 'IN_PROGRESS',
      createdByUserId: MOCK_EMPLOYEE.id,
      createdByEmail: MOCK_EMPLOYEE.email,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      assignedAt: new Date().toISOString(),
      responseDueAt: new Date().toISOString(),
      resolutionDueAt: new Date().toISOString(),
      firstRespondedAt: null,
      resolvedAt: null,
      resolutionSummary: null,
      responseSlaStatus: 'MET',
      resolutionSlaStatus: 'PENDING',
    }

    const commentsList = [
      {
        commentId: 'c1111111-0000-0000-0000-000000000001',
        authorUserId: MOCK_EMPLOYEE.id,
        authorName: 'Employee User',
        body: 'Experiencing slow queries since this morning.',
        visibility: 'PUBLIC',
        createdAt: new Date(Date.now() - 3600000).toISOString(),
      },
    ]

    await page.route('**/api/support/tickets/FD-100200', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockTicket),
      })
    })

    await page.route('**/api/support/tickets/FD-100200/comments', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(commentsList),
        })
      } else if (route.request().method() === 'POST') {
        const body = route.request().postDataJSON() as { body: string; visibility?: string }
        const createdComment = {
          commentId: 'c2222222-0000-0000-0000-000000000002',
          authorUserId: MOCK_ENGINEER.id,
          authorName: 'Support Engineer',
          body: body.body,
          visibility: body.visibility || 'PUBLIC',
          createdAt: new Date().toISOString(),
        }
        commentsList.push(createdComment)
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(createdComment),
        })
      }
    })

    await page.route('**/api/tickets/FD-100200/attachments**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      })
    })

    await page.goto('/support/tickets/FD-100200')
    await expect(page.getByText('Database connection spike')).toBeVisible()

    // Verify existing public comment is visible
    await expect(page.getByText('Experiencing slow queries since this morning.')).toBeVisible()

    // Check internal note toggle
    const internalCheckbox = page.locator('#comment-is-internal')
    await expect(internalCheckbox).toBeVisible()
    await internalCheckbox.check()

    // Button label should now indicate internal note
    const submitBtn = page.getByRole('button', { name: /add internal note/i })
    await expect(submitBtn).toBeVisible()

    // Type private staff note
    await page.locator('#support-comment-body').fill('Confidential staff investigation: checked DB replicas.')
    await submitBtn.click()

    // Internal note should appear with badge
    await expect(page.getByText('Confidential staff investigation: checked DB replicas.')).toBeVisible()
    await expect(page.getByTestId('internal-note-badge')).toBeVisible()

    // 2. Employee view verification: only public comments must be returned
    const empPage = await page.context().newPage()
    await setupApiMocks(empPage, MOCK_EMPLOYEE)
    const empToken = createMockToken(MOCK_EMPLOYEE)
    await empPage.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, empToken)

    await empPage.route('**/api/tickets/FD-100200', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ...mockTicket,
          id: mockTicket.ticketId,
        }),
      })
    })

    // Employee API strictly returns only PUBLIC comments
    await empPage.route('**/api/tickets/FD-100200/comments', async (route) => {
      const publicOnly = commentsList.filter((c) => c.visibility === 'PUBLIC')
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(publicOnly),
      })
    })

    await empPage.route('**/api/tickets/FD-100200/attachments**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      })
    })

    await empPage.goto('/tickets/FD-100200')
    await expect(empPage.getByText('Database connection spike')).toBeVisible()
    await expect(empPage.getByText('Experiencing slow queries since this morning.')).toBeVisible()

    // Verify internal note is completely absent from employee view
    await expect(empPage.getByText('Confidential staff investigation: checked DB replicas.')).not.toBeVisible()
    await expect(empPage.getByTestId('internal-note-badge')).not.toBeVisible()

    await empPage.close()
  })
  test('Journey 10: Support Engineer toggles between Active Workload and Resolved History with URL sync and state isolation', async ({
    page,
  }) => {
    await setupApiMocks(page, MOCK_ENGINEER)
    const token = createMockToken(MOCK_ENGINEER)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    let activeEndpointCalled = 0
    let historyEndpointCalled = 0

    await page.route('**/api/support/tickets/my*', async (route) => {
      activeEndpointCalled++
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              id: 'ticket-active-1',
              ticketNumber: 'FD-100201',
              title: 'Active workload incident',
              description: 'Investigating high memory consumption.',
              type: 'INCIDENT',
              priority: 'HIGH',
              status: 'IN_PROGRESS',
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
              assignedAt: new Date().toISOString(),
              responseSlaStatus: 'HEALTHY',
              resolutionSlaStatus: 'HEALTHY',
            },
          ],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
        }),
      })
    })

    await page.route('**/api/support/tickets/history*', async (route) => {
      historyEndpointCalled++
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              id: 'ticket-hist-1',
              ticketNumber: 'FD-100202',
              title: 'Historical resolved database ticket',
              description: 'Completed query optimization.',
              type: 'INCIDENT',
              priority: 'MEDIUM',
              status: 'RESOLVED',
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
              assignedAt: new Date().toISOString(),
              responseSlaStatus: 'HEALTHY',
              resolutionSlaStatus: 'HEALTHY',
            },
          ],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
        }),
      })
    })

    // Navigate to assigned tickets workbench
    await page.goto('/support/tickets')
    await expect(page.getByRole('heading', { name: /my assigned tickets/i })).toBeVisible()

    // 1. Initially on Active Workload
    const activeTab = page.getByRole('tab', { name: /active workload/i })
    const historyTab = page.getByRole('tab', { name: /resolved history/i })
    await expect(activeTab).toHaveAttribute('aria-selected', 'true')
    await expect(historyTab).toHaveAttribute('aria-selected', 'false')
    await expect(page.getByText('FD-100201').first()).toBeVisible()
    expect(activeEndpointCalled).toBeGreaterThanOrEqual(1)

    // 2. Click Resolved History tab
    await historyTab.click()

    // URL must update to view=history
    await expect(page).toHaveURL(/view=history/)
    await expect(historyTab).toHaveAttribute('aria-selected', 'true')
    await expect(activeTab).toHaveAttribute('aria-selected', 'false')

    // History endpoint must be called and display historical ticket
    expect(historyEndpointCalled).toBeGreaterThanOrEqual(1)
    await expect(page.getByText('FD-100202').first()).toBeVisible()
    await expect(page.getByText('Historical resolved database ticket').first()).toBeVisible()

    // 3. Switch back to Active Workload tab
    await activeTab.click()
    await expect(page).not.toHaveURL(/view=history/)
    await expect(activeTab).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByText('FD-100201').first()).toBeVisible()

    // 4. Test empty history view with Return button
    await page.route('**/api/support/tickets/history*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [],
          page: 0,
          size: 10,
          totalElements: 0,
          totalPages: 0,
        }),
      })
    })

    await historyTab.click()
    await expect(page).toHaveURL(/view=history/)
    await expect(page.getByText(/no resolved or closed tickets/i)).toBeVisible()
    const viewWorkloadBtn = page.getByRole('button', { name: /view active workload/i })
    await expect(viewWorkloadBtn).toBeVisible()

    // Clicking "View Active Workload" restores active workload and strips view=history
    await viewWorkloadBtn.click()
    await expect(page).not.toHaveURL(/view=history/)
    await expect(activeTab).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByText('FD-100201').first()).toBeVisible()
  })
});
