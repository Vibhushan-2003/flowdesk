import type { Page } from '@playwright/test'

export interface MockUser {
  id: string
  email: string
  roles: string[]
}

export const MOCK_EMPLOYEE: MockUser = {
  id: '11111111-1111-1111-1111-111111111111',
  email: 'employee@flowdesk.test',
  roles: ['EMPLOYEE'],
}

export const MOCK_SUPPORT_ENGINEER: MockUser = {
  id: '22222222-2222-2222-2222-222222222222',
  email: 'support@flowdesk.test',
  roles: ['SUPPORT_ENGINEER'],
}

export const MOCK_ADMIN: MockUser = {
  id: '33333333-3333-3333-3333-333333333333',
  email: 'admin@flowdesk.test',
  roles: ['ADMIN'],
}

export function createMockToken(user: MockUser, expiresInSeconds = 900): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const now = Math.floor(Date.now() / 1000)
  const payload = btoa(
    JSON.stringify({
      iss: 'flowdesk',
      sub: user.id,
      email: user.email,
      roles: user.roles,
      credential_version: 1,
      iat: now,
      exp: now + expiresInSeconds,
    }),
  )
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

  // Login
  await page.route('**/api/auth/login', async (route) => {
    const body = route.request().postDataJSON() as { email?: string; password?: string }
    if (body.email === currentUser.email && body.password === 'ValidPass123!') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          accessToken: createMockToken(currentUser),
          tokenType: 'Bearer',
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
}

