import { test, expect, Page } from '@playwright/test'
import {
  createMockToken,
  setupComprehensiveMocks,
} from './fixtures/mockApi'

const SUPER_ADMIN = {
  id: 'e0000000-0000-0000-0000-000000000003',
  email: 'admin@flowdesk.test',
  roles: ['ADMIN', 'SUPPORT_ENGINEER', 'TEAM_LEAD', 'EMPLOYEE'],
}

interface TestRoute {
  name: string
  path: string
  heading: RegExp
}

const PRIMARY_14_ROUTES: TestRoute[] = [
  { name: 'Dashboard', path: '/dashboard', heading: /dashboard|it self-service|operations command/i },
  { name: 'My Tickets', path: '/tickets', heading: /my tickets/i },
  { name: 'Create Ticket', path: '/tickets/new', heading: /create a ticket/i },
  { name: 'Ticket Details', path: '/tickets/FD-100201', heading: /ticket details/i },
  { name: 'Support Queue', path: '/support/queue', heading: /support queue/i },
  { name: 'Assigned Tickets', path: '/support/tickets', heading: /assigned tickets|support tickets/i },
  { name: 'Support Workbench', path: '/support/tickets/FD-100200', heading: /support ticket details/i },
  { name: 'Knowledge Base', path: '/kb', heading: /knowledge base/i },
  { name: 'Approval Inbox', path: '/approvals', heading: /approval inbox/i },
  { name: 'SLA Dashboard', path: '/sla/dashboard', heading: /sla operations/i },
  { name: 'Reports', path: '/reports', heading: /operational reporting & analytics/i },
  { name: 'Audit Trail', path: '/audit', heading: /security audit trail/i },
  { name: 'User Management', path: '/admin/users', heading: /user management/i },
  { name: 'Attachment Security', path: '/admin/attachments', heading: /attachment security & malware quarantine/i },
  { name: 'User Profile', path: '/profile', heading: /my profile/i },
  { name: 'Account Settings', path: '/account', heading: /account settings/i },
]

async function setupAuth(page: Page) {
  await setupComprehensiveMocks(page, SUPER_ADMIN)
  const token = createMockToken(SUPER_ADMIN)
  await page.addInitScript((t) => {
    sessionStorage.setItem('flowdesk_access_token', t)
  }, token)
}

