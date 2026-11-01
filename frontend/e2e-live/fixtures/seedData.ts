import type { Page } from '@playwright/test'
import {
  ADMIN_BOOTSTRAP_ACCOUNT,
  E2E_EMPLOYEE_A,
  E2E_EMPLOYEE_B,
  E2E_SUPPORT_ENGINEER,
  type TestAccount,
} from './testAccounts'

let resolvedBackendUrl: string | null = null

function assertSafeE2eTarget(url: string): void {
  try {
    const parsed = new URL(url)
    // 1. Never connect directly to database port 5432 or non-HTTP ports
    if (parsed.port === '5432') {
      throw new Error(
        `[Data Safety Violation]: E2E helper attempted to target database port 5432 directly: ${url}`,
      )
    }

    // 2. Strict Host Allowlist: must be local/CI loopback
    const allowedHosts = ['localhost', '127.0.0.1']
    if (!allowedHosts.includes(parsed.hostname)) {
      throw new Error(
        `[Data Safety Violation]: E2E helper attempted to target non-local host: ${parsed.hostname}`,
      )
    }

    // 3. Strict Port Allowlist: 8089 (E2E Compose), 3001 (E2E Frontend proxy), or 8080/3000 in CI/strict mode
    const isCiOrStrict =
      Boolean(process.env.CI) ||
      process.env.STRICT_LIVE_E2E?.trim().toLowerCase() === 'true' ||
      process.env.FLOWDESK_E2E_ENVIRONMENT === 'isolated'

    const allowedPorts = ['8089', '3001']
    if (isCiOrStrict) {
      allowedPorts.push('8080', '3000')
    }

    if (parsed.port && !allowedPorts.includes(parsed.port)) {
      throw new Error(
        `[Data Safety Violation]: Port ${parsed.port} is not in the authorized E2E port allowlist [${allowedPorts.join(', ')}].`,
      )
    }
  } catch (err) {
    if (err instanceof Error && err.message.includes('Data Safety Violation')) {
      throw err
    }
  }
}

export async function checkLiveStackHealth(candidateUrl?: string): Promise<boolean> {
  const candidates: string[] = []
  if (process.env.LIVE_BACKEND_URL) {
    candidates.push(process.env.LIVE_BACKEND_URL.replace(/\/+$/, ''))
  }
  if (candidateUrl) {
    candidates.push(candidateUrl.replace(/\/+$/, ''))
  }
  // Dedicated E2E Compose backend port
  candidates.push('http://127.0.0.1:8089')
  // CI runner backend port
  candidates.push('http://127.0.0.1:8080')

  const uniqueCandidates = [...new Set(candidates)]
  for (const url of uniqueCandidates) {
    try {
      assertSafeE2eTarget(url)
      const res = await fetch(`${url}/api/health`, { method: 'GET' })
      if (res.ok) {
        resolvedBackendUrl = url
        return true
      }
    } catch {
      // Continue probing other candidate endpoints
    }
  }
  return false
}

export function getResolvedBackendUrl(): string {
  return resolvedBackendUrl || process.env.LIVE_BACKEND_URL || 'http://127.0.0.1:8089'
}

export async function assertLiveStackReady(): Promise<boolean> {
  const isHealthy = await checkLiveStackHealth()
  if (!isHealthy) {
    const isStrict =
      Boolean(process.env.CI) ||
      process.env.STRICT_LIVE_E2E?.trim().toLowerCase() === 'true'
    if (isStrict) {
      throw new Error(
        `[E2E Release Gate Failure]: Live-stack backend is not reachable at ${getResolvedBackendUrl()}. CI release acceptance requires an active, healthy Spring Boot backend and PostgreSQL container.`,
      )
    }
    return false
  }
  return true
}

