import { test, expect } from '@playwright/test'
import {
  assertLiveStackReady,
  ensureE2eAccountsProvisioned,
  loginViaUi,
} from './fixtures/seedData'
import { E2E_EMPLOYEE_A } from './fixtures/testAccounts'

test.describe('Real-Stack Live: Knowledge Base & Self-Service Deflection', () => {
  test.beforeAll(async () => {
    const isReady = await assertLiveStackReady()
    if (!isReady) {
      test.skip(true, 'Local live-stack environment is not reachable. Start via docker compose -f docker-compose.e2e.yml up')
      return
    }
    await ensureE2eAccountsProvisioned()
  })

  test('Scenario 9: Knowledge Base search returns published article from PostgreSQL', async ({
    page,
  }) => {
    await loginViaUi(page, E2E_EMPLOYEE_A)

    await page.goto('/kb')
    await expect(page.getByRole('heading', { name: /knowledge base/i })).toBeVisible()

    // Type search query
    const searchInput = page.getByPlaceholder(/search articles/i)
    await searchInput.fill('VPN')

    // Expect published article to appear in live search results
    await expect(page.getByText('VPN Troubleshooting Guide').first()).toBeVisible()

    // Click article card to navigate to detail view
    await page.getByText('VPN Troubleshooting Guide').first().click()
    await expect(page.getByRole('heading', { name: 'VPN Troubleshooting Guide' }).first()).toBeVisible()
    await expect(page.getByText(/restart the vpn client/i)).toBeVisible()
  })

  test('Scenario 10: Ticket deflection via "Solved My Issue" button avoids ticket creation', async ({
    page,
  }) => {
    await loginViaUi(page, E2E_EMPLOYEE_A)

    await page.goto('/tickets/new')
    await page.getByLabel(/title/i).fill('VPN connection issue')
    await page.getByLabel(/description/i).fill('My corporate VPN connection keeps dropping unexpectedly')

    // Deflection card shows suggested article
    await expect(page.locator('.kb-deflection-card')).toBeVisible()
    await expect(page.locator('.kb-deflection-card').getByText('VPN Troubleshooting Guide').first()).toBeVisible()

    // Click "Solved My Issue" button
    const solvedButton = page.getByRole('button', { name: /solved my issue/i })
    await expect(solvedButton).toBeVisible()
    await solvedButton.click()

    // Successfully deflected banner is shown
    await expect(
      page.getByRole('heading', { name: /issue resolved via knowledge base/i }),
    ).toBeVisible()
    await expect(page.locator('.ticket-deflected-message')).toBeVisible()
    await expect(page.getByRole('button', { name: /return to dashboard/i })).toBeVisible()
  })
})