test.describe('Desktop Sidebar Stationary Scrolling & Layout Architecture Suite', () => {
  test.beforeEach(async ({ page }) => {
    await setupAuth(page)
  })

  // 1. Stationary Position Verification Across All 14+ Primary Routes
  for (const route of PRIMARY_14_ROUTES) {
    test(`Desktop Sidebar remains stationary during page scroll: ${route.name} (${route.path})`, async ({
      page,
    }) => {
      await page.setViewportSize({ width: 1440, height: 900 })
      await page.goto(route.path)
      await expect(page.getByRole('heading', { name: route.heading })).toBeVisible({ timeout: 8000 })

      const sidebar = page.locator('.horizon-sidebar:not(.is-mobile)')
      const topbar = page.locator('.horizon-topbar')
      await expect(sidebar).toBeVisible()
      await expect(topbar).toBeVisible()

      // Measure pre-scroll positions
      const preScroll = await page.evaluate(() => {
        const sb = document.querySelector('.horizon-sidebar:not(.is-mobile)')!
        const tb = document.querySelector('.horizon-topbar')!
        const sbRect = sb.getBoundingClientRect()
        const tbRect = tb.getBoundingClientRect()
        const layout = document.querySelector('.horizon-app-layout')!
        const layoutComp = window.getComputedStyle(layout)
        return {
          sbTop: sbRect.top,
          sbHeight: sbRect.height,
          tbTop: tbRect.top,
          overflowX: layoutComp.overflowX,
          overflowY: layoutComp.overflowY,
          scrollHeight: document.documentElement.scrollHeight,
          clientHeight: document.documentElement.clientHeight,
        }
      })

      expect(Math.abs(preScroll.sbTop)).toBeLessThanOrEqual(1)
      expect(Math.abs(preScroll.tbTop)).toBeLessThanOrEqual(1)
      expect(preScroll.overflowX).toBe('clip')
      expect(preScroll.overflowY).toBe('visible')

      // Scroll down by 400px (or max scrollable distance)
      await page.evaluate(() => window.scrollTo(0, 400))
      await page.waitForTimeout(150)

      // Measure post-scroll positions
      const postScroll = await page.evaluate(() => {
        const sb = document.querySelector('.horizon-sidebar:not(.is-mobile)')!
        const tb = document.querySelector('.horizon-topbar')!
        const sbRect = sb.getBoundingClientRect()
        const tbRect = tb.getBoundingClientRect()
        return {
          sbTop: sbRect.top,
          sbHeight: sbRect.height,
          tbTop: tbRect.top,
          scrollY: window.scrollY,
        }
      })

      // If the page was scrollable, confirm scrollY > 0
      if (preScroll.scrollHeight > preScroll.clientHeight + 50) {
        expect(postScroll.scrollY).toBeGreaterThan(0)
      }

      // SIDEBAR AND TOPBAR MUST REMAIN STATIONARY RELATIVE TO VIEWPORT (top === 0 within 1px)
      expect(Math.abs(postScroll.sbTop)).toBeLessThanOrEqual(1)
      expect(Math.abs(postScroll.tbTop)).toBeLessThanOrEqual(1)
    })
  }

  // 2. Desktop Viewports Matrix: 1024px, 1280px, 1440px, 1920px
  const DESKTOP_VIEWPORTS = [
    { name: '1024px (Small Desktop)', width: 1024, height: 768 },
    { name: '1280px (Standard Desktop)', width: 1280, height: 800 },
    { name: '1440px (Wide Desktop)', width: 1440, height: 900 },
    { name: '1920px (Full HD)', width: 1920, height: 1080 },
  ]

  for (const vp of DESKTOP_VIEWPORTS) {
    test(`Desktop Sidebar remains stationary at viewport ${vp.name} on /tickets/new`, async ({ page }) => {
      await page.setViewportSize({ width: vp.width, height: vp.height })
      await page.goto('/tickets/new')
      await expect(page.getByRole('heading', { name: /create a ticket/i })).toBeVisible()

      const sidebar = page.locator('.horizon-sidebar:not(.is-mobile)')
      await expect(sidebar).toBeVisible()

      // Scroll 500px down
      await page.evaluate(() => window.scrollTo(0, 500))
      await page.waitForTimeout(150)

      const rect = await sidebar.evaluate((el) => {
        const r = el.getBoundingClientRect()
        return {
          top: r.top,
          bottom: r.bottom,
          height: r.height,
          scrollY: window.scrollY,
          scrollHeight: document.documentElement.scrollHeight,
          clientHeight: document.documentElement.clientHeight,
        }
      })

      if (rect.scrollHeight > rect.clientHeight + 20) {
        expect(rect.scrollY).toBeGreaterThan(0)
      }
      expect(Math.abs(rect.top)).toBeLessThanOrEqual(1)
      expect(Math.abs(rect.height - vp.height)).toBeLessThanOrEqual(2)
    })
  }

  // 3. Expanded (256px) and Collapsed (72px) Sidebar Modes
  test('Preserves expanded (256px) and collapsed (72px) modes with stationary scrolling', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/tickets/new')
    await expect(page.getByRole('heading', { name: /create a ticket/i })).toBeVisible()

    const sidebar = page.locator('.horizon-sidebar:not(.is-mobile)')
    await expect(sidebar).toBeVisible()

    // Default expanded mode width
    const expandedWidth = await sidebar.evaluate((el) => el.getBoundingClientRect().width)
    expect(Math.round(expandedWidth)).toBe(256)

    // Scroll down 350px in expanded mode
    await page.evaluate(() => window.scrollTo(0, 350))
    await page.waitForTimeout(150)
    let rect = await sidebar.evaluate((el) => el.getBoundingClientRect())
    expect(Math.abs(rect.top)).toBeLessThanOrEqual(1)

    // Toggle collapse
    const collapseBtn = page.getByRole('button', { name: /collapse sidebar/i })
    await collapseBtn.click()

    await expect(sidebar).toHaveClass(/is-collapsed/)
    await page.waitForTimeout(250) // wait for width transition

    // Collapsed mode width must be 72px
    const collapsedWidth = await sidebar.evaluate((el) => el.getBoundingClientRect().width)
    expect(Math.round(collapsedWidth)).toBe(72)

    // Scroll down further
    await page.evaluate(() => window.scrollTo(0, 500))
    await page.waitForTimeout(150)
    rect = await sidebar.evaluate((el) => el.getBoundingClientRect())
    expect(Math.abs(rect.top)).toBeLessThanOrEqual(1)

    // Expand back
    const expandBtn = page.getByRole('button', { name: /expand sidebar/i })
    await expandBtn.click()
    await expect(sidebar).not.toHaveClass(/is-collapsed/)
    await page.waitForTimeout(250)

    const reExpandedWidth = await sidebar.evaluate((el) => el.getBoundingClientRect().width)
    expect(Math.round(reExpandedWidth)).toBe(256)
    rect = await sidebar.evaluate((el) => el.getBoundingClientRect())
    expect(Math.abs(rect.top)).toBeLessThanOrEqual(1)
  })

  // 4. Short Viewports: Sidebar Nav Independently Scrollable; Header & Footer Anchored
  test('Sidebar navigation scrolls independently in short viewports without displacing header or footer', async ({
    page,
  }) => {
    // Short viewport 1280x480
    await page.setViewportSize({ width: 1280, height: 480 })
    await page.goto('/dashboard')
    await expect(page.getByRole('heading', { name: /sla operations/i })).toBeVisible()

    const sidebar = page.locator('.horizon-sidebar:not(.is-mobile)')
    const nav = sidebar.locator('.horizon-sidebar-nav')
    const header = sidebar.locator('.horizon-sidebar-header')
    const footer = sidebar.locator('.horizon-sidebar-footer')

    await expect(sidebar).toBeVisible()
    await expect(header).toBeVisible()
    await expect(footer).toBeVisible()

    // Check nav is scrollable internally
    const navScrollability = await nav.evaluate((el) => {
      const scrollable = el.scrollHeight > el.clientHeight
      const initialScrollTop = el.scrollTop
      el.scrollTop = 120
      const scrolledTop = el.scrollTop
      return {
        scrollable,
        initialScrollTop,
        scrolledTop,
        scrollHeight: el.scrollHeight,
        clientHeight: el.clientHeight,
      }
    })

    expect(navScrollability.scrollable).toBe(true)
    expect(navScrollability.scrolledTop).toBeGreaterThan(0)

    // Header remains pinned at top: 0
    const headerRect = await header.evaluate((el) => el.getBoundingClientRect())
    expect(Math.abs(headerRect.top)).toBeLessThanOrEqual(1)

    // Footer remains anchored at viewport bottom: 480
    const footerRect = await footer.evaluate((el) => el.getBoundingClientRect())
    expect(Math.abs(footerRect.bottom - 480)).toBeLessThanOrEqual(1)

    // Now scroll the main window
    await page.evaluate(() => window.scrollTo(0, 300))
    await page.waitForTimeout(150)

    const sidebarRectAfterScroll = await sidebar.evaluate((el) => el.getBoundingClientRect())
    expect(Math.abs(sidebarRectAfterScroll.top)).toBeLessThanOrEqual(1)
  })

  // 5. Sidebar Footer Anchoring and Reachability
  test('Sidebar footer (user profile, sign out) is anchored, reachable, and stationary', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto('/tickets/new')
    await expect(page.getByRole('heading', { name: /create a ticket/i })).toBeVisible()

    const footer = page.locator('.horizon-sidebar:not(.is-mobile) .horizon-sidebar-footer')
    await expect(footer).toBeVisible()

    // Profile link
    const profileLink = footer.locator('a[href="/profile"]')
    await expect(profileLink).toBeVisible()
    await expect(profileLink).toContainText('admin@flowdesk.test')

    // Sign out button
    const signOutBtn = footer.locator('.horizon-sidebar-signout-btn')
    await expect(signOutBtn).toBeVisible()
    await expect(signOutBtn).toHaveAttribute('aria-label', 'Sign out')

    // Scroll page down
    await page.evaluate(() => window.scrollTo(0, 500))
    await page.waitForTimeout(150)

    const footerRectAfterScroll = await footer.evaluate((el) => {
      const r = el.getBoundingClientRect()
      return { bottom: r.bottom, height: r.height }
    })

    // Footer bottom must still match viewport height 720
    expect(Math.abs(footerRectAfterScroll.bottom - 720)).toBeLessThanOrEqual(1)
  })

  // 6. High DPI / Zoom Emulation on Desktop (125% and 150%)
  test('Sidebar remains stationary under browser zoom emulation (125% and 150%)', async ({ page }) => {
    for (const scale of [1.25, 1.5]) {
      // Emulate zoom on a standard 1920x1080 display (125% = 1536x864, 150% = 1280x720)
      const zoomedWidth = Math.round(1920 / scale)
      const zoomedHeight = Math.round(1080 / scale)
      await page.setViewportSize({ width: zoomedWidth, height: zoomedHeight })
      await page.goto('/tickets/new')
      await expect(page.getByRole('heading', { name: /create a ticket/i })).toBeVisible()

      const sidebar = page.locator('.horizon-sidebar:not(.is-mobile)')
      await expect(sidebar).toBeVisible()

      // Scroll page
      await page.evaluate(() => window.scrollTo(0, 300))
      await page.waitForTimeout(150)

      const rect = await sidebar.evaluate((el) => {
        const r = el.getBoundingClientRect()
        return { top: r.top, scrollY: window.scrollY }
      })

      expect(rect.scrollY).toBeGreaterThan(0)
      expect(Math.abs(rect.top)).toBeLessThanOrEqual(1)
    }
  })
})
