import { test, expect } from '@playwright/test'
import {
  assertLiveStackReady,
  ensureE2eAccountsProvisioned,
  getResolvedBackendUrl,
  loginAndGetToken,
} from './fixtures/seedData'
import { E2E_EMPLOYEE_A } from './fixtures/testAccounts'

test.describe('Real-Stack Live: Attachments & Session Continuity', () => {
  test.beforeAll(async () => {
    const isReady = await assertLiveStackReady()
    if (!isReady) {
      test.skip(true, 'Local live-stack environment is not reachable. Start via docker compose -f docker-compose.e2e.yml up')
      return
    }
    await ensureE2eAccountsProvisioned()
  })

  test('Scenario 11: Real attachment upload and authorized download with byte integrity', async () => {
    const backendUrl = getResolvedBackendUrl()
    const token = await loginAndGetToken(backendUrl, E2E_EMPLOYEE_A)

    // 1. Create a ticket
    const ticketRes = await fetch(`${backendUrl}/api/tickets`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        type: 'INCIDENT',
        title: `Attachment Test Ticket ${Date.now()}`,
        description: 'Testing live attachment upload and download byte verification',
      }),
    })
    expect(ticketRes.status).toBe(201)
    const ticket = (await ticketRes.json()) as { ticketNumber: string }

    // 2. Upload an attachment file via Multipart
    const fileContent = `FlowDesk live attachment verification payload: ${Date.now()}`
    const blob = new Blob([fileContent], { type: 'text/plain' })
    const formData = new FormData()
    formData.append('file', blob, 'e2e-test-proof.txt')

    const uploadRes = await fetch(
      `${backendUrl}/api/tickets/${ticket.ticketNumber}/attachments`,
      {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: formData,
      },
    )
    expect(uploadRes.status).toBe(201)
    const attachmentData = (await uploadRes.json()) as { id: string; filename: string }
    expect(attachmentData.filename).toBe('e2e-test-proof.txt')

    // 3. Download the attachment and verify byte content matches exactly
    const downloadRes = await fetch(
      `${backendUrl}/api/tickets/${ticket.ticketNumber}/attachments/${attachmentData.id}/download`,
      {
        method: 'GET',
        headers: { Authorization: `Bearer ${token}` },
      },
    )
    expect(downloadRes.status).toBe(200)
    const downloadedText = await downloadRes.text()
    expect(downloadedText).toBe(fileContent)
  })

  test('Scenario 12: Session expiration detection triggers notice and enables safe reauthentication', async ({
    page,
  }) => {
    // 1. Seed an invalid/expired token in sessionStorage
    const malformedToken =
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTYiLCJleHAiOjE2MDAwMDAwMDB9.signature'

    await page.addInitScript((t) => {
      sessionStorage.setItem('flowdesk_access_token', t)
    }, malformedToken)

    // 2. Navigate to deep protected route (/tickets/new)
    await page.goto('/tickets/new')

    // 3. Client auth interceptor detects expired token and redirects to login with alert notice
    await expect(page).toHaveURL(/.*login/)
    await expect(page.getByRole('alert')).toBeVisible()
    await expect(page.getByRole('alert')).toContainText(/session has expired/i)

    // 4. Re-authenticating with valid credentials redirects back to requested deep path (/tickets/new)
    await page.getByLabel(/email/i).fill(E2E_EMPLOYEE_A.email)
    await page.locator('#login-password').fill(E2E_EMPLOYEE_A.password)
    await page.getByRole('button', { name: /sign in/i }).click()

    await expect(page).toHaveURL(/.*tickets\/new/)
    await expect(page.getByRole('heading', { name: /create a ticket/i })).toBeVisible()
  })

  test('Scenario 13: Real refresh-token cookie rotation and session continuity', async () => {
    const backendUrl = getResolvedBackendUrl()

    // 1. Authenticate with real credentials
    const loginRes = await fetch(`${backendUrl}/api/auth/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Origin: 'http://127.0.0.1:3000',
        'X-FlowDesk-Client': 'Playwright-Live',
      },
      body: JSON.stringify({
        email: E2E_EMPLOYEE_A.email,
        password: E2E_EMPLOYEE_A.password,
      }),
    })
    expect(loginRes.status).toBe(200)

    // Inspect Set-Cookie header
    const setCookie = loginRes.headers.get('set-cookie') || ''
    expect(setCookie).toContain('flowdesk_refresh_token')
    expect(setCookie.toLowerCase()).toContain('httponly')
    expect(setCookie.toLowerCase()).toMatch(/samesite=(strict|lax)/)

    // Extract the raw cookie
    const cookieMatch = setCookie.match(/flowdesk_refresh_token=([^;]+)/)
    expect(cookieMatch).toBeTruthy()
    const refreshCookieValue = cookieMatch ? cookieMatch[1] : ''

    // 2. Perform silent refresh with the cookie and CSRF protection
    const refreshRes = await fetch(`${backendUrl}/api/auth/refresh`, {
      method: 'POST',
      headers: {
        Origin: 'http://127.0.0.1:3000',
        'X-FlowDesk-Client': 'Playwright-Live',
        Cookie: `flowdesk_refresh_token=${refreshCookieValue}`,
      },
    })
    expect(refreshRes.status).toBe(200)
    const refreshData = (await refreshRes.json()) as { accessToken: string; email: string }
    expect(refreshData.accessToken).toBeTruthy()
    expect(refreshData.email).toBe(E2E_EMPLOYEE_A.email)

    // Verify cookie was rotated
    const rotatedCookie = refreshRes.headers.get('set-cookie') || ''
    expect(rotatedCookie).toContain('flowdesk_refresh_token')
  })

  test('Scenario 14: Quarantined malware attachment upload and download rejection', async () => {
    const backendUrl = getResolvedBackendUrl()
    const token = await loginAndGetToken(backendUrl, E2E_EMPLOYEE_A)

    // 1. Create a ticket
    const ticketRes = await fetch(`${backendUrl}/api/tickets`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        type: 'INCIDENT',
        title: `Malware Quarantine Test Ticket ${Date.now()}`,
        description: 'Verifying infected file quarantine and download rejection fail-closed',
      }),
    })
    expect(ticketRes.status).toBe(201)
    const ticket = (await ticketRes.json()) as { ticketNumber: string }

    // 2. Upload simulated EICAR malware payload
    const eicarPayload = 'X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*'
    const blob = new Blob([eicarPayload], { type: 'text/plain' })
    const formData = new FormData()
    formData.append('file', blob, 'eicar-test.txt')

    const uploadRes = await fetch(
      `${backendUrl}/api/tickets/${ticket.ticketNumber}/attachments`,
      {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
        body: formData,
      },
    )
    expect(uploadRes.status).toBe(201)
    const attachmentData = (await uploadRes.json()) as {
      id: string
      filename: string
      scanDetails: string
      scanStatus: string
    }
    // Verify attachment response reports INFECTED scan status
    expect(attachmentData.scanStatus).toBe('INFECTED')

    // 3. Attempt download: must fail closed with 403 Forbidden
    const downloadRes = await fetch(
      `${backendUrl}/api/tickets/${ticket.ticketNumber}/attachments/${attachmentData.id}/download`,
      {
        method: 'GET',
        headers: { Authorization: `Bearer ${token}` },
      },
    )
    expect(downloadRes.status).toBe(403)
  })

  test('Scenario 15: Logout revokes refresh session and clears cookie', async () => {
    const backendUrl = getResolvedBackendUrl()

    // 1. Authenticate to establish session
    const loginRes = await fetch(`${backendUrl}/api/auth/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Origin: 'http://127.0.0.1:3000',
        'X-FlowDesk-Client': 'Playwright-Live',
      },
      body: JSON.stringify({
        email: E2E_EMPLOYEE_A.email,
        password: E2E_EMPLOYEE_A.password,
      }),
    })
    expect(loginRes.status).toBe(200)
    const setCookie = loginRes.headers.get('set-cookie') || ''
    const cookieMatch = setCookie.match(/flowdesk_refresh_token=([^;]+)/)
    expect(cookieMatch).toBeTruthy()
    const refreshCookieValue = cookieMatch ? cookieMatch[1] : ''

    // 2. Call logout with CSRF protection and cookie
    const logoutRes = await fetch(`${backendUrl}/api/auth/logout`, {
      method: 'POST',
      headers: {
        Origin: 'http://127.0.0.1:3000',
        'X-FlowDesk-Client': 'Playwright-Live',
        Cookie: `flowdesk_refresh_token=${refreshCookieValue}`,
      },
    })
    expect(logoutRes.status).toBe(200)

    // Inspect cookie cleared
    const clearCookieHeader = logoutRes.headers.get('set-cookie') || ''
    expect(clearCookieHeader).toContain('flowdesk_refresh_token')

    // 3. Attempting refresh with revoked cookie must return 401 Unauthorized
    const retryRefresh = await fetch(`${backendUrl}/api/auth/refresh`, {
      method: 'POST',
      headers: {
        Origin: 'http://127.0.0.1:3000',
        'X-FlowDesk-Client': 'Playwright-Live',
        Cookie: `flowdesk_refresh_token=${refreshCookieValue}`,
      },
    })
    expect(retryRefresh.status).toBe(401)
  })
})
