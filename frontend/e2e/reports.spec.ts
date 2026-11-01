import { test, expect } from '@playwright/test'
import {
  MOCK_EMPLOYEE,
  MOCK_ADMIN,
  createMockToken,
  setupApiMocks,
} from './fixtures/mockApi'

const MOCK_REPORT_DATA = {
  dateRange: {
    startDate: '2026-10-03',
    endDate: '2026-11-02',
    generatedAt: '2026-11-02T00:00:00Z',
  },
  ticketVolume: {
    totalCreated: 48,
    totalResolved: 35,
    openBacklog: 13,
  },
  volumeByStatus: {
    NEW: 5,
    IN_PROGRESS: 8,
    RESOLVED: 35,
  },
  volumeByPriority: {
    CRITICAL: 4,
    HIGH: 14,
    MEDIUM: 20,
    LOW: 10,
  },
  dailyTrends: [
    { date: '2026-11-01', created: 3, resolved: 2 },
    { date: '2026-11-02', created: 5, resolved: 4 },
  ],
  resolutionMetrics: {
    resolvedCount: 35,
    avgDurationHours: 4.5,
    medianDurationHours: 3.2,
    avgDurationByPriorityHours: {
      CRITICAL: 1.5,
      HIGH: 3.0,
      MEDIUM: 5.5,
      LOW: 8.0,
    },
  },
  slaCompliance: {
    totalResolved: 35,
    compliantResolved: 33,
    complianceRatePercent: 94.3,
    responseBreaches: 1,
    resolutionBreaches: 1,
  },
  engineerWorkloads: [
    {
      engineerId: '11111111-1111-1111-1111-111111111111',
      engineerName: 'Sarah Support',
      engineerEmail: 'sarah@flowdesk.test',
      activeAssignedTickets: 3,
      assignedInPeriod: 20,
      resolvedInPeriod: 18,
    },
  ],
  kbDeflection: {
    totalArticles: 15,
    totalViews: 120,
    totalDeflections: 28,
    deflectionRatePercent: 23.3,
    topArticles: [
      {
        id: '22222222-2222-2222-2222-222222222222',
        title: 'How to Reset Your VPN Password',
        slug: 'how-to-reset-your-vpn-password',
        categoryName: 'Network',
        viewCount: 65,
        deflectionCount: 22,
      },
    ],
  },
}

test.describe('Operational Reporting & Analytics Journeys', () => {
  test('Admin navigates to /reports and views KPIs, charts, workloads and export options', async ({ page }) => {
    await setupApiMocks(page, MOCK_ADMIN)

    const token = createMockToken(MOCK_ADMIN)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.route('**/api/reports/operational*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_REPORT_DATA),
      })
    })

    await page.goto('/reports')

    await expect(page.getByRole('heading', { name: /Operational Reporting & Analytics/i })).toBeVisible()

    // KPI Summary Cards
    await expect(page.getByText('48', { exact: true })).toBeVisible() // Total created
    await expect(page.getByText('35', { exact: true }).first()).toBeVisible() // Total resolved
    await expect(page.getByText('4.5h', { exact: true })).toBeVisible() // Avg resolution
    await expect(page.getByText('94.3%', { exact: true })).toBeVisible() // SLA compliance
    await expect(page.getByText('28', { exact: true })).toBeVisible() // KB Deflections

    // Workload Table
    await expect(page.getByText('Sarah Support')).toBeVisible()
    await expect(page.getByText('90.0%')).toBeVisible() // 18/20 resolution rate

    // KB Deflection Impact
    await expect(page.getByText('How to Reset Your VPN Password')).toBeVisible()
    await expect(page.getByText('22', { exact: true })).toBeVisible()

    // Export Buttons are available
    await expect(page.getByRole('button', { name: /Export Summary CSV/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /Export Raw Tickets CSV/i })).toBeVisible()
  })

  test('Employee is barred from /reports and redirected safely', async ({ page }) => {
    await setupApiMocks(page, MOCK_EMPLOYEE)

    const token = createMockToken(MOCK_EMPLOYEE)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    await page.goto('/reports')

    // RoleProtectedRoute redirects unauthorized role to /dashboard
    await page.waitForURL('**/dashboard')
    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible()
  })
})

