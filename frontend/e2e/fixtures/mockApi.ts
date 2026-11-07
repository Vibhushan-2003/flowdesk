import type { Page } from '@playwright/test'

export interface MockUser {
  id: string
  email: string
  roles: string[]
}

export const MOCK_EMPLOYEE: MockUser = {
  id: 'e0000000-0000-0000-0000-000000000001',
  email: 'employee@flowdesk.test',
  roles: ['EMPLOYEE'],
}

export const MOCK_ENGINEER: MockUser = {
  id: 'e0000000-0000-0000-0000-000000000002',
  email: 'engineer@flowdesk.test',
  roles: ['SUPPORT_ENGINEER'],
}

export const MOCK_ADMIN: MockUser = {
  id: 'e0000000-0000-0000-0000-000000000003',
  email: 'admin@flowdesk.test',
  roles: ['ADMIN'],
}

export function createMockToken(user: MockUser, expiresInSeconds = 3600): string {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url')
  const now = Math.floor(Date.now() / 1000)
  const payload = Buffer.from(
    JSON.stringify({
      sub: user.id,
      email: user.email,
      roles: user.roles,
      iat: now,
      exp: now + expiresInSeconds,
    }),
  ).toString('base64url')
  return `${header}.${payload}.mock-signature`
}

export async function setupApiMocks(page: Page, currentUser: MockUser = MOCK_EMPLOYEE) {
  // Public OAuth config
  await page.route('**/api/auth/oauth2/config', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ enabled: false, clientId: '', redirectUri: '', authUri: '' }),
    })
  })

  // Auth Me
  await page.route('**/api/auth/me', async (route) => {
    const authHeader = route.request().headers()['authorization']
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      await route.fulfill({ status: 401, json: { status: 401, error: 'Authentication required' } })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        userId: currentUser.id,
        id: currentUser.id,
        email: currentUser.email,
        roles: currentUser.roles,
      }),
    })
  })

  // Refresh Token
  await page.route('**/api/auth/refresh', async (route) => {
    const cookies = await page.context().cookies()
    const hasRefreshCookie = cookies.some((c) => c.name === 'flowdesk_refresh_token' && !!c.value)
    if (!hasRefreshCookie) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ status: 401, error: 'Refresh token is missing' }),
      })
      return
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      headers: {
        'Set-Cookie': 'flowdesk_refresh_token=mock-rotated-token; Path=/api/auth; HttpOnly; SameSite=Lax',
      },
      body: JSON.stringify({
        accessToken: createMockToken(currentUser),
        tokenType: 'Bearer',
        userId: currentUser.id,
        email: currentUser.email,
        roles: currentUser.roles,
      }),
    })
  })

  // Logout
  await page.route('**/api/auth/logout', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      headers: {
        'Set-Cookie': 'flowdesk_refresh_token=; Path=/api/auth; Max-Age=0; HttpOnly; SameSite=Lax',
      },
      body: JSON.stringify({ message: 'Logged out successfully' }),
    })
  })

  // Login
  await page.route('**/api/auth/login', async (route) => {
    const body = route.request().postDataJSON() as { email?: string; password?: string }
    if (body.email === currentUser.email && body.password === 'ValidPass123!') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: {
          'Set-Cookie': 'flowdesk_refresh_token=mock-refresh-token; Path=/api/auth; HttpOnly; SameSite=Lax',
        },
        body: JSON.stringify({
          accessToken: createMockToken(currentUser),
          tokenType: 'Bearer',
          userId: currentUser.id,
          email: currentUser.email,
          roles: currentUser.roles,
        }),
      })
    } else {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ status: 401, error: 'Invalid email or password' }),
      })
    }
  })

  // Notifications count
  await page.route('**/api/notifications/unread-count', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ unreadCount: 0 }),
    })
  })

  // Dashboard stats
  await page.route('**/api/dashboard/stats', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        openTickets: 3,
        inProgressTickets: 1,
        resolvedTickets: 5,
        criticalTickets: 0,
      }),
    })
  })

  // Default attachments GET fallback
  await page.route('**/api/tickets/*/attachments', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      })
    } else {
      await route.fallback()
    }
  })

  await page.route('**/api/support/tickets/*/attachments', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'att-1',
            filename: 'system-diagnostic-report.log',
            sanitizedFilename: 'system-diagnostic-report.log',
            contentType: 'text/plain',
            sizeBytes: 1048576,
            scanStatus: 'CLEAN',
            createdAt: '2026-11-02T08:35:00Z',
          },
        ]),
      })
    } else {
      await route.fallback()
    }
  })
}

