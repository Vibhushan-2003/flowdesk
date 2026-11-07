import { test, expect } from '@playwright/test'
import {
  MOCK_EMPLOYEE,
  MOCK_ADMIN,
  createMockToken,
  setupApiMocks,
} from './fixtures/mockApi'

test.describe('FlowDesk Horizon Batch D — Admin, SLA & Attachment Security Journeys', () => {
  test('SLA Dashboard: renders operational health metrics and links to breached tickets', async ({ page }) => {
    await setupApiMocks(page, MOCK_ADMIN)
    const token = createMockToken(MOCK_ADMIN)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/sla/dashboard', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          activeTickets: 14,
          responseBreached: 2,
          resolutionBreached: 1,
          responseCompliancePercent: 91.5,
          resolutionCompliancePercent: 88.0,
          breachedByPriority: {
            CRITICAL: 1,
            HIGH: 1,
            MEDIUM: 1,
            LOW: 0,
          },
          recentBreaches: [
            {
              ticketNumber: 'FD-100299',
              eventType: 'RESPONSE_BREACHED',
              priority: 'CRITICAL',
              status: 'OPEN',
              occurredAt: '2026-11-02T11:00:00Z',
            },
          ],
        }),
      })
    })

    await page.goto('/sla/dashboard')
    await expect(page.getByRole('heading', { name: /sla operations/i })).toBeVisible()

    // Metric values
    await expect(page.getByText('14', { exact: true })).toBeVisible()
    await expect(page.getByText('2', { exact: true })).toBeVisible()
    await expect(page.getByText('91.5%')).toBeVisible()
    await expect(page.getByText('88.0%')).toBeVisible()

    // Breached ticket
    await expect(page.getByText('FD-100299')).toBeVisible()
    await expect(page.getByText('Response SLA Missed')).toBeVisible()
  })

  test('Security Audit Trail: filters by action, searches actor, and inspects JSON payload', async ({ page }) => {
    await setupApiMocks(page, MOCK_ADMIN)
    const token = createMockToken(MOCK_ADMIN)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    const mockEvent = {
      id: 'audit-001',
      action: 'TICKET_STATUS_CHANGED',
      targetType: 'TICKET',
      targetId: 't-12345',
      targetReference: 'FD-100250',
      actorUserId: MOCK_ADMIN.id,
      actorEmail: MOCK_ADMIN.email,
      actorType: 'USER',
      occurredAt: '2026-11-02T10:30:00Z',
      metadata: {
        ticketNumber: 'FD-100250',
        fromStatus: 'ASSIGNED',
        toStatus: 'IN_PROGRESS',
      },
    }

    await page.route('**/api/audit/events*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [mockEvent],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        }),
      })
    })

    await page.goto('/audit')
    await expect(page.getByRole('heading', { name: /security audit trail/i })).toBeVisible()
    await expect(page.getByRole('table').getByText('FD-100250', { exact: true })).toBeVisible()
    await expect(page.getByRole('table').getByText('Status Changed')).toBeVisible()

    // Click inspect to view modal
    await page.getByRole('button', { name: /inspect event audit-001/i }).first().click()
    await expect(page.getByRole('heading', { name: /audit event inspection/i })).toBeVisible()
    await expect(page.getByText('Event Payload & Metadata (JSON)')).toBeVisible()
    await expect(page.getByText('"fromStatus": "ASSIGNED"')).toBeVisible()

    // Close modal
    await page.getByRole('button', { name: 'Close', exact: true }).click()
    await expect(page.getByRole('heading', { name: /audit event inspection/i })).not.toBeVisible()
  })

  test('User Management: displays user directory, verifies create user is removed, and opens invite modal', async ({ page }) => {
    await setupApiMocks(page, MOCK_ADMIN)
    const token = createMockToken(MOCK_ADMIN)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/admin/users**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              id: 'u-101',
              email: 'jane.smith@flowdesk.test',
              firstName: 'Jane',
              lastName: 'Smith',
              roles: ['SUPPORT_ENGINEER'],
              status: 'ACTIVE',
              createdAt: '2026-11-01T09:00:00Z',
              updatedAt: '2026-11-01T09:00:00Z',
              lastLoginAt: '2026-11-02T08:00:00Z',
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        }),
      })
    })

    await page.goto('/admin/users')
    await expect(page.getByRole('heading', { name: /user management/i })).toBeVisible()
    await expect(page.getByRole('table').getByText('Jane Smith')).toBeVisible()
    await expect(page.getByRole('table').getByText('jane.smith@flowdesk.test')).toBeVisible()

    // Verify Create User button is removed (invitation-only policy)
    await expect(page.getByRole('button', { name: /create user/i })).not.toBeVisible()

    // Open Invite User Modal
    await page.getByRole('button', { name: /invite user/i }).click()
    await expect(page.getByRole('heading', { name: /invite new user/i })).toBeVisible()
    await page.getByRole('button', { name: /cancel/i }).click()
    await expect(page.getByRole('heading', { name: /invite new user/i })).not.toBeVisible()
  })

  test('User Management: resending invitation returns shareable link and opens link modal when email disabled', async ({ page }) => {
    await setupApiMocks(page, MOCK_ADMIN)
    const token = createMockToken(MOCK_ADMIN)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/admin/users?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              id: 'user-invited-001',
              email: 'invited.user@flowdesk.test',
              firstName: 'Invited',
              lastName: 'User',
              status: 'INVITED',
              roles: ['EMPLOYEE'],
              createdAt: '2026-11-01T10:00:00Z',
            },
          ],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        }),
      })
    })

    await page.route('**/api/admin/users/user-invited-001/invitations/resend', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'Invitation re-issued for "invited.user@flowdesk.test".',
          invitationUrl: 'http://localhost:5173/accept-invitation?token=mock-secure-token-12345',
        }),
      })
    })

    await page.goto('/admin/users')
    await expect(page.getByRole('heading', { name: /user management/i })).toBeVisible()
    await expect(page.getByRole('table').getByText('invited.user@flowdesk.test')).toBeVisible()

    // Click Resend Invitation button
    await page.getByRole('table').getByRole('button', { name: /^resend$/i }).click()

    // Verify Invitation Link Ready modal opens
    await expect(page.getByRole('heading', { name: /invitation link ready/i })).toBeVisible()
    await expect(page.getByText(/outbound email is disabled/i)).toBeVisible()
    await expect(page.getByText('Invited User (invited.user@flowdesk.test)')).toBeVisible()

    const linkInput = page.locator('#invitation-url-input')
    await expect(linkInput).toHaveValue('http://localhost:5173/accept-invitation?token=mock-secure-token-12345')

    // Dismiss modal
    await page.getByRole('button', { name: /done/i }).click()
    await expect(page.getByRole('heading', { name: /invitation link ready/i })).not.toBeVisible()
  })

  test('Accept Invitation: allows setting password even when email delivery is disabled, offering Google OAuth when enabled', async ({ page }) => {
    await page.route('**/api/auth/oauth2/config', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          enabled: true,
          clientId: 'mock-google-client-id',
          redirectUri: 'http://localhost:5173/auth/google/callback',
          authUri: 'https://accounts.google.com/o/oauth2/v2/auth',
          mailEnabled: false,
        }),
      })
    })

    await page.goto('/accept-invitation?token=mock-secure-token-12345')

    await expect(page.getByRole('heading', { name: /set up your account/i })).toBeVisible()
    await expect(page.getByText(/email delivery is disabled in this environment/i)).toBeVisible()
    await expect(page.getByRole('button', { name: /activate with google/i })).toBeVisible()

    // Password fields are accessible and ready for user onboarding
    await expect(page.locator('#accept-new-password')).toBeVisible()
    await expect(page.locator('#accept-confirm-password')).toBeVisible()
    await expect(page.getByRole('button', { name: /activate account/i })).toBeVisible()
  })

  test('Administrative Attachment Rescan: confirms batch size and displays execution report', async ({ page }) => {
    await setupApiMocks(page, MOCK_ADMIN)
    const token = createMockToken(MOCK_ADMIN)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/admin/attachments/rescan-pending*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          totalPending: 5,
          scannedCount: 5,
          cleanCount: 4,
          infectedCount: 1,
          failedCount: 0,
          message: 'Attachment batch rescan completed successfully. 1 infected attachment was quarantined.',
        }),
      })
    })

    await page.goto('/admin/attachments')
    await expect(page.getByRole('heading', { name: /attachment security & malware quarantine/i })).toBeVisible()

    // Verify batch size input is bounded
    const batchInput = page.locator('#rescan-batch-size')
    await expect(batchInput).toHaveValue('50')
    await batchInput.fill('25')

    // Click trigger -> opens confirmation modal
    await page.getByRole('button', { name: /trigger batch rescan/i }).click()
    await expect(page.getByRole('heading', { name: /confirm attachment rescan/i })).toBeVisible()

    // Confirm rescan
    await page.getByRole('button', { name: /confirm & start rescan/i }).click()

    // Verify completion report metrics
    await expect(page.getByRole('heading', { name: /batch rescan results/i })).toBeVisible()
    await expect(page.getByText('5 Scanned')).toBeVisible()
    await expect(page.getByText('Verified Clean')).toBeVisible()
    await expect(page.getByText('Infected Quarantined')).toBeVisible()
    await expect(page.getByText('1 infected attachment was quarantined.')).toBeVisible()
  })

  test('Role-Aware Access Control: Employee is strictly barred from all Admin routes', async ({ page }) => {
    await setupApiMocks(page, MOCK_EMPLOYEE)
    const token = createMockToken(MOCK_EMPLOYEE)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    // Barred from /audit
    await page.goto('/audit')
    await page.waitForURL('**/dashboard')
    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible()

    // Barred from /admin/users
    await page.goto('/admin/users')
    await page.waitForURL('**/dashboard')
    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible()

    // Barred from /admin/attachments
    await page.goto('/admin/attachments')
    await page.waitForURL('**/dashboard')
    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible()
  })
})
