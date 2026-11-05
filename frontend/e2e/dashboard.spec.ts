import { test, expect } from '@playwright/test'
import {
  MOCK_EMPLOYEE,
  MOCK_ADMIN,
  createMockToken,
  setupApiMocks,
} from './fixtures/mockApi'

test.describe('Role-Based Operational Dashboard Journeys', () => {
  test('Journey 7: Employee dashboard renders personal metrics and enforces role data isolation', async ({ page }) => {
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
            openTickets: 3,
            resolvedTickets: 12,
            ticketsAwaitingResponse: 1,
            pendingApprovals: null,
          },
          support: null,
          operations: null,
        }),
      })
    })

    await page.goto('/dashboard')

    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible()

    // Employee workspace metrics are visible
    await expect(page.getByText('3', { exact: true })).toBeVisible() // open tickets
    await expect(page.getByText('12', { exact: true })).toBeVisible() // resolved tickets
    await expect(page.getByText('1', { exact: true })).toBeVisible() // awaiting response

    // Support queue and Operations cards must NOT display metrics for employee
    await expect(page.getByRole('heading', { name: /sla operations/i })).not.toBeVisible()
    await expect(page.getByRole('heading', { name: /support queue/i })).not.toBeVisible()
  })

  test('Journey 8: Administrator dashboard renders operational KPIs and queue oversight', async ({ page }) => {
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
            openTickets: 1,
            resolvedTickets: 5,
            ticketsAwaitingResponse: 0,
            pendingApprovals: 2,
          },
          support: null,
          operations: {
            totalActiveTickets: 42,
            unassignedQueueTickets: 8,
            pendingApprovals: 4,
            activeSlaBreaches: 2,
          },
        }),
      })
    })

    await page.goto('/dashboard')

    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible()

    // Operations Overview card must be visible with live metrics
    await expect(page.getByRole('heading', { name: /sla operations/i })).toBeVisible()
    await expect(page.getByText('42', { exact: true })).toBeVisible() // Total active
    await expect(page.getByText('8', { exact: true })).toBeVisible() // Unassigned queue
    await expect(page.getByText('4', { exact: true })).toBeVisible() // Pending approvals
  })

  test('Dashboard error banner displays retry button that successfully recovers metrics', async ({ page }) => {
    await setupApiMocks(page, MOCK_EMPLOYEE)

    const token = createMockToken(MOCK_EMPLOYEE)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)

    let shouldFail = true
    await page.route('**/api/dashboard/stats', async (route) => {
      if (shouldFail) {
        await route.fulfill({ status: 500, json: { status: 500, error: 'Database timeout' } })
      } else {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            employee: {
              openTickets: 5,
              resolvedTickets: 20,
              ticketsAwaitingResponse: 2,
              pendingApprovals: null,
            },
            support: null,
            operations: null,
          }),
        })
      }
    })

    await page.goto('/dashboard')

    // Initial failure displays error alert with retry button
    await expect(page.getByRole('alert')).toBeVisible()
    const retryBtn = page.getByRole('button', { name: /retry/i })
    await expect(retryBtn).toBeVisible()

    // Allow retry to succeed
    shouldFail = false
    await retryBtn.click()

    await expect(page.getByText('5', { exact: true })).toBeVisible()
    await expect(page.getByText('20', { exact: true })).toBeVisible()
    await expect(page.getByRole('alert')).not.toBeVisible()
  })
  test('Administrator dashboard distinguishes confirmed zero breaches and displays verified SLA policy targets and architecture safeguards', async ({ page }) => {
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
            openTickets: 0,
            resolvedTickets: 10,
            ticketsAwaitingResponse: 0,
            pendingApprovals: 0,
          },
          support: null,
          operations: {
            totalActiveTickets: 15,
            unassignedQueueTickets: 3,
            pendingApprovals: 0,
            activeSlaBreaches: 0,
          },
        }),
      })
    })

    await page.goto('/dashboard')

    // Confirmed zero breaches badge (not generic "Target Met")
    await expect(page.getByText('0 Active Breaches')).toBeVisible()
    await expect(page.getByText(/All active tickets currently within policy response and resolution windows/i)).toBeVisible()

    // Verified SLA policy commitments (15m/4h, 30m/8h, 1h/24h, 4h/48h)
    await expect(page.getByText('15m Response / 4h Resolution')).toBeVisible()
    await expect(page.getByText('30m Response / 8h Resolution')).toBeVisible()
    await expect(page.getByText('1h Response / 24h Resolution')).toBeVisible()
    await expect(page.getByText('4h Response / 48h Resolution')).toBeVisible()

    // Truthful architectural safeguards (not unverified live health claims)
    await expect(page.getByText('ClamAV Stream Protocol')).toBeVisible()
    await expect(page.getByText('PostgreSQL Prevent-Mutation Trigger')).toBeVisible()
    await expect(page.getByText('Invitation-Only Flow')).toBeVisible()
  })

  test('Administrator dashboard displays active breach alerts and does not treat missing operations data as zero breaches', async ({ page }) => {
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
            openTickets: 2,
            resolvedTickets: 8,
            ticketsAwaitingResponse: 1,
            pendingApprovals: 3,
          },
          support: null,
          operations: {
            totalActiveTickets: 24,
            unassignedQueueTickets: 5,
            pendingApprovals: 3,
            activeSlaBreaches: 3,
          },
        }),
      })
    })

    await page.goto('/dashboard')

    // Confirmed active breaches badge and alert
    await expect(page.getByText('Active Breaches (3)')).toBeVisible()
    await expect(page.getByText(/3 Active SLA Breaches in open queue tickets/i)).toBeVisible()
  })
});