export async function setupComprehensiveMocks(page: Page, currentUser: MockUser = MOCK_ADMIN) {
  await setupApiMocks(page, currentUser)

  await page.route('**/api/tickets/my*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [
          {
            id: 't-1',
            ticketNumber: 'FD-100201',
            title: 'Laptop screen flickering during Zoom calls',
            type: 'INCIDENT',
            priority: 'HIGH',
            status: 'OPEN',
            createdAt: '2026-11-04T10:00:00Z',
            responseDueAt: '2026-11-04T10:30:00Z',
            resolutionDueAt: '2026-11-04T18:00:00Z',
            responseSlaStatus: 'MET',
            resolutionSlaStatus: 'PENDING',
          },
        ],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
      }),
    })
  })

  await page.route('**/api/tickets/FD-100201', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 't-1',
        ticketNumber: 'FD-100201',
        title: 'Laptop screen flickering during Zoom calls',
        description: 'Screen randomly flashes black when external 4K monitor connected.',
        type: 'INCIDENT',
        priority: 'HIGH',
        status: 'OPEN',
        createdAt: '2026-11-04T10:00:00Z',
        updatedAt: '2026-11-04T10:00:00Z',
        responseDueAt: '2026-11-04T10:30:00Z',
        resolutionDueAt: '2026-11-04T18:00:00Z',
        responseSlaStatus: 'MET',
        resolutionSlaStatus: 'PENDING',
        createdByEmail: 'requester@flowdesk.test',
      }),
    })
  })

  await page.route('**/api/tickets/FD-100201/comments', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
  })

  await page.route('**/api/tickets/queue*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [
          {
            id: 't-queue-1',
            ticketNumber: 'FD-100300',
            title: 'SSO login error 500 for finance team',
            type: 'INCIDENT',
            priority: 'CRITICAL',
            status: 'OPEN',
            createdAt: '2026-11-04T09:00:00Z',
            responseDueAt: '2026-11-04T09:15:00Z',
            resolutionDueAt: '2026-11-04T13:00:00Z',
            responseSlaStatus: 'PENDING',
            resolutionSlaStatus: 'PENDING',
          },
        ],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
      }),
    })
  })

  await page.route('**/api/support/tickets/my*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [
          {
            id: 't-supp-1',
            ticketNumber: 'FD-100200',
            title: 'Database connection pool exhaustion on production API',
            type: 'INCIDENT',
            priority: 'MEDIUM',
            status: 'ASSIGNED',
            createdAt: '2026-11-04T08:30:00Z',
            responseDueAt: '2026-11-04T09:30:00Z',
            resolutionDueAt: '2026-11-05T08:30:00Z',
            responseSlaStatus: 'MET',
            resolutionSlaStatus: 'PENDING',
          },
        ],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
      }),
    })
  })

  await page.route('**/api/support/tickets/FD-100200', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 't-supp-1',
        ticketNumber: 'FD-100200',
        title: 'Database connection pool exhaustion on production API',
        description: 'HikariCP pool timeouts observed during morning traffic spike.',
        type: 'INCIDENT',
        priority: 'MEDIUM',
        status: 'ASSIGNED',
        assignedToUserId: currentUser.id,
        assignedToEmail: currentUser.email,
        assignedAt: '2026-11-04T08:35:00Z',
        createdAt: '2026-11-04T08:30:00Z',
        updatedAt: '2026-11-04T08:35:00Z',
        responseDueAt: '2026-11-04T09:30:00Z',
        resolutionDueAt: '2026-11-05T08:30:00Z',
        responseSlaStatus: 'MET',
        resolutionSlaStatus: 'PENDING',
        createdByEmail: 'alice@flowdesk.test',
      }),
    })
  })

  await page.route('**/api/support/tickets/FD-100200/comments', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
  })

  await page.route('**/api/support/tickets/FD-100200/ai-triage', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        ticketId: 't-supp-1',
        suggestedPriority: 'HIGH',
        confidenceScore: 0.89,
        reasoning: 'Critical backend infrastructure affected',
        status: 'PENDING',
      }),
    })
  })

  await page.route('**/api/kb/categories', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { id: 'cat-1', name: 'Authentication', description: 'Logins and passwords', articleCount: 4 },
        { id: 'cat-2', name: 'Network', description: 'VPN and WiFi', articleCount: 3 },
      ]),
    })
  })

  await page.route('**/api/kb/articles/*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'art-1',
        title: 'How to Reset Your Corporate Password',
        slug: 'how-to-reset-password',
        summary: 'Follow these steps to reset your corporate password securely.',
        contentMarkdown: '## Steps to Reset Password\n\n1. Navigate to identity portal.\n2. Complete MFA challenge.',
        categoryId: 'cat-1',
        categoryName: 'Authentication',
        categorySlug: 'authentication',
        status: 'PUBLISHED',
        createdAt: '2026-10-15T08:00:00Z',
        updatedAt: '2026-11-01T14:00:00Z',
        authorName: 'Alex Smith',
        viewCount: 42,
        helpfulCount: 20,
        deflectedCount: 15,
      }),
    })
  })

  await page.route('**/api/support/kb/articles/*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'art-1',
        title: 'How to Reset Your Corporate Password',
        slug: 'how-to-reset-password',
        summary: 'Follow these steps to reset your corporate password securely.',
        contentMarkdown: '## Steps to Reset Password\n\n1. Navigate to identity portal.\n2. Complete MFA challenge.',
        categoryId: 'cat-1',
        categoryName: 'Authentication',
        categorySlug: 'authentication',
        status: 'PUBLISHED',
        createdAt: '2026-10-15T08:00:00Z',
        updatedAt: '2026-11-01T14:00:00Z',
        authorName: 'Alex Smith',
        viewCount: 42,
        helpfulCount: 20,
        deflectedCount: 15,
      }),
    })
  })

  await page.route('**/api/kb/articles?*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [
          {
            id: 'art-1',
            title: 'How to Reset Your Corporate Password',
            slug: 'how-to-reset-password',
            categoryName: 'Authentication',
            viewCount: 42,
            deflectionCount: 15,
            isPublished: true,
          },
        ],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
      }),
    })
  })

  await page.route('**/api/kb/articles', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [
          {
            id: 'art-1',
            title: 'How to Reset Your Corporate Password',
            slug: 'how-to-reset-password',
            categoryName: 'Authentication',
            viewCount: 42,
            deflectionCount: 15,
            isPublished: true,
          },
        ],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
      }),
    })
  })

  await page.route('**/api/approvals/pending*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [
          {
            id: 'app-1',
            ticketNumber: 'FD-100215',
            ticketTitle: 'Production AWS IAM Admin Policy Grant',
            priority: 'HIGH',
            status: 'PENDING',
            requestedByEmail: 'dev@flowdesk.test',
            requestedAt: '2026-11-04T09:00:00Z',
          },
        ],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
      }),
    })
  })

  await page.route('**/api/sla/dashboard', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        activeTickets: 8,
        responseBreached: 1,
        resolutionBreached: 0,
        responseCompliancePercent: 92.0,
        resolutionCompliancePercent: 96.0,
        breachedByPriority: { CRITICAL: 0, HIGH: 1, MEDIUM: 0, LOW: 0 },
        recentBreaches: [
          {
            ticketNumber: 'FD-100299',
            eventType: 'RESPONSE_BREACHED',
            priority: 'HIGH',
            status: 'OPEN',
            occurredAt: '2026-11-04T10:00:00Z',
          },
        ],
      }),
    })
  })

  await page.route('**/api/reports/operational*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        dateRange: { startDate: '2026-10-05', endDate: '2026-11-04', generatedAt: '2026-11-04T00:00:00Z' },
        ticketVolume: { totalCreated: 45, totalResolved: 35, openBacklog: 10 },
        volumeByStatus: { OPEN: 5, IN_PROGRESS: 5, RESOLVED: 35 },
        volumeByPriority: { CRITICAL: 3, HIGH: 10, MEDIUM: 22, LOW: 10 },
        dailyTrends: [{ date: '2026-11-04', created: 5, resolved: 4 }],
        engineerWorkloads: [],
        resolutionMetrics: { resolvedCount: 35, avgDurationHours: 4.2, medianDurationHours: 2.8, avgDurationByPriorityHours: { CRITICAL: 1.0, HIGH: 2.5, MEDIUM: 5.0, LOW: 8.0 } },
        slaCompliance: { totalResolved: 35, compliantResolved: 33, complianceRatePercent: 94.3, responseBreaches: 1, resolutionBreaches: 1 },
        kbDeflection: { totalArticles: 10, totalViews: 90, totalDeflections: 20, deflectionRatePercent: 22.2, topArticles: [] },
      }),
    })
  })

  await page.route('**/api/audit/events*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [
          {
            id: 'audit-1',
            action: 'TICKET_PRIORITY_CHANGED',
            targetType: 'TICKET',
            targetId: 't-1',
            targetIdentifier: 'FD-100201',
            actorEmail: 'admin@flowdesk.test',
            timestamp: '2026-11-04T10:00:00Z',
            details: { fromPriority: 'MEDIUM', toPriority: 'HIGH' },
          },
        ],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
      }),
    })
  })

  await page.route('**/api/admin/users*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [
          {
            id: currentUser.id,
            email: currentUser.email,
            status: 'ACTIVE',
            roles: currentUser.roles,
            createdAt: '2026-11-01T00:00:00Z',
          },
        ],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
      }),
    })
  })

  await page.route('**/api/admin/attachments/rescan-pending*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        pendingCount: 0,
        scannedCount: 15,
        quarantinedCount: 0,
      }),
    })
  })
}
