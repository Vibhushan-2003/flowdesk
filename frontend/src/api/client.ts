import { API_BASE_URL } from './config'
import { notifySessionExpired, TOKEN_REFRESHED_EVENT } from '../auth/sessionEvents'

let refreshPromise: Promise<string> | null = null

const EXCLUDED_REFRESH_PATHS = [
  '/api/auth/login',
  '/api/auth/refresh',
  '/api/auth/logout',
  '/api/auth/forgot-password',
  '/api/auth/reset-password',
  '/api/auth/accept-invitation',
]

function isAuthExcludedUrl(url: string): boolean {
  return EXCLUDED_REFRESH_PATHS.some((path) => url.includes(path))
}

/**
 * Single-flight mutex to rotate refresh token without concurrent duplicate requests.
 */
export async function requestTokenRefresh(): Promise<string> {
  if (refreshPromise) {
    return refreshPromise
  }

  refreshPromise = (async () => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          'X-Requested-With': 'XMLHttpRequest',
          'X-FlowDesk-Client': 'web',
        },
      })

      if (!response.ok) {
        throw new Error('Refresh token rotation rejected')
      }

      const data = (await response.json()) as { accessToken: string }
      if (!data.accessToken) {
        throw new Error('Missing access token in refresh response')
      }

      sessionStorage.setItem('flowdesk_access_token', data.accessToken)

      if (typeof window !== 'undefined') {
        window.dispatchEvent(
          new CustomEvent(TOKEN_REFRESHED_EVENT, {
            detail: { accessToken: data.accessToken },
          }),
        )
      }

      return data.accessToken
    } catch (err) {
      sessionStorage.removeItem('flowdesk_access_token')
      notifySessionExpired('Your session has expired. Please sign in again.')
      throw err
    } finally {
      refreshPromise = null
    }
  })()

  return refreshPromise
}

/**
 * Central authenticated fetch wrapper providing:
 * - credentials: 'include' for HttpOnly refresh cookies
 * - single-flight token refresh mutex on HTTP 401
 * - retry-at-most-once for expired tokens
 * - bypass for 403 authorization failures
 */
export async function authFetch(
  input: RequestInfo | URL,
  init?: RequestInit,
): Promise<Response> {
  const url = typeof input === 'string' ? input : input.toString()

  const headers = new Headers(init?.headers || {})
  if (!headers.has('X-Requested-With')) {
    headers.set('X-Requested-With', 'XMLHttpRequest')
  }

  // Inject current access token if not explicitly provided
  if (!headers.has('Authorization')) {
    const currentToken = sessionStorage.getItem('flowdesk_access_token')
    if (currentToken && currentToken.trim()) {
      headers.set('Authorization', `Bearer ${currentToken}`)
    }
  }

  const modifiedInit: RequestInit = {
    ...init,
    credentials: 'include',
    headers,
  }

  const response = await fetch(input, modifiedInit)

  // 401 handling with auto-refresh and retry-once
  if (response.status === 401 && !isAuthExcludedUrl(url)) {
    try {
      const newAccessToken = await requestTokenRefresh()

      // Retry request with fresh access token
      const retryHeaders = new Headers(modifiedInit.headers)
      retryHeaders.set('Authorization', `Bearer ${newAccessToken}`)

      return await fetch(input, {
        ...modifiedInit,
        headers: retryHeaders,
      })
    } catch {
      return response
    }
  }

  return response
}

