import { test, expect } from '@playwright/test'
import {
  MOCK_EMPLOYEE,
  createMockToken,
  setupApiMocks,
} from './fixtures/mockApi'

test.describe('Knowledge Base & Self-Service Deflection Journeys', () => {
  test.beforeEach(async ({ page }) => {
    await setupApiMocks(page, MOCK_EMPLOYEE)

    const token = createMockToken(MOCK_EMPLOYEE)
    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, token)
  })

  test('Journey 9: Knowledge Base search and article display', async ({ page }) => {
    await page.route('**/api/kb/categories', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          { id: 'cat-1', name: 'Network & Connectivity', description: 'VPN, Wi-Fi, Ethernet' },
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
              title: 'How to configure Corporate VPN',
              slug: 'how-to-configure-corporate-vpn',
              summary: 'Step by step VPN setup guide for macOS and Windows',
              categoryName: 'Network & Connectivity',
            },
          ],
          totalElements: 1,
          totalPages: 1,
          size: 10,
          number: 0,
        }),
      })
    })

    await page.goto('/kb')

    await expect(page.getByRole('heading', { name: /knowledge base/i })).toBeVisible()
    await expect(page.getByRole('button', { name: /network & connectivity/i })).toBeVisible()
    await expect(page.getByText('How to configure Corporate VPN')).toBeVisible()
  })

  test('Journey 10: Ticket deflection flow via "Solved My Issue" button deflects ticket creation', async ({
    page,
  }) => {
    await page.route('**/api/kb/articles/deflect?*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'art-vpn',
            title: 'VPN Connection Troubleshooting Guide',
            slug: 'vpn-troubleshooting',
            summary: 'Resolve authentication and certificate errors with corporate VPN',
            categoryName: 'Network & Connectivity',
          },
        ]),
      })
    })

    await page.goto('/tickets/new')

    // Type matching title to trigger deflection suggestion
    await page.getByLabel(/title/i).fill('Cannot connect to VPN')
    await page.getByLabel(/description/i).fill('Failing to establish tunnel to corporate gateway')

    // Expect deflection card suggestion
    await expect(page.getByText(/suggested solutions/i)).toBeVisible()
    await expect(page.getByText('VPN Connection Troubleshooting Guide')).toBeVisible()

    // Click "Solved My Issue"
    const solvedBtn = page.getByRole('button', { name: /solved my issue/i })
    await expect(solvedBtn).toBeVisible()
    await solvedBtn.click()

    // Expect deflection panel confirmation
    await expect(page.getByRole('heading', { name: /issue resolved via knowledge base/i })).toBeVisible()
    await expect(page.getByText(/no ticket was submitted to the it support queue/i)).toBeVisible()
  })
})

