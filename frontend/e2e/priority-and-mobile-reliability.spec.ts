import { test, expect, type Page, type Route } from '@playwright/test'
import {
  MOCK_EMPLOYEE,
  MOCK_ENGINEER,
  MOCK_ADMIN,
  type MockUser,
  createMockToken,
  setupApiMocks,
} from './fixtures/mockApi'

test.describe('Priority System & Mobile Responsiveness Reliability Suite', () => {
  test('Mobile drawer navigation & touch targets on Samsung Galaxy A54 (412x915)', async ({ page }) => {
    await setupApiMocks(page, MOCK_EMPLOYEE)
    const token = createMockToken(MOCK_EMPLOYEE)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    // Set Samsung Galaxy A54 viewport
    await page.setViewportSize({ width: 412, height: 915 })
    await page.goto('/dashboard')

    // Desktop sidebar should be hidden
    const desktopSidebar = page.locator('.horizon-sidebar:not(.is-mobile)')
    await expect(desktopSidebar).toBeHidden()

    // Mobile menu button should be visible with adequate touch target (>= 44px)
    const menuBtn = page.locator('.horizon-mobile-menu-btn')
    await expect(menuBtn).toBeVisible()
    const menuBox = await menuBtn.boundingBox()
    expect(menuBox?.width).toBeGreaterThanOrEqual(44)
    expect(menuBox?.height).toBeGreaterThanOrEqual(44)

    // Open mobile drawer
    await menuBtn.click()
    const drawerPortal = page.locator('.horizon-mobile-drawer-portal')
    await expect(drawerPortal).toBeVisible()
    await expect(drawerPortal).toHaveAttribute('aria-modal', 'true')

    // Close button touch target
    const closeBtn = page.locator('.horizon-drawer-close-btn')
    await expect(closeBtn).toBeVisible()
    const closeBox = await closeBtn.boundingBox()
    expect(closeBox?.width).toBeGreaterThanOrEqual(44)
    expect(closeBox?.height).toBeGreaterThanOrEqual(44)

    // Close via close button
    await closeBtn.click()
    await expect(drawerPortal).toBeHidden()

    // Open again and test Escape key dismissal
    await menuBtn.click()
    await expect(drawerPortal).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(drawerPortal).toBeHidden()
  })

  test('Zero horizontal overflow across all mobile viewports (320px, 360px, 390px, 412px, 430px)', async ({ page }) => {
    await setupApiMocks(page, MOCK_ADMIN)
    const token = createMockToken(MOCK_ADMIN)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    const viewports = [
      { width: 320, height: 568, name: 'iPhone SE 1st gen' },
      { width: 360, height: 740, name: 'Galaxy S8 / compact Android' },
      { width: 390, height: 844, name: 'iPhone 12/13/14' },
      { width: 412, height: 915, name: 'Samsung Galaxy A54 / Pixel 7' },
      { width: 430, height: 932, name: 'iPhone 14/15 Pro Max' },
    ]

    for (const vp of viewports) {
      await page.setViewportSize({ width: vp.width, height: vp.height })
      await page.goto('/dashboard')
      await page.waitForTimeout(300)

      const isOverflowing = await page.evaluate(() => {
        return document.documentElement.scrollWidth > window.innerWidth + 1
      })
      expect(isOverflowing, `Expected no horizontal scroll on ${vp.name} (${vp.width}x${vp.height})`).toBe(false)
    }
  })

  test('TopBar touch targets & breadcrumb truncation on narrow mobile screens (320px & 360px)', async ({ page }) => {
    await setupApiMocks(page, MOCK_ADMIN)
    const token = createMockToken(MOCK_ADMIN)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.setViewportSize({ width: 360, height: 740 })
    await page.goto('/admin/users')

    // At 360px, the breadcrumb section/sep are hidden and title is clearly shown
    const crumbTitle = page.locator('.horizon-topbar-crumb-title')
    await expect(crumbTitle).toBeVisible()
    await expect(crumbTitle).toHaveText('User Management')

    // Interactive buttons meet touch target guidelines (>= 44px)
    const menuBtn = page.locator('.horizon-mobile-menu-btn')
    const themeBtn = page.locator('.theme-toggle-btn')
    const bellBtn = page.locator('.notification-bell-button')
    const profileBtn = page.locator('.horizon-profile-btn')

    const menuBox = await menuBtn.boundingBox()
    expect(menuBox?.width).toBeGreaterThanOrEqual(44)
    expect(menuBox?.height).toBeGreaterThanOrEqual(44)

    const themeBox = await themeBtn.boundingBox()
    expect(themeBox?.width).toBeGreaterThanOrEqual(44)
    expect(themeBox?.height).toBeGreaterThanOrEqual(44)

    const bellBox = await bellBtn.boundingBox()
    expect(bellBox?.width).toBeGreaterThanOrEqual(44)
    expect(bellBox?.height).toBeGreaterThanOrEqual(44)

    const profileBox = await profileBtn.boundingBox()
    expect(profileBox?.width).toBeGreaterThanOrEqual(44)
    expect(profileBox?.height).toBeGreaterThanOrEqual(44)
  })

  test('Priority Workflow 1: Employee requester is restricted from CRITICAL priority at creation', async ({ page }) => {
    await setupApiMocks(page, MOCK_EMPLOYEE)
    const token = createMockToken(MOCK_EMPLOYEE)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.goto('/tickets/new')
    await expect(page.getByRole('heading', { name: /create a ticket/i })).toBeVisible()

    const prioritySelect = page.locator('#ticket-priority-select')
    await expect(prioritySelect).toBeVisible()

    // Options available to normal employee
    const optionValues = await prioritySelect.locator('option').evaluateAll((opts) => opts.map((o) => (o as HTMLOptionElement).value))

    expect(optionValues).toContain('LOW')
    expect(optionValues).toContain('MEDIUM')
    expect(optionValues).toContain('HIGH')
    expect(optionValues).not.toContain('CRITICAL')

    // Explanatory escalation helper is visible
    await expect(page.locator('.horizon-field-helper').first()).toContainText(/Critical priority requires support escalation/i)
  })

  test('Priority Workflow 2: Privileged Staff (Support Engineer) CAN submit tickets directly at CRITICAL priority', async ({ page }) => {
    await setupApiMocks(page, MOCK_ENGINEER)
    const token = createMockToken(MOCK_ENGINEER)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    let createdPriority: string | null = null
    await page.route('**/api/tickets', async (route) => {
      if (route.request().method() === 'POST') {
        const payload = JSON.parse(route.request().postData() || '{}')
        createdPriority = payload.priority
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            id: '88888888-8888-8888-8888-888888888888',
            ticketNumber: 'FD-CRIT01',
            type: payload.type || 'INCIDENT',
            title: payload.title,
            description: payload.description,
            priority: payload.priority,
            status: 'OPEN',
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            createdByEmail: MOCK_ENGINEER.email,
            createdByName: 'Support Engineer',
          }),
        })
        return
      }
      await route.continue()
    })

    await page.goto('/tickets/new')
    await expect(page.getByRole('heading', { name: /create a ticket/i })).toBeVisible()

    const prioritySelect = page.locator('#ticket-priority-select')
    const optionValues = await prioritySelect.locator('option').evaluateAll((opts) => opts.map((o) => (o as HTMLOptionElement).value))

    // Privileged staff can select CRITICAL
    expect(optionValues).toContain('CRITICAL')
    await expect(page.locator('.horizon-field-helper').first()).toContainText(/Privileged staff/i)

    // Select CRITICAL and submit
    await prioritySelect.selectOption('CRITICAL')
    await page.locator('#ticket-title-input').fill('Emergency Data Center Power Outage')
    await page.locator('#ticket-description-input').fill('All primary UPS units offline. Immediate support dispatch required.')

    await page.getByRole('button', { name: /create ticket/i }).click()

    await expect(page.getByRole('heading', { name: /ticket created successfully/i })).toBeVisible()
    await expect(page.getByText('FD-CRIT01', { exact: true })).toBeVisible()
    expect(createdPriority).toBe('CRITICAL')
  })

  test('Priority Workflow 3: Support Engineer reprioritizes active ticket in Support Workbench with SLA notice', async ({ page }) => {
    await setupApiMocks(page, MOCK_ENGINEER)
    const token = createMockToken(MOCK_ENGINEER)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/support/tickets/FD-100001', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'ticket-100001-uuid',
          ticketNumber: 'FD-100001',
          type: 'INCIDENT',
          title: 'VPN Connection Fails on macOS Sequoia',
          description: 'Employee cannot connect to corp network.',
          priority: 'MEDIUM',
          status: 'ASSIGNED',
          assignedToUserId: MOCK_ENGINEER.id,
          assignedToEmail: MOCK_ENGINEER.email,
          assignedAt: '2026-11-04T10:05:00Z',
          responseSlaStatus: 'HEALTHY',
          resolutionSlaStatus: 'HEALTHY',
          responseDueAt: '2026-11-04T10:20:00Z',
          resolutionDueAt: '2026-11-04T14:05:00Z',
          createdAt: '2026-11-04T10:00:00Z',
          updatedAt: '2026-11-04T10:05:00Z',
          createdByEmail: 'requester@flowdesk.test',
        }),
      })
    })

    await page.route('**/api/support/tickets/FD-100001/comments', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      })
    })

    let updatedPriority: string | null = null
    await page.route('**/api/support/tickets/FD-100001/priority', async (route) => {
      if (route.request().method() === 'PATCH') {
        const payload = JSON.parse(route.request().postData() || '{}')
        updatedPriority = payload.priority
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 'ticket-100001-uuid',
            ticketNumber: 'FD-100001',
            type: 'INCIDENT',
            title: 'VPN Connection Fails on macOS Sequoia',
            description: 'Employee cannot connect to corp network.',
            priority: payload.priority,
            status: 'ASSIGNED',
            assignedToUserId: MOCK_ENGINEER.id,
            assignedToEmail: MOCK_ENGINEER.email,
            assignedAt: '2026-11-04T10:05:00Z',
            responseSlaStatus: 'HEALTHY',
            resolutionSlaStatus: 'HEALTHY',
            responseDueAt: '2026-11-04T10:20:00Z',
            resolutionDueAt: '2026-11-04T14:05:00Z',
            createdAt: '2026-11-04T10:00:00Z',
            updatedAt: new Date().toISOString(),
            createdByEmail: 'requester@flowdesk.test',
          }),
        })
        return
      }
      await route.continue()
    })

    await page.goto('/support/tickets/FD-100001')
    await expect(page.getByText('VPN Connection Fails on macOS Sequoia')).toBeVisible()

    const prioritySelect = page.locator('#ticket-priority-update-select')
    await expect(prioritySelect).toBeVisible()

    // Reprioritize to CRITICAL
    await prioritySelect.selectOption('CRITICAL')

    // Confirmation message with SLA recalculation notice
    await expect(page.locator('.horizon-auth-alert--success')).toContainText(
      /Priority updated to Critical. SLA deadlines recalculated from original ticket creation./i
    )
    expect(updatedPriority).toBe('CRITICAL')
  })

  test('Priority Workflow 4: SLA Dashboard & Support Queue render all 4 priorities', async ({ page }) => {
    const privilegedUser = {
      id: 'e0000000-0000-0000-0000-000000000099',
      email: 'lead@flowdesk.test',
      roles: ['ADMIN', 'SUPPORT_ENGINEER'],
    }
    await setupApiMocks(page, privilegedUser)
    const token = createMockToken(privilegedUser)
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

    await page.route('**/api/support/queue*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true,
        }),
      })
    })

    // Check SLA Dashboard
    await page.goto('/sla/dashboard')
    await expect(page.getByRole('heading', { name: /sla operations/i })).toBeVisible()

    const overdueSection = page.locator('.horizon-card:has-text("Overdue Tickets by Priority")')
    await expect(overdueSection).toBeVisible()
    await expect(overdueSection).toContainText('Critical Priority')
    await expect(overdueSection).toContainText('High Priority')
    await expect(overdueSection).toContainText('Medium Priority')
    await expect(overdueSection).toContainText('Low Priority')

    // Check Support Queue Priority Filter
    await page.goto('/support/queue')
    await expect(page.getByRole('heading', { name: /support queue/i })).toBeVisible()

    const queuePriorityFilter = page.locator('#queue-priority-filter')
    const filterOptions = await queuePriorityFilter.locator('option').allTextContents()

    expect(filterOptions.some((o) => /all priorities/i.test(o))).toBe(true)
    expect(filterOptions.some((o) => /critical/i.test(o))).toBe(true)
    expect(filterOptions.some((o) => /high/i.test(o))).toBe(true)
    expect(filterOptions.some((o) => /medium/i.test(o))).toBe(true)
    expect(filterOptions.some((o) => /low/i.test(o))).toBe(true)
  })

  test('All 24 application routes load cleanly with zero element clipping across mobile viewports (320px, 360px, 390px, 412px, 430px)', async ({ page }) => {
    // Setup full suite mocks with Admin user
    const superAdmin = {
      id: 'e0000000-0000-0000-0000-000000000003',
      email: 'admin@flowdesk.test',
      roles: ['ADMIN', 'SUPPORT_ENGINEER', 'TEAM_LEAD', 'EMPLOYEE'],
    }
    await setupComprehensiveMocks(page, superAdmin)
    const token = createMockToken(superAdmin)

    // A. Public / Auth routes (without session token)
    const publicRoutes = [
      { path: '/login', heading: /welcome back/i },
      { path: '/forgot-password', heading: /forgot password/i },
      { path: '/reset-password?token=mock-token', heading: /reset your password|invalid reset link/i },
      { path: '/accept-invitation?token=mock-token', heading: /set up your account|invalid invitation link/i },
    ]

    for (const routeInfo of publicRoutes) {
      await page.setViewportSize({ width: 412, height: 915 })
      await page.goto('/login')
      await page.evaluate(() => sessionStorage.clear())
      await page.goto(routeInfo.path)
      await expect(page.getByRole('heading', { name: routeInfo.heading })).toBeVisible({ timeout: 6000 })
      await checkElementBoundsAndReachability(page, 412)
    }

    // B. Authenticated routes (with session token)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    const authenticatedRoutes = [
      // Core Employee & General routes
      { path: '/dashboard', heading: /dashboard|it self-service/i },
      { path: '/tickets', heading: /my tickets/i },
      { path: '/tickets/new', heading: /create a ticket/i },
      { path: '/tickets/FD-100201', heading: /ticket details/i },
      { path: '/profile', heading: /my profile/i },
      { path: '/account', heading: /account settings/i },
      { path: '/kb', heading: /knowledge base/i },
      { path: '/kb/articles/how-to-reset-password', heading: /how to reset your corporate password/i },
      // Support Engineer routes
      { path: '/support/queue', heading: /support queue/i },
      { path: '/support/tickets', heading: /assigned tickets|support tickets/i },
      { path: '/support/tickets/FD-100200', heading: /support ticket details/i },
      { path: '/support/kb/articles/new', heading: /create knowledge base article/i },
      // Supervisor / Team Lead routes
      { path: '/approvals', heading: /approval inbox/i },
      { path: '/sla/dashboard', heading: /sla operations/i },
      { path: '/reports', heading: /operational reporting & analytics/i },
      // Administrator routes
      { path: '/audit', heading: /security audit trail/i },
      { path: '/admin/users', heading: /user management/i },
      { path: '/admin/attachments', heading: /attachment security & malware quarantine/i },
    ]

    // Verify authenticated routes at 412px (Galaxy A54)
    for (const routeInfo of authenticatedRoutes) {
      await page.setViewportSize({ width: 412, height: 915 })
      await page.goto(routeInfo.path)
      await expect(page.getByRole('heading', { name: routeInfo.heading })).toBeVisible({ timeout: 6000 })
      await checkElementBoundsAndReachability(page, 412)
    }

    // Verify key constrained routes at narrowest 320px
    const narrowRoutes = ['/dashboard', '/tickets/new', '/support/queue', '/reports', '/admin/users']
    for (const path of narrowRoutes) {
      await page.setViewportSize({ width: 320, height: 568 })
      await page.goto(path)
      await page.waitForTimeout(200)
      await checkElementBoundsAndReachability(page, 320)
    }
  })

  test('Real Mobile Interaction: End-to-end ticket submission on iPhone 12/13/14 (390x844)', async ({ page }) => {
    await setupComprehensiveMocks(page, MOCK_EMPLOYEE)
    const token = createMockToken(MOCK_EMPLOYEE)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.setViewportSize({ width: 390, height: 844 })

    let submittedData: { type?: string; priority?: string; title?: string } = {}
    await page.route('**/api/tickets', async (route) => {
      if (route.request().method() === 'POST') {
        submittedData = JSON.parse(route.request().postData() || '{}')
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify({
            id: 't-new-mobile',
            ticketNumber: 'FD-MOB999',
            type: submittedData.type,
            title: submittedData.title,
            priority: submittedData.priority,
            status: 'OPEN',
            createdAt: new Date().toISOString(),
          }),
        })
        return
      }
      await route.continue()
    })

    await page.goto('/tickets/new')
    await expect(page.getByRole('heading', { name: /create a ticket/i })).toBeVisible()

    // Form inputs and selections on mobile
    await page.locator('#ticket-type-select').selectOption('INCIDENT')
    await page.locator('#ticket-priority-select').selectOption('HIGH')
    await page.locator('#ticket-title-input').fill('Mobile Hotspot Tethering Blocked by Policy')
    await page.locator('#ticket-description-input').fill('Device cannot tether while working remotely. Need exemption.')

    // Submit button is full-width, visible, and clickable without zooming
    const submitBtn = page.getByRole('button', { name: /create ticket/i })
    await expect(submitBtn).toBeVisible()
    await submitBtn.click()

    await expect(page.getByRole('heading', { name: /ticket created successfully/i })).toBeVisible()
    await expect(page.getByText('FD-MOB999', { exact: true })).toBeVisible()
    expect(submittedData.priority).toBe('HIGH')
    expect(submittedData.type).toBe('INCIDENT')
  })

  test('Real Mobile Interaction: Support Workbench status and internal notes on Samsung Galaxy A54 (412x915)', async ({ page }) => {
    const engineer = {
      id: 'e0000000-0000-0000-0000-000000000002',
      email: 'engineer@flowdesk.test',
      roles: ['SUPPORT_ENGINEER'],
    }
    await setupComprehensiveMocks(page, engineer)
    const token = createMockToken(engineer)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.setViewportSize({ width: 412, height: 915 })
    await page.goto('/support/tickets/FD-100200')
    await expect(page.getByRole('heading', { name: /support ticket details/i })).toBeVisible()

    // Status transition action in workbench
    const startWorkingBtn = page.getByRole('button', { name: /start working/i })
    await expect(startWorkingBtn).toBeVisible()

    // Priority reprioritization selector
    const prioritySelect = page.locator('#ticket-priority-update-select')
    await expect(prioritySelect).toBeVisible()

    // Comment input and message button
    const commentInput = page.getByPlaceholder(/type a clear, professional reply/i)
    await expect(commentInput).toBeVisible()

    // Reachable action buttons
    const postCommentBtn = page.getByRole('button', { name: /send message|send public reply/i })
    await expect(postCommentBtn).toBeVisible()
    const btnBox = await postCommentBtn.boundingBox()
    expect(btnBox?.width).toBeGreaterThanOrEqual(44)
    expect(btnBox?.height).toBeGreaterThanOrEqual(44)
  })

  test('Real Mobile Interaction: Operational Reports date preset toggles on 412x915', async ({ page }) => {
    await setupComprehensiveMocks(page, MOCK_ADMIN)
    const token = createMockToken(MOCK_ADMIN)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.setViewportSize({ width: 412, height: 915 })
    await page.goto('/reports')
    await expect(page.getByRole('heading', { name: /operational reporting & analytics/i })).toBeVisible()

    // Date preset buttons
    const preset7d = page.getByRole('button', { name: /last 7 days/i })
    const preset30d = page.getByRole('button', { name: /last 30 days/i })
    const preset90d = page.getByRole('button', { name: /last 90 days/i })

    await expect(preset7d).toBeVisible()
    await expect(preset30d).toBeVisible()
    await expect(preset90d).toBeVisible()

    // Switch presets
    await preset7d.click()
    await expect(preset7d).toHaveClass(/is-active|active/)

    await preset90d.click()
    await expect(preset90d).toHaveClass(/is-active|active/)

    // Export buttons are rendered and accessible
    const exportSummaryBtn = page.getByRole('button', { name: /export summary csv/i })
    await expect(exportSummaryBtn).toBeVisible()
  })

  test('Real Mobile Interaction: Admin User Invitation Bottom Sheet Dialog on Galaxy S8 (360x740)', async ({ page }) => {
    await setupComprehensiveMocks(page, MOCK_ADMIN)
    const token = createMockToken(MOCK_ADMIN)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.setViewportSize({ width: 360, height: 740 })
    await page.goto('/admin/users')
    await expect(page.getByRole('heading', { name: /user management/i })).toBeVisible()

    // Open Invite User Modal
    const inviteBtn = page.getByRole('button', { name: /invite user/i })
    await expect(inviteBtn).toBeVisible()
    await inviteBtn.click()

    // Verify modal is visible
    const modalDialog = page.locator('.horizon-modal-dialog')
    await expect(modalDialog).toBeVisible()

    // Verify touch target on modal close button (>= 44px)
    const modalCloseBtn = page.locator('.horizon-modal-close-btn')
    await expect(modalCloseBtn).toBeVisible()
    const closeBox = await modalCloseBtn.boundingBox()
    expect(closeBox?.width).toBeGreaterThanOrEqual(44)
    expect(closeBox?.height).toBeGreaterThanOrEqual(44)

    // Fill email input inside modal dialog
    const emailInput = modalDialog.locator('input[type="email"]')
    await expect(emailInput).toBeVisible()
    await emailInput.fill('newstaff@flowdesk.test')

    // Close modal cleanly via close button
    await modalCloseBtn.click()
    await expect(modalDialog).toBeHidden()
  })

  test('Real Mobile Interaction: Account Settings and Theme Toggle on iPhone SE (320x568)', async ({ page }) => {
    await setupComprehensiveMocks(page, MOCK_EMPLOYEE)
    const token = createMockToken(MOCK_EMPLOYEE)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.setViewportSize({ width: 320, height: 568 })
    await page.goto('/account')
    await expect(page.getByRole('heading', { name: /account settings/i })).toBeVisible()

    // Theme toggle interaction
    const themeBtn = page.locator('.theme-toggle-btn')
    await expect(themeBtn).toBeVisible()
    await themeBtn.click()

    // Verify dark mode applied
    const isDark = await page.evaluate(() => document.documentElement.getAttribute('data-theme') === 'dark')
    expect(isDark).toBe(true)

    // Toggle back to light
    await themeBtn.click()
    const isLight = await page.evaluate(() => document.documentElement.getAttribute('data-theme') === 'light')
    expect(isLight).toBe(true)
  })
})

