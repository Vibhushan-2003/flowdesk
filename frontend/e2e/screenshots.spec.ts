import { test, expect } from '@playwright/test'
import path from 'path'
import fs from 'fs'
import {
  MOCK_EMPLOYEE,
  MOCK_ENGINEER,
  MOCK_ADMIN,
  createMockToken,
  setupApiMocks,
} from './fixtures/mockApi'

const SCREENSHOT_DIR = process.env.CI
  ? path.resolve(process.cwd(), 'playwright-screenshots')
  : path.resolve(
      'C:/Users/gvibh/.gemini/antigravity/brain/883a14e0-42dd-4f24-a364-05a5cbe98223/screenshots',
    )

if (!fs.existsSync(SCREENSHOT_DIR)) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true })
}

test.describe('Horizon Visual Screenshots Suite', () => {
  test('01 & 02: Dashboard Light and Dark (1440x900)', async ({ page }) => {
    await setupApiMocks(page, MOCK_ADMIN)
    const token = createMockToken(MOCK_ADMIN)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/dashboard/stats', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          employee: {
            openTickets: 3,
            resolvedTickets: 18,
            ticketsAwaitingResponse: 1,
            pendingApprovals: 0,
          },
          support: {
            assignedTickets: 5,
            unassignedQueue: 4,
            breachedTickets: 1,
            slaComplianceRate: 94.8,
          },
          operations: {
            activeTickets: 24,
            openHighUrgentTickets: 3,
            approvalsPendingTotal: 2,
            slaBreachesTotal: 1,
            totalManagedUsers: 48,
          },
        }),
      })
    })

    await page.setViewportSize({ width: 1440, height: 900 })

    // 01: Light Dashboard
    await page.addInitScript(() => {
      localStorage.setItem('flowdesk_theme', 'light')
    })
    await page.goto('/dashboard')
    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible()
    await page.waitForTimeout(500)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '01-dashboard-light.png'),
      fullPage: false,
    })

    // 02: Dark Dashboard
    await page.evaluate(() => {
      localStorage.setItem('flowdesk_theme', 'dark')
      document.documentElement.setAttribute('data-theme', 'dark')
      document.documentElement.classList.remove('theme-light')
      document.documentElement.classList.add('theme-dark')
    })
    await page.waitForTimeout(500)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '02-dashboard-dark.png'),
      fullPage: false,
    })
  })

  test('03: Login Page Desktop and Mobile', async ({ page }) => {
    await setupApiMocks(page, MOCK_EMPLOYEE)

    // Desktop Login
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/login')
    await expect(page.getByRole('button', { name: /sign in/i })).toBeVisible()
    await page.waitForTimeout(300)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '03a-login-desktop.png'),
      fullPage: false,
    })

    // Mobile Login
    await page.setViewportSize({ width: 390, height: 844 })
    await page.waitForTimeout(300)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '03b-login-mobile.png'),
      fullPage: false,
    })
  })

  test('04: My Tickets Page Desktop and Mobile', async ({ page }) => {
    await setupApiMocks(page, MOCK_EMPLOYEE)
    const token = createMockToken(MOCK_EMPLOYEE)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/tickets/my*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              id: '1',
              ticketNumber: 'FD-100201',
              title: 'Cannot access internal Kubernetes dashboard',
              status: 'IN_PROGRESS',
              priority: 'HIGH',
              type: 'INCIDENT',
              createdAt: '2026-11-02T10:15:00Z',
              slaStatus: 'WITHIN_SLA',
            },
            {
              id: '2',
              ticketNumber: 'FD-100202',
              title: 'Request for Figma enterprise license',
              status: 'WAITING_FOR_USER',
              priority: 'MEDIUM',
              type: 'SERVICE_REQUEST',
              createdAt: '2026-11-01T14:30:00Z',
              slaStatus: 'WITHIN_SLA',
            },
            {
              id: '3',
              ticketNumber: 'FD-100203',
              title: 'MacBook Pro battery health degradation warning',
              status: 'OPEN',
              priority: 'LOW',
              type: 'INCIDENT',
              createdAt: '2026-10-31T09:00:00Z',
              slaStatus: 'WITHIN_SLA',
            },
            {
              id: '4',
              ticketNumber: 'FD-100199',
              title: 'Production SSL certificate rotation assistance',
              status: 'RESOLVED',
              priority: 'URGENT',
              type: 'INCIDENT',
              createdAt: '2026-10-28T16:45:00Z',
              slaStatus: 'WITHIN_SLA',
            },
          ],
          totalElements: 4,
          totalPages: 1,
          size: 10,
          page: 0,
          first: true,
          last: true,
        }),
      })
    })

    // Desktop (1440x900)
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/tickets')
    await expect(page.getByRole('heading', { name: /my tickets/i })).toBeVisible()
    await expect(page.getByRole('button', { name: 'FD-100201' }).first()).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '04a-my-tickets-desktop.png'),
      fullPage: false,
    })

    // Mobile (390x844)
    await page.setViewportSize({ width: 390, height: 844 })
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '04b-my-tickets-mobile.png'),
      fullPage: false,
    })
  })

  test('05: Create Ticket Desktop with Deflection Suggestions', async ({ page }) => {
    await setupApiMocks(page, MOCK_EMPLOYEE)
    const token = createMockToken(MOCK_EMPLOYEE)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/kb/articles/deflect*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'art-1',
            title: 'How to configure Corporate VPN on macOS and Windows',
            slug: 'how-to-configure-corporate-vpn',
            summary: 'Step-by-step connection guide for GlobalProtect and WireGuard profiles.',
          },
          {
            id: 'art-2',
            title: 'Resolving VPN Gateway TLS Handshake Timeouts',
            slug: 'resolving-vpn-gateway-tls-handshake-timeouts',
            summary: 'Common fixes for 2FA delays and certificate cache errors.',
          },
        ]),
      })
    })

    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/tickets/new')
    await expect(page.getByRole('heading', { name: /create a ticket/i })).toBeVisible()

    // Type in ticket details to trigger deflection
    await page.locator('#ticket-title-input').fill('Cannot connect to Corporate VPN')
    await page.locator('#ticket-description-input').fill('Getting timeout errors when authenticating through the gateway.')

    // Wait for deflection suggestions to appear
    await expect(page.getByText('How to configure Corporate VPN on macOS and Windows')).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '05-create-ticket-desktop.png'),
      fullPage: false,
    })
  })

  test('06: Ticket Details Desktop', async ({ page }) => {
    await setupApiMocks(page, MOCK_EMPLOYEE)
    const token = createMockToken(MOCK_EMPLOYEE)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/tickets/FD-100201', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: '1',
          ticketNumber: 'FD-100201',
          title: 'Cannot access internal Kubernetes dashboard',
          description:
            'Receiving 403 Forbidden when attempting to connect to the staging cluster dashboard via Teleport proxy.',
          type: 'INCIDENT',
          priority: 'HIGH',
          status: 'IN_PROGRESS',
          createdAt: '2026-11-02T10:15:00Z',
          updatedAt: '2026-11-02T11:45:00Z',
          requesterEmail: 'employee@flowdesk.test',
          requesterName: 'Jane Doe',
          assignedEngineerEmail: 'engineer@flowdesk.test',
          assignedEngineerName: 'Alex Smith',
          slaStatus: 'WITHIN_SLA',
          slaResponseDeadline: '2026-11-02T14:15:00Z',
          slaResolutionDeadline: '2026-11-02T18:15:00Z',
        }),
      })
    })

    await page.route('**/api/tickets/FD-100201/comments', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'c1',
            authorEmail: 'engineer@flowdesk.test',
            authorName: 'Alex Smith',
            body: 'Hello Jane, I verified your IAM role on staging cluster. Please retry logging into Teleport now.',
            visibility: 'PUBLIC',
            createdAt: '2026-11-02T11:45:00Z',
          },
        ]),
      })
    })

    await page.route('**/api/tickets/FD-100201/attachments**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'att-1',
            ticketNumber: 'FD-100201',
            filename: 'teleport-error-screenshot.png',
            sizeBytes: 142800,
            contentType: 'image/png',
            scanStatus: 'CLEAN',
            uploadedByUserId: 'u1',
            uploadedByName: 'Jane Doe',
            createdAt: '2026-11-02T10:16:00Z',
          },
        ]),
      })
    })

    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/tickets/FD-100201')
    await expect(page.getByRole('heading', { name: /ticket details/i })).toBeVisible()
    await expect(page.getByText('Cannot access internal Kubernetes dashboard')).toBeVisible()
    await expect(page.getByText('teleport-error-screenshot.png')).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '06-ticket-details-desktop.png'),
      fullPage: false,
    })
  })

  test('07: Knowledge Base Desktop and Mobile', async ({ page }) => {
    await setupApiMocks(page, MOCK_EMPLOYEE)
    const token = createMockToken(MOCK_EMPLOYEE)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/kb/categories', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { id: 'cat-1', name: 'Network & Connectivity', description: 'VPN, Wi-Fi, DNS' },
          { id: 'cat-2', name: 'Security & Access', description: 'MFA, passwords, credentials' },
          { id: 'cat-3', name: 'Developer Tooling', description: 'Docker, Git, IDEs' },
          { id: 'cat-4', name: 'Hardware & Devices', description: 'Laptops, monitors, peripherals' },
        ]),
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
              title: 'How to configure Corporate VPN on macOS and Windows',
              slug: 'how-to-configure-corporate-vpn',
              summary: 'Complete setup guide with credentials, client profiles, and certificate renewal.',
              categoryName: 'Network & Connectivity',
              createdAt: '2026-10-15T08:00:00Z',
              viewCount: 412,
            },
            {
              id: 'art-2',
              title: 'Setting up Hardware Security Keys for MFA',
              slug: 'hardware-security-keys-mfa',
              summary: 'Configure YubiKey 5 Series for corporate SSO authentication and GitHub SSH.',
              categoryName: 'Security & Access',
              createdAt: '2026-10-18T11:20:00Z',
              viewCount: 289,
            },
            {
              id: 'art-3',
              title: 'Local Docker Environment Configuration Guide',
              slug: 'local-docker-environment-guide',
              summary: 'Colima and Docker Desktop setup instructions for Apple Silicon workstations.',
              categoryName: 'Developer Tooling',
              createdAt: '2026-10-22T14:10:00Z',
              viewCount: 654,
            },
            {
              id: 'art-4',
              title: 'Ergonomic Monitor & Hub Setup Checklist',
              slug: 'ergonomic-monitor-hub-setup',
              summary: 'DisplayPort 1.4 daisy chaining and USB-C power delivery troubleshooting.',
              categoryName: 'Hardware & Devices',
              createdAt: '2026-10-25T09:40:00Z',
              viewCount: 147,
            },
          ],
          totalElements: 4,
          totalPages: 1,
          size: 10,
          page: 0,
          first: true,
          last: true,
        }),
      })
    })

    // Desktop (1440x900)
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/kb')
    await expect(page.getByRole('heading', { name: /knowledge base/i })).toBeVisible()
    await expect(page.getByText('How to configure Corporate VPN on macOS and Windows')).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '07a-knowledge-base-desktop.png'),
      fullPage: false,
    })

    // Mobile (390x844)
    await page.setViewportSize({ width: 390, height: 844 })
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '07b-knowledge-base-mobile.png'),
      fullPage: false,
    })
  })

  test('08: Article View and Editor Screens', async ({ page }) => {
    await setupApiMocks(page, MOCK_ADMIN)
    const token = createMockToken(MOCK_ADMIN)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    // Route for Article View
    await page.route('**/api/kb/articles/how-to-configure-corporate-vpn', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'art-1',
          title: 'How to configure Corporate VPN on macOS and Windows',
          slug: 'how-to-configure-corporate-vpn',
          summary: 'Complete setup guide with credentials, client profiles, and certificate renewal.',
          contentMarkdown: `## Overview\n\nFollow these instructions to connect to our private internal network.\n\n### Step 1: Install Client\n\nDownload the approved GlobalProtect client from the IT Software Portal.\n\n### Step 2: Configure Gateway\n\n- **Gateway URL**: \`vpn.flowdesk.corp\`\n- **Port**: \`443\`\n- **Authentication**: SSO with Hardware MFA Token\n\n\`\`\`bash\n# Verify your connection status\ncurl -I https://internal.dashboard.flowdesk.corp\n\`\`\`\n\n### Troubleshooting\n\nIf you experience TLS handshake timeouts, flush your local DNS cache.`,
          categoryId: 'cat-1',
          categoryName: 'Network & Connectivity',
          categorySlug: 'network-connectivity',
          status: 'PUBLISHED',
          createdAt: '2026-10-15T08:00:00Z',
          updatedAt: '2026-11-01T14:00:00Z',
          authorName: 'Alex Smith',
          viewCount: 412,
          helpfulCount: 88,
          deflectedCount: 34,
        }),
      })
    })

    // Route for Article Editor categories
    await page.route('**/api/kb/categories', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { id: 'cat-1', name: 'Network & Connectivity', description: 'VPN, Wi-Fi, DNS' },
          { id: 'cat-2', name: 'Security & Access', description: 'MFA, passwords' },
        ]),
      })
    })

    await page.setViewportSize({ width: 1440, height: 900 })

    // 08a: Article View
    await page.goto('/kb/articles/how-to-configure-corporate-vpn')
    await expect(page.getByRole('heading', { name: /how to configure corporate vpn/i })).toBeVisible()
    await expect(page.getByText('Did this article resolve your issue?')).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '08a-article-view.png'),
      fullPage: false,
    })

    // 08b: Article Editor
    await page.goto('/support/kb/articles/new')
    await expect(page.getByRole('heading', { name: /create knowledge base article/i })).toBeVisible()
    await page.locator('#article-title').fill('Setting Up Remote SSH Bastion Access')
    await page.locator('#article-summary').fill('Instructions for configuring SSH proxy jumps and public key verification.')
    await page.locator('#article-content').fill('## Prerequisites\n\n1. Generate an Ed25519 keypair\n2. Register key in FlowDesk IAM portal')
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '08b-article-editor.png'),
      fullPage: false,
    })
  })

  test('09: Support Queue Desktop and Mobile', async ({ page }) => {
    await setupApiMocks(page, MOCK_ENGINEER)
    const token = createMockToken(MOCK_ENGINEER)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/tickets/queue**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              id: '1',
              ticketNumber: 'FD-100210',
              title: 'VPN gateway packet loss affecting Frankfurt branch',
              type: 'INCIDENT',
              priority: 'URGENT',
              status: 'OPEN',
              createdAt: '2026-11-02T10:00:00Z',
              responseDueAt: '2026-11-02T11:00:00Z',
              resolutionDueAt: '2026-11-02T14:00:00Z',
              responseSlaStatus: 'MET',
              resolutionSlaStatus: 'PENDING',
            },
            {
              id: '2',
              ticketNumber: 'FD-100211',
              title: 'Provision developer sandbox access for new hire',
              type: 'SERVICE_REQUEST',
              priority: 'NORMAL',
              status: 'OPEN',
              createdAt: '2026-11-02T11:15:00Z',
              responseDueAt: '2026-11-02T15:15:00Z',
              resolutionDueAt: '2026-11-03T11:15:00Z',
              responseSlaStatus: 'MET',
              resolutionSlaStatus: 'PENDING',
            },
            {
              id: '3',
              ticketNumber: 'FD-100212',
              title: 'PostgreSQL read replica latency alert',
              type: 'INCIDENT',
              priority: 'HIGH',
              status: 'OPEN',
              createdAt: '2026-11-02T11:30:00Z',
              responseDueAt: '2026-11-02T13:30:00Z',
              resolutionDueAt: '2026-11-02T17:30:00Z',
              responseSlaStatus: 'PENDING',
              resolutionSlaStatus: 'PENDING',
            },
          ],
          page: 0,
          size: 10,
          totalElements: 3,
          totalPages: 1,
        }),
      })
    })

    // Desktop
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/support/queue')
    await expect(page.getByRole('heading', { name: /support queue/i })).toBeVisible()
    await expect(page.getByRole('table').getByText('VPN gateway packet loss affecting Frankfurt branch')).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '09a-support-queue-desktop.png'),
      fullPage: false,
    })

    // Mobile
    await page.setViewportSize({ width: 390, height: 844 })
    await page.reload()
    await expect(page.getByRole('heading', { name: /support queue/i })).toBeVisible()
    await expect(page.locator('.mobile-ticket-card-view').getByText('VPN gateway packet loss affecting Frankfurt branch')).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '09b-support-queue-mobile.png'),
      fullPage: false,
    })
  })

  test('10: Assigned Tickets Desktop and Mobile', async ({ page }) => {
    await setupApiMocks(page, MOCK_ENGINEER)
    const token = createMockToken(MOCK_ENGINEER)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/support/tickets/my**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              id: '1',
              ticketNumber: 'FD-100200',
              title: 'Database connection pool exhaustion on production API',
              type: 'INCIDENT',
              priority: 'URGENT',
              status: 'IN_PROGRESS',
              createdByEmail: 'alice@flowdesk.test',
              assignedToEmail: 'engineer@flowdesk.test',
              createdAt: '2026-11-02T08:30:00Z',
              updatedAt: '2026-11-02T11:00:00Z',
              assignedAt: '2026-11-02T08:35:00Z',
              responseDueAt: '2026-11-02T09:30:00Z',
              resolutionDueAt: '2026-11-02T12:30:00Z',
              responseSlaStatus: 'MET',
              resolutionSlaStatus: 'PENDING',
            },
            {
              id: '2',
              ticketNumber: 'FD-100205',
              title: 'Request for staging AWS S3 bucket CORS configuration',
              type: 'SERVICE_REQUEST',
              priority: 'LOW',
              status: 'WAITING_FOR_USER',
              createdByEmail: 'bob@flowdesk.test',
              assignedToEmail: 'engineer@flowdesk.test',
              createdAt: '2026-11-01T14:00:00Z',
              updatedAt: '2026-11-02T09:00:00Z',
              assignedAt: '2026-11-01T14:10:00Z',
              responseDueAt: '2026-11-01T18:00:00Z',
              resolutionDueAt: '2026-11-03T14:00:00Z',
              responseSlaStatus: 'MET',
              resolutionSlaStatus: 'PENDING',
            },
          ],
          page: 0,
          size: 10,
          totalElements: 2,
          totalPages: 1,
        }),
      })
    })

    // Desktop
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/support/tickets')
    await expect(page.getByRole('heading', { name: /my assigned tickets/i })).toBeVisible()
    await expect(page.getByRole('table').getByText('Database connection pool exhaustion on production API')).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '10a-assigned-tickets-desktop.png'),
      fullPage: false,
    })

    // Mobile
    await page.setViewportSize({ width: 390, height: 844 })
    await page.reload()
    await expect(page.getByRole('heading', { name: /my assigned tickets/i })).toBeVisible()
    await expect(page.locator('.mobile-ticket-card-view').getByText('Database connection pool exhaustion on production API')).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '10b-assigned-tickets-mobile.png'),
      fullPage: false,
    })
  })

  test('11: Support Ticket Details Desktop (Light & Dark) and Mobile', async ({ page }) => {
    await setupApiMocks(page, MOCK_ENGINEER)
    const token = createMockToken(MOCK_ENGINEER)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/support/tickets/FD-100200', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: '1',
          ticketNumber: 'FD-100200',
          title: 'Database connection pool exhaustion on production API',
          description:
            'HikariCP pool timeouts observed on checkout service during morning flash campaign. Active database connections hovering at max limit (100).',
          type: 'INCIDENT',
          priority: 'URGENT',
          status: 'IN_PROGRESS',
          createdByUserId: 'e0000000-0000-0000-0000-000000000001',
          createdByEmail: 'alice@flowdesk.test',
          assignedToUserId: 'e0000000-0000-0000-0000-000000000002',
          assignedToEmail: 'engineer@flowdesk.test',
          assignedAt: '2026-11-02T08:35:00Z',
          createdAt: '2026-11-02T08:30:00Z',
          updatedAt: '2026-11-02T11:00:00Z',
          responseDueAt: '2026-11-02T09:30:00Z',
          resolutionDueAt: '2026-11-02T12:30:00Z',
          responseSlaStatus: 'MET',
          resolutionSlaStatus: 'PENDING',
        }),
      })
    })

    await page.route('**/api/support/tickets/FD-100200/comments', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            commentId: 'c1',
            authorUserId: 'e0000000-0000-0000-0000-000000000001',
            authorName: 'Alice Johnson',
            body: 'Experiencing 504 gateway timeouts on /api/v1/orders during checkout spike.',
            visibility: 'PUBLIC',
            createdAt: '2026-11-02T08:30:00Z',
          },
          {
            commentId: 'c2',
            authorUserId: 'e0000000-0000-0000-0000-000000000002',
            authorName: 'Support Staff',
            body: 'Internal Investigation: Checked pgbouncer stats. Abandoned cart webhook worker is holding idle connections without auto-closing.',
            visibility: 'INTERNAL',
            createdAt: '2026-11-02T08:45:00Z',
          },
          {
            commentId: 'c3',
            authorUserId: 'e0000000-0000-0000-0000-000000000002',
            authorName: 'Support Staff',
            body: 'We deployed a hotfix terminating leaked connections and scaled pool limits. Latencies are recovering.',
            visibility: 'PUBLIC',
            createdAt: '2026-11-02T09:15:00Z',
          },
        ]),
      })
    })

    await page.route('**/api/support/tickets/FD-100200/attachments', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'att-1',
            sanitizedFilename: 'hikari-pool-exhaustion.png',
            contentType: 'image/png',
            sizeBytes: 245760,
            scanStatus: 'CLEAN',
            createdAt: '2026-11-02T08:31:00Z',
          },
        ]),
      })
    })

    await page.route('**/api/tickets/FD-100200/attachments**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'att-db-1',
            ticketNumber: 'FD-100200',
            filename: 'hikaricp-metrics-spike.png',
            sizeBytes: 204850,
            contentType: 'image/png',
            scanStatus: 'CLEAN',
            uploadedByUserId: 'u1',
            uploadedByName: 'Alice Johnson',
            createdAt: '2026-11-02T08:31:00Z',
          },
        ]),
      })
    })

    await page.route('**/api/support/tickets/FD-100200/ai-triage', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          recommendationId: 'rec-101',
          ticketId: '1',
          ticketNumber: 'FD-100200',
          suggestedPriority: 'URGENT',
          confidence: 0.96,
          reason: 'Production database connection pool saturation directly degrades customer checkouts.',
          provider: 'google',
          model: 'gemini-2.0-flash',
          promptVersion: 'v1.2',
          generatedByUserId: 'e0000000-0000-0000-0000-000000000002',
          createdAt: '2026-11-02T08:35:00Z',
          applied: false,
          appliedByUserId: null,
          appliedAt: null,
        }),
      })
    })

    // 11a: Desktop Light
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.addInitScript(() => {
      localStorage.setItem('flowdesk_theme', 'light')
    })
    await page.goto('/support/tickets/FD-100200')
    await expect(page.getByRole('heading', { name: /support ticket details/i })).toBeVisible()
    await expect(page.getByText('Database connection pool exhaustion on production API')).toBeVisible()
    await expect(page.getByTestId('internal-note-badge')).toBeVisible()
    await page.waitForTimeout(500)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '11a-support-ticket-details-light.png'),
      fullPage: false,
    })

    // 11b: Desktop Dark
    await page.evaluate(() => {
      localStorage.setItem('flowdesk_theme', 'dark')
      document.documentElement.setAttribute('data-theme', 'dark')
      document.documentElement.classList.remove('theme-light')
      document.documentElement.classList.add('theme-dark')
    })
    await page.waitForTimeout(500)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '11b-support-ticket-details-dark.png'),
      fullPage: false,
    })

    // 11c: Mobile
    await page.setViewportSize({ width: 390, height: 844 })
    await page.evaluate(() => {
      localStorage.setItem('flowdesk_theme', 'light')
      document.documentElement.setAttribute('data-theme', 'light')
      document.documentElement.classList.remove('theme-dark')
      document.documentElement.classList.add('theme-light')
    })
    await page.reload()
    await expect(page.getByRole('heading', { name: /support ticket details/i })).toBeVisible()
    await page.waitForTimeout(500)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '11c-support-ticket-details-mobile.png'),
      fullPage: false,
    })
  })

  test('12: AI Assistant Panels (Copilot and Triage)', async ({ page }) => {
    await setupApiMocks(page, MOCK_ENGINEER)
    const token = createMockToken(MOCK_ENGINEER)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/support/tickets/FD-100200', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: '1',
          ticketNumber: 'FD-100200',
          title: 'Database connection pool exhaustion on production API',
          description:
            'HikariCP pool timeouts observed on checkout service during morning flash campaign.',
          type: 'INCIDENT',
          priority: 'NORMAL',
          status: 'ASSIGNED',
          createdByUserId: 'e0000000-0000-0000-0000-000000000001',
          createdByEmail: 'alice@flowdesk.test',
          assignedToUserId: 'e0000000-0000-0000-0000-000000000002',
          assignedToEmail: 'engineer@flowdesk.test',
          assignedAt: '2026-11-02T08:35:00Z',
          createdAt: '2026-11-02T08:30:00Z',
          updatedAt: '2026-11-02T11:00:00Z',
          responseDueAt: '2026-11-02T09:30:00Z',
          resolutionDueAt: '2026-11-02T12:30:00Z',
          responseSlaStatus: 'MET',
          resolutionSlaStatus: 'PENDING',
        }),
      })
    })

    await page.route('**/api/support/tickets/FD-100200/comments', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            commentId: 'c1',
            authorUserId: 'e0000000-0000-0000-0000-000000000001',
            authorName: 'Alice Johnson',
            body: 'Experiencing 504 gateway timeouts on /api/v1/orders.',
            visibility: 'PUBLIC',
            createdAt: '2026-11-02T08:30:00Z',
          },
        ]),
      })
    })

    await page.route('**/api/tickets/FD-100200/attachments**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      })
    })

    await page.route('**/api/support/tickets/FD-100200/ai-copilot', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ticketNumber: 'FD-100200',
          answer:
            '**Root Cause Analysis:**\n\n1. The connection pool ceiling (100) is being saturated due to unclosed database connections in asynchronous workers.\n2. Inspect the latest checkout worker deployment for unclosed transactions.\n3. **Recommendation:** Temporarily increase connection pool size to 150 and enable Hikari leak detection threshold at 2000ms.',
          toolsUsed: ['GET_TICKET_OVERVIEW', 'GET_PUBLIC_CONVERSATION', 'SEARCH_KNOWLEDGE_BASE'],
          provider: 'google',
          model: 'gemini-2.0-flash',
          generatedAt: '2026-11-02T11:05:00Z',
        }),
      })
    })

    await page.route('**/api/support/tickets/FD-100200/ai-triage', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          recommendationId: 'rec-101',
          ticketId: '1',
          ticketNumber: 'FD-100200',
          suggestedPriority: 'URGENT',
          confidence: 0.96,
          reason: 'Production checkout downtime identified. Customer facing revenue operations directly degraded.',
          provider: 'google',
          model: 'gemini-2.0-flash',
          promptVersion: 'v1.2',
          generatedByUserId: 'e0000000-0000-0000-0000-000000000002',
          createdAt: '2026-11-02T08:35:00Z',
          applied: false,
          appliedByUserId: null,
          appliedAt: null,
        }),
      })
    })

    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/support/tickets/FD-100200')
    await expect(page.getByRole('heading', { name: /support ticket details/i })).toBeVisible()
    await page.waitForTimeout(500)

    // 12a: Copilot prompt submission and answer display
    const copilotInput = page.locator('#ai-copilot-question-input')
    await expect(copilotInput).toBeVisible()
    await copilotInput.fill('What is the recommended fix for HikariCP connection pool saturation?')
    await page.locator('button[aria-label="Ask Copilot"]').click()
    await expect(page.getByText('Root Cause Analysis:')).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '12a-ai-workbench-copilot.png'),
      fullPage: false,
    })

    // 12b: Triage tab with confidence and Apply modal
    await page.getByRole('tab', { name: 'Triage' }).click()
    await expect(page.getByText('Production checkout downtime identified')).toBeVisible()
    await page.getByRole('button', { name: /apply recommendation/i }).click()
    await expect(page.getByRole('heading', { name: /apply ai priority recommendation/i })).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '12b-ai-workbench-triage.png'),
      fullPage: false,
    })
  })

  test('13: Approval Inbox Desktop and Mobile', async ({ page }) => {
    await setupApiMocks(page, MOCK_ADMIN)
    const token = createMockToken(MOCK_ADMIN)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/approvals/pending**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              id: 'app-001',
              ticketId: 't-001',
              ticketNumber: 'FD-100215',
              ticketTitle: 'Production AWS IAM Admin Policy Grant for Infrastructure Migration',
              ticketDescription:
                'Requires temporary AdministratorAccess role on AWS account for Terraform EKS cluster deployment.',
              ticketType: 'SERVICE_REQUEST',
              priority: 'HIGH',
              ticketStatus: 'PENDING_APPROVAL',
              requestedByUserId: 'e0000000-0000-0000-0000-000000000001',
              requestedByEmail: 'developer@flowdesk.test',
              status: 'PENDING',
              decidedByUserId: null,
              decidedByEmail: null,
              decisionNote: null,
              requestedAt: '2026-11-02T09:00:00Z',
              decidedAt: null,
              responseDueAt: '2026-11-02T13:00:00Z',
              resolutionDueAt: '2026-11-03T09:00:00Z',
            },
            {
              id: 'app-002',
              ticketId: 't-002',
              ticketNumber: 'FD-100216',
              ticketTitle: 'Emergency Production Database Schema Migration V26',
              ticketDescription:
                'Executing non-backward-compatible column drop on customer_payment_tokens during maintenance window.',
              ticketType: 'SERVICE_REQUEST',
              priority: 'CRITICAL',
              ticketStatus: 'PENDING_APPROVAL',
              requestedByUserId: 'e0000000-0000-0000-0000-000000000001',
              requestedByEmail: 'dba@flowdesk.test',
              status: 'PENDING',
              decidedByUserId: null,
              decidedByEmail: null,
              decisionNote: null,
              requestedAt: '2026-11-02T09:30:00Z',
              decidedAt: null,
              responseDueAt: '2026-11-02T10:30:00Z',
              resolutionDueAt: '2026-11-02T13:30:00Z',
            },
          ],
          page: 0,
          size: 10,
          totalElements: 2,
          totalPages: 1,
          first: true,
          last: true,
        }),
      })
    })

    // Desktop
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/approvals')
    await expect(page.getByRole('heading', { name: /approval inbox/i })).toBeVisible()
    await expect(
      page.getByText('Production AWS IAM Admin Policy Grant for Infrastructure Migration'),
    ).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '13a-approval-inbox-desktop.png'),
      fullPage: false,
    })

    // Mobile
    await page.setViewportSize({ width: 390, height: 844 })
    await page.reload()
    await expect(page.getByRole('heading', { name: /approval inbox/i })).toBeVisible()
    await expect(
      page.getByText('Production AWS IAM Admin Policy Grant for Infrastructure Migration'),
    ).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '13b-approval-inbox-mobile.png'),
      fullPage: false,
    })
  })

  test('14: Reports & Analytics Desktop and Mobile', async ({ page }) => {
    await setupApiMocks(page, MOCK_ADMIN)
    const token = createMockToken(MOCK_ADMIN)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/reports/operational*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          dateRange: {
            startDate: '2026-10-03',
            endDate: '2026-11-02',
            generatedAt: '2026-11-02T00:00:00Z',
          },
          ticketVolume: {
            totalCreated: 52,
            totalResolved: 38,
            openBacklog: 14,
          },
          volumeByStatus: {
            NEW: 4,
            OPEN: 6,
            IN_PROGRESS: 4,
            RESOLVED: 38,
          },
          volumeByPriority: {
            CRITICAL: 5,
            HIGH: 15,
            MEDIUM: 22,
            LOW: 10,
          },
          dailyTrends: [
            { date: '2026-10-27', created: 6, resolved: 4 },
            { date: '2026-10-28', created: 8, resolved: 5 },
            { date: '2026-10-29', created: 7, resolved: 6 },
            { date: '2026-10-30', created: 9, resolved: 8 },
            { date: '2026-10-31', created: 5, resolved: 6 },
            { date: '2026-11-01', created: 8, resolved: 4 },
            { date: '2026-11-02', created: 9, resolved: 5 },
          ],
          resolutionMetrics: {
            resolvedCount: 38,
            avgDurationHours: 3.8,
            medianDurationHours: 2.6,
            avgDurationByPriorityHours: {
              CRITICAL: 1.2,
              HIGH: 2.5,
              MEDIUM: 4.8,
              LOW: 7.2,
            },
          },
          slaCompliance: {
            totalResolved: 38,
            compliantResolved: 36,
            complianceRatePercent: 94.7,
            responseBreaches: 1,
            resolutionBreaches: 1,
          },
          engineerWorkloads: [
            {
              engineerId: '11111111-1111-1111-1111-111111111111',
              engineerName: 'Sarah Support',
              engineerEmail: 'sarah@flowdesk.test',
              activeAssignedTickets: 4,
              assignedInPeriod: 22,
              resolvedInPeriod: 20,
            },
            {
              engineerId: '22222222-2222-2222-2222-222222222222',
              engineerName: 'David Chen',
              engineerEmail: 'david.chen@flowdesk.test',
              activeAssignedTickets: 3,
              assignedInPeriod: 18,
              resolvedInPeriod: 16,
            },
          ],
          kbDeflection: {
            totalArticles: 18,
            totalViews: 145,
            totalDeflections: 34,
            deflectionRatePercent: 23.4,
            topArticles: [
              {
                id: '33333333-3333-3333-3333-333333333333',
                title: 'How to Reset Your VPN Password',
                slug: 'how-to-reset-your-vpn-password',
                categoryName: 'Network',
                viewCount: 78,
                deflectionCount: 26,
              },
            ],
          },
        }),
      })
    })

    // Desktop
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/reports')
    await expect(page.getByRole('heading', { name: /operational reporting & analytics/i })).toBeVisible()
    await expect(page.getByText('52', { exact: true })).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '14a-reports-desktop.png'),
      fullPage: false,
    })

    // Mobile
    await page.setViewportSize({ width: 390, height: 844 })
    await page.reload()
    await expect(page.getByRole('heading', { name: /operational reporting & analytics/i })).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '14b-reports-mobile.png'),
      fullPage: false,
    })
  })

  test('15: SLA Dashboard Desktop and Mobile', async ({ page }) => {
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
          activeTickets: 18,
          responseBreached: 2,
          resolutionBreached: 1,
          responseCompliancePercent: 92.4,
          resolutionCompliancePercent: 89.1,
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
            {
              ticketNumber: 'FD-100288',
              eventType: 'RESOLUTION_BREACHED',
              priority: 'HIGH',
              status: 'IN_PROGRESS',
              occurredAt: '2026-11-02T09:45:00Z',
            },
          ],
        }),
      })
    })

    // Desktop
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/sla/dashboard')
    await expect(page.getByRole('heading', { name: /sla operations/i })).toBeVisible()
    await expect(page.getByText('FD-100299')).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '15a-sla-dashboard-desktop.png'),
      fullPage: false,
    })

    // Mobile
    await page.setViewportSize({ width: 390, height: 844 })
    await page.reload()
    await expect(page.getByRole('heading', { name: /sla operations/i })).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '15b-sla-dashboard-mobile.png'),
      fullPage: false,
    })
  })

  test('16: Security Audit Trail Desktop and Mobile', async ({ page }) => {
    await setupApiMocks(page, MOCK_ADMIN)
    const token = createMockToken(MOCK_ADMIN)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    const mockEvents = [
      {
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
      },
      {
        id: 'audit-002',
        action: 'AI_TRIAGE_APPLIED',
        targetType: 'TICKET',
        targetId: 't-12346',
        targetReference: 'FD-100251',
        actorUserId: 'e0000000-0000-0000-0000-000000000002',
        actorEmail: 'engineer@flowdesk.test',
        actorType: 'USER',
        occurredAt: '2026-11-02T10:15:00Z',
        metadata: {
          suggestedPriority: 'HIGH',
          suggestedCategory: 'Database',
        },
      },
      {
        id: 'audit-003',
        action: 'USER_ROLES_UPDATED',
        targetType: 'USER',
        targetId: 'u-101',
        targetReference: 'sarah.lead@flowdesk.test',
        actorUserId: MOCK_ADMIN.id,
        actorEmail: MOCK_ADMIN.email,
        actorType: 'USER',
        occurredAt: '2026-11-02T09:45:00Z',
        metadata: {
          rolesAdded: ['TEAM_LEAD'],
          rolesRemoved: [],
        },
      },
    ]

    await page.route('**/api/audit/events*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: mockEvents,
          page: 0,
          size: 20,
          totalElements: 3,
          totalPages: 1,
          first: true,
          last: true,
        }),
      })
    })

    // Desktop
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/audit')
    await expect(page.getByRole('heading', { name: /security audit trail/i })).toBeVisible()
    await expect(page.getByRole('table').getByText('FD-100250', { exact: true })).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '16a-audit-trail-desktop.png'),
      fullPage: false,
    })

    // Mobile
    await page.setViewportSize({ width: 390, height: 844 })
    await page.reload()
    await expect(page.getByRole('heading', { name: /security audit trail/i })).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '16b-audit-trail-mobile.png'),
      fullPage: false,
    })
  })

  test('17: User Management Desktop and Mobile', async ({ page }) => {
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
              email: 'sarah.support@flowdesk.test',
              firstName: 'Sarah',
              lastName: 'Support',
              roles: ['SUPPORT_ENGINEER'],
              status: 'ACTIVE',
              createdAt: '2026-10-15T09:00:00Z',
              updatedAt: '2026-10-15T09:00:00Z',
              lastLoginAt: '2026-11-02T08:30:00Z',
            },
            {
              id: 'u-102',
              email: 'alex.lead@flowdesk.test',
              firstName: 'Alex',
              lastName: 'Rivera',
              roles: ['TEAM_LEAD', 'SUPPORT_ENGINEER'],
              status: 'ACTIVE',
              createdAt: '2026-09-01T09:00:00Z',
              updatedAt: '2026-10-01T09:00:00Z',
              lastLoginAt: '2026-11-02T09:15:00Z',
            },
            {
              id: 'u-103',
              email: 'dev.admin@flowdesk.test',
              firstName: 'Devon',
              lastName: 'Admin',
              roles: ['ADMIN'],
              status: 'ACTIVE',
              createdAt: '2026-08-01T09:00:00Z',
              updatedAt: '2026-08-01T09:00:00Z',
              lastLoginAt: '2026-11-02T09:45:00Z',
            },
            {
              id: 'u-104',
              email: 'marcus.contractor@flowdesk.test',
              firstName: 'Marcus',
              lastName: 'Vance',
              roles: ['EMPLOYEE'],
              status: 'INACTIVE',
              createdAt: '2026-10-20T09:00:00Z',
              updatedAt: '2026-11-01T12:00:00Z',
              lastLoginAt: '2026-10-31T17:00:00Z',
            },
          ],
          page: 0,
          size: 20,
          totalElements: 4,
          totalPages: 1,
          first: true,
          last: true,
        }),
      })
    })

    // Desktop
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/admin/users')
    await expect(page.getByRole('heading', { name: /user management/i })).toBeVisible()
    await expect(page.getByRole('table').getByText('Sarah Support')).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '17a-user-management-desktop.png'),
      fullPage: false,
    })

    // Mobile
    await page.setViewportSize({ width: 390, height: 844 })
    await page.reload()
    await expect(page.getByRole('heading', { name: /user management/i })).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '17b-user-management-mobile.png'),
      fullPage: false,
    })
  })

  test('18: Attachment Security Workspace and Completion Report', async ({ page }) => {
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
          totalPending: 12,
          scannedCount: 12,
          cleanCount: 11,
          infectedCount: 1,
          failedCount: 0,
          message: 'Attachment batch rescan completed successfully. 1 infected attachment was quarantined.',
        }),
      })
    })

    // Desktop Initial
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/admin/attachments')
    await expect(page.getByRole('heading', { name: /attachment security & malware quarantine/i })).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '18a-attachment-security-desktop.png'),
      fullPage: false,
    })

    // Trigger Rescan
    await page.getByRole('button', { name: /trigger batch rescan/i }).click()
    await expect(page.getByRole('heading', { name: /confirm attachment rescan/i })).toBeVisible()
    await page.getByRole('button', { name: /confirm & start rescan/i }).click()
    await expect(page.getByRole('heading', { name: /batch rescan results/i })).toBeVisible()
    await expect(page.getByText('12 Scanned')).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '18b-attachment-security-report.png'),
      fullPage: false,
    })
  })

  test('19: Employee Dashboard Mobile Viewport (390x844)', async ({ page }) => {
    await setupApiMocks(page, MOCK_EMPLOYEE)
    const token = createMockToken(MOCK_EMPLOYEE)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/dashboard/stats', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          employee: {
            openTickets: 2,
            resolvedTickets: 14,
            ticketsAwaitingResponse: 1,
            pendingApprovals: 0,
          },
        }),
      })
    })

    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/dashboard')
    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible()
    await expect(page.getByText('Open Tickets')).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '19-employee-dashboard-mobile.png'),
      fullPage: false,
    })
  })

  test('20: Profile and Account Settings Desktop and Dark Mode', async ({ page }) => {
    await setupApiMocks(page, MOCK_EMPLOYEE)
    const token = createMockToken(MOCK_EMPLOYEE)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
      localStorage.setItem('flowdesk_theme', 'light')
    }, token)

    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/profile')
    await expect(page.getByRole('heading', { name: /my profile/i })).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '20a-user-profile-desktop.png'),
      fullPage: false,
    })

    await page.goto('/account')
    await expect(page.getByRole('heading', { name: /account settings/i })).toBeVisible()
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '20b-account-settings-desktop.png'),
      fullPage: false,
    })

    // Dark mode
    await page.evaluate(() => {
      localStorage.setItem('flowdesk_theme', 'dark')
      document.documentElement.setAttribute('data-theme', 'dark')
      document.documentElement.classList.remove('theme-light')
      document.documentElement.classList.add('theme-dark')
    })
    await page.waitForTimeout(400)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '20c-account-settings-dark.png'),
      fullPage: false,
    })
  })

  test('21: Samsung Galaxy A54 (412x915) Mobile Experience Suite', async ({ page }) => {
    await setupApiMocks(page, MOCK_ADMIN)
    const token = createMockToken(MOCK_ADMIN)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    // Set Samsung Galaxy A54 screen dimensions
    await page.setViewportSize({ width: 412, height: 915 })

    // A. Dashboard on Galaxy A54
    await page.goto('/dashboard')
    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible()
    await page.waitForTimeout(300)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '21a-galaxy-a54-dashboard.png'),
      fullPage: false,
    })

    // B. Mobile Drawer on Galaxy A54
    const menuBtn = page.locator('.horizon-mobile-menu-btn')
    await menuBtn.click()
    const drawerPortal = page.locator('.horizon-mobile-drawer-portal')
    await expect(drawerPortal).toBeVisible()
    await page.waitForTimeout(300)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '21b-galaxy-a54-drawer.png'),
      fullPage: false,
    })
    await page.locator('.horizon-drawer-close-btn').click()
    await expect(drawerPortal).toBeHidden()

    // C. Create Ticket with Priority Selector & Escalation Guidance
    await page.goto('/tickets/new')
    await expect(page.getByRole('heading', { name: /create a ticket/i })).toBeVisible()
    await page.waitForTimeout(300)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '21c-galaxy-a54-create-ticket.png'),
      fullPage: false,
    })

    // D. SLA Dashboard on Galaxy A54
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
    await page.waitForTimeout(300)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '21d-galaxy-a54-sla-dashboard.png'),
      fullPage: false,
    })

    // E. Reports & Analytics on Galaxy A54
    await page.route('**/api/reports/operational*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          dateRange: { startDate: '2026-10-03', endDate: '2026-11-02', generatedAt: '2026-11-02T00:00:00Z' },
          ticketVolume: { totalCreated: 52, totalResolved: 38, openBacklog: 14 },
          volumeByStatus: { OPEN: 6, IN_PROGRESS: 5, WAITING_FOR_USER: 3, RESOLVED: 38 },
          volumeByPriority: { CRITICAL: 4, HIGH: 12, MEDIUM: 26, LOW: 10 },
          dailyTrends: [],
          engineerWorkload: [],
          kbDeflection: { totalSearches: 120, totalViews: 85, totalSolvedDirectly: 24, overallDeflectionRate: 28.2, topDeflectingArticles: [] },
        }),
      })
    })
    await page.goto('/reports')
    await expect(page.getByRole('heading', { name: /operational reporting & analytics/i })).toBeVisible()
    await page.waitForTimeout(300)
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, '21e-galaxy-a54-reports.png'),
      fullPage: false,
    })
  })
})
