import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  testMatch: [
    'auth.spec.ts',
    'dashboard.spec.ts',
    'kb.spec.ts',
    'tickets.spec.ts',
    'reports.spec.ts',
    'security-refresh-malware.spec.ts',
    'screenshots.spec.ts',
    'admin-batch-d.spec.ts',
    'priority-and-mobile-reliability.spec.ts',
  ],
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'on-first-retry',
  },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 5173',
    url: 'http://127.0.0.1:5173',
    env: {
      VITE_API_BASE_URL: 'http://localhost:8080',
    },
    reuseExistingServer: !process.env.CI,
    timeout: 120 * 1000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