export async function loginAndGetToken(
  baseUrl: string,
  account: TestAccount,
): Promise<string> {
  const targetUrl = resolvedBackendUrl || baseUrl || getResolvedBackendUrl()
  assertSafeE2eTarget(targetUrl)

  const res = await fetch(`${targetUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: account.email,
      password: account.password,
    }),
  })

  if (!res.ok) {
    throw new Error(
      `Failed to authenticate test account ${account.email} against ${targetUrl} (HTTP ${res.status})`,
    )
  }

  const data = (await res.json()) as { accessToken: string }
  return data.accessToken
}

export async function ensureE2eAccountsProvisioned(baseUrl?: string): Promise<void> {
  const targetUrl = resolvedBackendUrl || baseUrl || getResolvedBackendUrl()
  assertSafeE2eTarget(targetUrl)

  // 1. Authenticate as bootstrap administrator
  let adminToken: string
  try {
    adminToken = await loginAndGetToken(targetUrl, ADMIN_BOOTSTRAP_ACCOUNT)
  } catch (err) {
    throw new Error(
      `Cannot authenticate bootstrap admin at ${targetUrl}. Ensure backend was started with FLOWDESK_BOOTSTRAP_ADMIN_ENABLED=true`,
      { cause: err },
    )
  }

  // 2. Provision deterministic test users via POST /api/admin/users
  const accountsToProvision = [
    E2E_EMPLOYEE_A,
    E2E_EMPLOYEE_B,
    E2E_SUPPORT_ENGINEER,
  ]

  for (const account of accountsToProvision) {
    const res = await fetch(`${targetUrl}/api/admin/users`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminToken}`,
      },
      body: JSON.stringify({
        email: account.email,
        password: account.password,
        firstName: account.firstName,
        lastName: account.lastName,
        roles: account.roles,
      }),
    })

    // 201 Created is expected; 409 Conflict / duplicate user is acceptable
    if (!res.ok && res.status !== 409 && res.status !== 400) {
      throw new Error(
        `Failed to provision E2E test account ${account.email} (HTTP ${res.status})`,
      )
    }
  }

  // 3. Provision Knowledge Base category and published article
  let categoryId: string | null = null

  // Check existing categories
  const categoriesRes = await fetch(`${targetUrl}/api/admin/kb/categories`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  })

  if (categoriesRes.ok) {
    const categories = (await categoriesRes.json()) as Array<{
      id: string
      name: string
    }>
    const existing = categories.find((c) =>
      c.name.toLowerCase().includes('network'),
    )
    if (existing) {
      categoryId = existing.id
    }
  }

  if (!categoryId) {
    const createCatRes = await fetch(`${targetUrl}/api/admin/kb/categories`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${adminToken}`,
      },
      body: JSON.stringify({
        name: 'Network & Connectivity',
        description: 'Guides for VPN, Wi-Fi, and network troubleshooting',
      }),
    })
    if (createCatRes.ok) {
      const catData = (await createCatRes.json()) as { id: string }
      categoryId = catData.id
    } else {
      throw new Error(
        `Failed to create E2E Knowledge Base category (HTTP ${createCatRes.status})`,
      )
    }
  }

  if (categoryId) {
    const supportToken = await loginAndGetToken(targetUrl, E2E_SUPPORT_ENGINEER)
    const createArticleRes = await fetch(`${targetUrl}/api/support/kb/articles`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${supportToken}`,
      },
      body: JSON.stringify({
        categoryId,
        title: 'VPN Troubleshooting Guide',
        summary: 'How to resolve VPN drops and connection issues',
        contentMarkdown:
          '# VPN Troubleshooting\n\n1. Restart the VPN client.\n2. Verify corporate credentials.\n3. Reconnect to primary gateway.',
      }),
    })

    if (createArticleRes.ok) {
      const articleData = (await createArticleRes.json()) as { id: string }
      const statusRes = await fetch(
        `${targetUrl}/api/support/kb/articles/${articleData.id}/status`,
        {
          method: 'PATCH',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${supportToken}`,
          },
          body: JSON.stringify({ status: 'PUBLISHED' }),
        },
      )
      if (!statusRes.ok) {
        throw new Error(
          `Failed to publish E2E Knowledge Base article (HTTP ${statusRes.status})`,
        )
      }
    }
  }
}

export async function loginViaUi(page: Page, account: TestAccount): Promise<void> {
  await page.goto('/login')
  await page.getByLabel(/email/i).fill(account.email)
  await page.locator('#login-password').fill(account.password)
  await page.getByRole('button', { name: /sign in/i }).click()
  await page.waitForURL(/.*dashboard/)
}