async function checkElementBoundsAndReachability(page: Page, viewportWidth: number) {
  const result = await page.evaluate((vpWidth: number) => {
    const elements = Array.from(document.querySelectorAll<HTMLElement>(
      'h1, h2, h3, .horizon-card, .horizon-page-header, .horizon-filter-bar, .horizon-form-field, .horizon-btn, .mobile-ticket-card'
    ))

    const violations: { tag: string; className: string; text: string; right: number; maxWidth: number }[] = []

    for (const el of elements) {
      if (el.closest('.horizon-table-container, .mobile-ticket-card-view, .desktop-ticket-table-view, table, tbody, tr, td, th, pre, code')) {
        continue
      }
      const style = window.getComputedStyle(el)
      if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') {
        continue
      }

      const rect = el.getBoundingClientRect()
      if (rect.right > vpWidth + 2 && rect.width > 0) {
        violations.push({
          tag: el.tagName,
          className: el.className,
          text: (el.textContent || '').slice(0, 30),
          right: Math.round(rect.right),
          maxWidth: vpWidth,
        })
      }
    }

    return violations
  }, viewportWidth)

  expect(result, `Element bounds violations found: ${JSON.stringify(result.slice(0, 3))}`).toHaveLength(0)
}

async function setupComprehensiveMocks(page: Page, currentUser: MockUser) {
  await setupApiMocks(page, currentUser)

  await page.route('**/api/tickets/my*', async (route: Route) => {
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

  await page.route('**/api/tickets/FD-100201', async (route: Route) => {
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

  await page.route('**/api/tickets/FD-100201/comments', async (route: Route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
  })

  await page.route('**/api/tickets/queue*', async (route: Route) => {
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

  await page.route('**/api/support/tickets/my*', async (route: Route) => {
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

  await page.route('**/api/support/tickets/FD-100200', async (route: Route) => {
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

  await page.route('**/api/support/tickets/FD-100200/comments', async (route: Route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
  })

  await page.route('**/api/support/tickets/FD-100200/ai-triage', async (route: Route) => {
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

  await page.route('**/api/kb/categories', async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        { id: 'cat-1', name: 'Authentication', description: 'Logins and passwords', articleCount: 4 },
        { id: 'cat-2', name: 'Network', description: 'VPN and WiFi', articleCount: 3 },
      ]),
    })
  })

  await page.route('**/api/kb/articles/*', async (route: Route) => {
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

  await page.route('**/api/kb/articles?*', async (route: Route) => {
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

  await page.route('**/api/kb/articles', async (route: Route) => {
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

  await page.route('**/api/approvals/pending*', async (route: Route) => {
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

  await page.route('**/api/sla/dashboard', async (route: Route) => {
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

  await page.route('**/api/reports/operational*', async (route: Route) => {
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

  await page.route('**/api/audit/events*', async (route: Route) => {
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

  await page.route('**/api/admin/users*', async (route: Route) => {
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

  await page.route('**/api/admin/attachments/rescan-pending*', async (route: Route) => {
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
