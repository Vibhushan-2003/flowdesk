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

  test('Create Ticket Attachment Workflow: successfully uploads staged files and truthfully displays clean ClamAV scan status', async ({
    page,
  }) => {
    let ticketCreationCount = 0
    const uploadedAttachments: string[] = []

    await page.route('**/api/tickets', async (route) => {
      if (route.request().method() === 'POST') {
        ticketCreationCount++
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            id: '77777777-7777-7777-7777-777777777777',
            ticketNumber: 'FD-000077',
            type: 'INCIDENT',
            title: 'VPN timeout with packet trace',
            description: 'Packet trace attached showing TCP handshake timeout',
            priority: 'HIGH',
            status: 'OPEN',
            createdAt: new Date().toISOString(),
          }),
        })
      }
    })

    await page.route('**/api/tickets/FD-000077/attachments', async (route) => {
      if (route.request().method() === 'POST') {
        uploadedAttachments.push('uploaded')
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            id: `att-id-${uploadedAttachments.length}`,
            ticketNumber: 'FD-000077',
            filename: uploadedAttachments.length === 1 ? 'packet-trace.log' : 'network-topology.pdf',
            contentType: uploadedAttachments.length === 1 ? 'text/plain' : 'application/pdf',
            sizeBytes: 1024,
            uploadedByUserId: '11111111-1111-1111-1111-111111111111',
            uploadedByName: 'Test Employee',
            createdAt: new Date().toISOString(),
            scanStatus: 'CLEAN',
            scanDetails: 'ClamAV 1.4: OK',
          }),
        })
      }
    })

    await page.goto('/tickets/new')

    await page.getByLabel(/title/i).fill('VPN timeout with packet trace')
    await page.getByLabel(/description/i).fill('Packet trace attached showing TCP handshake timeout')

    // Stage 2 files
    await page.locator('#ticket-create-file-input').setInputFiles([
      {
        name: 'packet-trace.log',
        mimeType: 'text/plain',
        buffer: Buffer.from('SYN sent\nSYN-ACK timed out\nRetrying...'),
      },
      {
        name: 'network-topology.pdf',
        mimeType: 'application/pdf',
        buffer: Buffer.from('%PDF-1.4 network topology mockup %%EOF'),
      },
    ])

    // Verify staged files list preview
    await expect(page.getByText('packet-trace.log')).toBeVisible()
    await expect(page.getByText('network-topology.pdf')).toBeVisible()
    await expect(page.getByText('2 / 10 selected')).toBeVisible()

    // Submit button shows staged count
    const submitBtn = page.getByRole('button', { name: /create ticket with 2 attachments/i })
    await expect(submitBtn).toBeVisible()
    await submitBtn.click()

    // Confirmation screen shows ticket number and truthful ClamAV scan status
    await expect(page.getByRole('heading', { name: /ticket created successfully/i })).toBeVisible()
    await expect(page.getByText('FD-000077', { exact: true })).toBeVisible()
    await expect(page.getByText(/Attached Files \(2 of 2 uploaded\)/i)).toBeVisible()
    await expect(page.getByText('Clean (Verified)').first()).toBeVisible()

    expect(ticketCreationCount).toBe(1)
    expect(uploadedAttachments.length).toBe(2)
  })

  test('Create Ticket Attachment Workflow: partial upload failure displays retry button and retries without duplicate ticket creation', async ({
    page,
  }) => {
    let ticketCreationCount = 0
    let uploadAttemptCount = 0

    await page.route('**/api/tickets', async (route) => {
      if (route.request().method() === 'POST') {
        ticketCreationCount++
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            id: '66666666-6666-6666-6666-666666666666',
            ticketNumber: 'FD-000066',
            type: 'INCIDENT',
            title: 'Partial failure test',
            description: 'Testing resilient retry on network failure',
            priority: 'MEDIUM',
            status: 'OPEN',
            createdAt: new Date().toISOString(),
          }),
        })
      }
    })

    await page.route('**/api/tickets/FD-000066/attachments', async (route) => {
      if (route.request().method() === 'POST') {
        uploadAttemptCount++
        if (uploadAttemptCount === 1) {
          // First file: valid.pdf succeeds
          await route.fulfill({
            status: 201,
            contentType: 'application/json',
            body: JSON.stringify({
              id: 'att-66-1',
              ticketNumber: 'FD-000066',
              filename: 'valid.pdf',
              contentType: 'application/pdf',
              sizeBytes: 512,
              uploadedByUserId: '11111111-1111-1111-1111-111111111111',
              uploadedByName: 'Test Employee',
              createdAt: new Date().toISOString(),
              scanStatus: 'CLEAN',
            }),
          })
        } else if (uploadAttemptCount === 2) {
          // Second file: fail.txt fails on initial attempt
          await route.fulfill({
            status: 500,
            contentType: 'application/json',
            body: JSON.stringify({
              message: 'Temporary storage gateway timeout',
            }),
          })
        } else {
          // Third attempt (retry of fail.txt): succeeds
          await route.fulfill({
            status: 201,
            contentType: 'application/json',
            body: JSON.stringify({
              id: 'att-66-2',
              ticketNumber: 'FD-000066',
              filename: 'fail.txt',
              contentType: 'text/plain',
              sizeBytes: 256,
              uploadedByUserId: '11111111-1111-1111-1111-111111111111',
              uploadedByName: 'Test Employee',
              createdAt: new Date().toISOString(),
              scanStatus: 'CLEAN',
            }),
          })
        }
      }
    })

    await page.goto('/tickets/new')

    await page.getByLabel(/title/i).fill('Partial failure test')
    await page.getByLabel(/description/i).fill('Testing resilient retry on network failure')

    // Stage 2 files: valid.pdf and fail.txt
    await page.locator('#ticket-create-file-input').setInputFiles([
      {
        name: 'valid.pdf',
        mimeType: 'application/pdf',
        buffer: Buffer.from('%PDF-1.4 test valid %%EOF'),
      },
      {
        name: 'fail.txt',
        mimeType: 'text/plain',
        buffer: Buffer.from('Diagnostics details to retry'),
      },
    ])

    await page.getByRole('button', { name: /create ticket with 2 attachments/i }).click()

    // Confirmation panel appears for ticket FD-000066
    await expect(page.getByRole('heading', { name: /ticket created successfully/i })).toBeVisible()
    await expect(page.getByText('FD-000066', { exact: true })).toBeVisible()

    // Shows partial failure notice and retry button for fail.txt
    await expect(page.getByText(/Attachment Notice/i)).toBeVisible()
    await expect(page.getByText('Upload Failed')).toBeVisible()
    const retryBtn = page.getByRole('button', { name: /retry/i })
    await expect(retryBtn).toBeVisible()

    // Click retry
    await retryBtn.click()

    // After retry succeeds, Upload Failed badge is replaced by Clean (Verified) and notice disappears
    await expect(page.getByText('Clean (Verified)').nth(1)).toBeVisible()
    await expect(page.getByText(/Attachment Notice/i)).not.toBeVisible()

    // CRITICAL ASSERTION: Ticket creation was dispatched EXACTLY once! NO duplicate ticket created!
    expect(ticketCreationCount).toBe(1)
    expect(uploadAttemptCount).toBe(3)
  })

  test('Create Ticket Attachment Workflow: client-side validation blocks unsupported formats, oversized files, and empty files', async ({
    page,
  }) => {
    await page.goto('/tickets/new')

    // 1. Unsupported extension (.exe)
    await page.locator('#ticket-create-file-input').setInputFiles([
      {
        name: 'malware.exe',
        mimeType: 'application/x-msdownload',
        buffer: Buffer.from('MZ binary'),
      },
    ])
    await expect(page.getByText(/File "malware.exe" has an unsupported format/i)).toBeVisible()

    // 2. Empty (0-byte) file
    await page.locator('#ticket-create-file-input').setInputFiles([
      {
        name: 'empty.log',
        mimeType: 'text/plain',
        buffer: Buffer.from(''),
      },
    ])
    await expect(page.getByText(/File "empty.log" is empty \(0 bytes\)/i)).toBeVisible()

    // 3. Oversized file (> 10MB)
    const bigBuffer = Buffer.alloc(10 * 1024 * 1024 + 1024) // 10MB + 1KB
    await page.locator('#ticket-create-file-input').setInputFiles([
      {
        name: 'large_dump.log',
        mimeType: 'text/plain',
        buffer: bigBuffer,
      },
    ])
    await expect(page.getByText(/File "large_dump.log" exceeds the maximum limit of 10 MB/i)).toBeVisible()

    // None of the invalid files should be staged
    await expect(page.getByText('0 / 10 selected')).toBeVisible()
  })

  test('Create Ticket Attachment Workflow: attachment quota prevents exceeding maximum 10 attachments', async ({
    page,
  }) => {
    await page.goto('/tickets/new')

    // Attempt to stage 11 files
    const elevenFiles = Array.from({ length: 11 }, (_, i) => ({
      name: `file_${i + 1}.txt`,
      mimeType: 'text/plain',
      buffer: Buffer.from(`Content for file ${i + 1}`),
    }))

    await page.locator('#ticket-create-file-input').setInputFiles(elevenFiles)
    await expect(page.getByText(/Maximum 10 attachments allowed per ticket/i)).toBeVisible()
    await expect(page.getByText('0 / 10 selected')).toBeVisible()
  })

  test('Create Ticket Attachment Workflow: truthful malware scan reporting for quarantined file', async ({
    page,
  }) => {
    await page.route('**/api/tickets', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            id: '55555555-5555-5555-5555-555555555555',
            ticketNumber: 'FD-000055',
            type: 'INCIDENT',
            title: 'Infected attachment security test',
            description: 'Uploading file that triggers ClamAV signature detection',
            priority: 'LOW',
            status: 'OPEN',
            createdAt: new Date().toISOString(),
          }),
        })
      }
    })

    // Mock backend returning scanStatus = INFECTED
    await page.route('**/api/tickets/FD-000055/attachments', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 'att-55-eicar',
            ticketNumber: 'FD-000055',
            filename: 'eicar-test.txt',
            contentType: 'text/plain',
            sizeBytes: 68,
            uploadedByUserId: '11111111-1111-1111-1111-111111111111',
            uploadedByName: 'Test Employee',
            createdAt: new Date().toISOString(),
            scanStatus: 'INFECTED',
            scanDetails: 'Malware detected: Eicar-Test-Signature',
          }),
        })
      }
    })

    await page.goto('/tickets/new')

    await page.getByLabel(/title/i).fill('Infected attachment security test')
    await page.getByLabel(/description/i).fill('Uploading file that triggers ClamAV signature detection')

    await page.locator('#ticket-create-file-input').setInputFiles([
      {
        name: 'eicar-test.txt',
        mimeType: 'text/plain',
        buffer: Buffer.from('X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*'),
      },
    ])

    await page.getByRole('button', { name: /create ticket with 1 attachment/i }).click()

    // Confirmation screen truthfully warns about malware detection
    await expect(page.getByRole('heading', { name: /ticket created successfully/i })).toBeVisible()
    await expect(page.getByText('Quarantined (Malware Detected)')).toBeVisible()
    await expect(page.getByText(/flagged by automated ClamAV malware scanning and quarantined fail-closed/i)).toBeVisible()
  })
});
