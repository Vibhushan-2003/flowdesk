import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e-live',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 30000,
  expect: {
    timeout: 10000,
  },
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-live-report', open: 'never' }],
    ['./e2e-live/fixtures/strictReporter.ts'],
  ],
  use: {
    baseURL: process.env.LIVE_FRONTEND_URL || 'http://127.0.0.1:3000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})

