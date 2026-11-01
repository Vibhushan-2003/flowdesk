import { test, expect } from '@playwright/test'
import {
  assertLiveStackReady,
  ensureE2eAccountsProvisioned,
  loginViaUi,
} from './fixtures/seedData'
import {
  ADMIN_BOOTSTRAP_ACCOUNT,
  E2E_EMPLOYEE_A,
} from './fixtures/testAccounts'

test.describe('Real-Stack Live: Role-Based Dashboard Metrics', () => {
  test.beforeAll(async () => {
    const isReady = await assertLiveStackReady()
    if (!isReady) {
      test.skip(true, 'Local live-stack environment is not reachable. Start via docker compose -f docker-compose.e2e.yml up')
      return
    }
    await ensureE2eAccountsProvisioned()
  })

  test('Scenario 6: Employee dashboard displays real personal metrics from live PostgreSQL', async ({
    page,
  }) => {
    await loginViaUi(page, E2E_EMPLOYEE_A)

    await page.goto('/dashboard')
    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible()

    // Employee IT support card is visible with live KPI chips
    await expect(page.getByRole('heading', { name: /it support/i })).toBeVisible()
    await expect(page.locator('.dashboard-kpi-grid').first()).toBeVisible()
    await expect(page.getByText('Open Tickets')).toBeVisible()

    // Support Engineer and SLA Operations cards must NOT be displayed for ordinary Employee role
    await expect(page.getByRole('heading', { name: /sla operations/i })).not.toBeVisible()
    await expect(page.getByRole('heading', { name: /engineer operations/i })).not.toBeVisible()
  })

  test('Scenario 7: Admin dashboard displays real organization-wide operational counts', async ({
    page,
  }) => {
    await loginViaUi(page, ADMIN_BOOTSTRAP_ACCOUNT)

    await page.goto('/dashboard')
    await expect(page.getByRole('heading', { name: /dashboard/i })).toBeVisible()

    // Operations Overview card is visible with live system statistics
    await expect(page.getByRole('heading', { name: /sla operations/i })).toBeVisible()
    await expect(page.getByText('Total Active')).toBeVisible()
    await expect(page.getByText('Unassigned Queue')).toBeVisible()
  })
})
