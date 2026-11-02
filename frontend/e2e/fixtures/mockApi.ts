import type { Page } from '@playwright/test'

export interface MockUser {
  id: string
  email: string
  roles: string[]
}

export const MOCK_EMPLOYEE: MockUser = {
  id: 'e0000000-0000-0000-0000-000000000001',
  email: 'employee@flowdesk.test',
  roles: ['EMPLOYEE'],
}

export const MOCK_ENGINEER: MockUser = {
  id: 'e0000000-0000-0000-0000-000000000002',
  email: 'engineer@flowdesk.test',
  roles: ['SUPPORT_ENGINEER'],
}

export const MOCK_ADMIN: MockUser = {
  id: 'e0000000-0000-0000-0000-000000000003',
  email: 'admin@flowdesk.test',
  roles: ['ADMIN'],
}

export function createMockToken(user: MockUser, expiresInSeconds = 3600): string {
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url')
  const now = Math.floor(Date.now() / 1000)
  const payload = Buffer.from(
    JSON.stringify({
      sub: user.id,
      email: user.email,
      roles: user.roles,
      iat: now,
      exp: now + expiresInSeconds,
    }),
  ).toString('base64url')
  return `${header}.${payload}.mock-signature`
}

export async function setupApiMocks(page: Page, currentUser: MockUser = MOCK_EMPLOYEE) {
  // Public OAuth config
  await page.route('**/api/auth/oauth2/config', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ enabled: false, clientId: '', redirectUri: '', authUri: '' }),
    })
  })

  // Auth Me
  await page.route('**/api/auth/me', async (route) => {
    const authHeader = route.request().headers()['authorization']
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      await route.fulfill({ status: 401, json: { status: 401, error: 'Authentication required' } })
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        userId: currentUser.id,
        id: currentUser.id,
        email: currentUser.email,
        roles: currentUser.roles,
      }),
    })
  })

  // Refresh Token
  await page.route('**/api/auth/refresh', async (route) => {
    const cookies = await page.context().cookies()
    const hasRefreshCookie = cookies.some((c) => c.name === 'flowdesk_refresh_token' && !!c.value)
    if (!hasRefreshCookie) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ status: 401, error: 'Refresh token is missing' }),
      })
      return
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      headers: {
        'Set-Cookie': 'flowdesk_refresh_token=mock-rotated-token; Path=/api/auth; HttpOnly; SameSite=Lax',
      },
      body: JSON.stringify({
        accessToken: createMockToken(currentUser),
        tokenType: 'Bearer',
        userId: currentUser.id,
        email: currentUser.email,
        roles: currentUser.roles,
      }),
    })
  })

  // Logout
  await page.route('**/api/auth/logout', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      headers: {
        'Set-Cookie': 'flowdesk_refresh_token=; Path=/api/auth; Max-Age=0; HttpOnly; SameSite=Lax',
      },
      body: JSON.stringify({ message: 'Logged out successfully' }),
    })
  })

  // Login
  await page.route('**/api/auth/login', async (route) => {
    const body = route.request().postDataJSON() as { email?: string; password?: string }
    if (body.email === currentUser.email && body.password === 'ValidPass123!') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: {
          'Set-Cookie': 'flowdesk_refresh_token=mock-refresh-token; Path=/api/auth; HttpOnly; SameSite=Lax',
        },
        body: JSON.stringify({
          accessToken: createMockToken(currentUser),
          tokenType: 'Bearer',
          userId: currentUser.id,
          email: currentUser.email,
          roles: currentUser.roles,
        }),
      })
    } else {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ status: 401, error: 'Invalid email or password' }),
      })
    }
  })

  // Notifications count
  await page.route('**/api/notifications/unread-count', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ unreadCount: 0 }),
    })
  })

  // Dashboard stats
  await page.route('**/api/dashboard/stats', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        openTickets: 3,
        inProgressTickets: 1,
        resolvedTickets: 5,
        criticalTickets: 0,
      }),
    })
  })
}
