import { test, expect } from '@playwright/test'
import {
  MOCK_EMPLOYEE,
  MOCK_ENGINEER,
  MOCK_ADMIN,
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

    // Mobile menu button should be visible with adequate touch target (>= 40px)
    const menuBtn = page.locator('.horizon-mobile-menu-btn')
    await expect(menuBtn).toBeVisible()
    const menuBox = await menuBtn.boundingBox()
    expect(menuBox?.width).toBeGreaterThanOrEqual(40)
    expect(menuBox?.height).toBeGreaterThanOrEqual(40)

    // Open mobile drawer
    await menuBtn.click()
    const drawerPortal = page.locator('.horizon-mobile-drawer-portal')
    await expect(drawerPortal).toBeVisible()
    await expect(drawerPortal).toHaveAttribute('aria-modal', 'true')

    // Close button touch target
    const closeBtn = page.locator('.horizon-drawer-close-btn')
    await expect(closeBtn).toBeVisible()
    const closeBox = await closeBtn.boundingBox()
    expect(closeBox?.width).toBeGreaterThanOrEqual(40)
    expect(closeBox?.height).toBeGreaterThanOrEqual(40)

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

    // Interactive buttons meet touch target guidelines (>= 40px)
    const menuBtn = page.locator('.horizon-mobile-menu-btn')
    const themeBtn = page.locator('.theme-toggle-btn')
    const bellBtn = page.locator('.notification-bell-button')
    const profileBtn = page.locator('.horizon-profile-btn')

    const menuBox = await menuBtn.boundingBox()
    expect(menuBox?.width).toBeGreaterThanOrEqual(40)
    expect(menuBox?.height).toBeGreaterThanOrEqual(40)

    const themeBox = await themeBtn.boundingBox()
    expect(themeBox?.width).toBeGreaterThanOrEqual(40)
    expect(themeBox?.height).toBeGreaterThanOrEqual(40)

    const bellBox = await bellBtn.boundingBox()
    expect(bellBox?.width).toBeGreaterThanOrEqual(40)
    expect(bellBox?.height).toBeGreaterThanOrEqual(40)

    const profileBox = await profileBtn.boundingBox()
    expect(profileBox?.width).toBeGreaterThanOrEqual(40)
    expect(profileBox?.height).toBeGreaterThanOrEqual(40)
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
})
