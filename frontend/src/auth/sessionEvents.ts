export const SESSION_EXPIRED_EVENT = 'flowdesk:session-expired'
export const TOKEN_REFRESHED_EVENT = 'flowdesk:token-refreshed'

let lastExpiryNotification = 0

export function notifySessionExpired(
  message = 'Your session has expired. Please sign in again.',
): void {
  const now = Date.now()
  // Debounce rapid multiple 401 notifications (within 1.5s window)
  if (now - lastExpiryNotification < 1500) {
    return
  }
  lastExpiryNotification = now

  if (typeof window !== 'undefined') {
    window.dispatchEvent(
      new CustomEvent(SESSION_EXPIRED_EVENT, {
        detail: { message },
      }),
    )
  }
}

export function getJwtExpiresAtMs(token: string | null): number | null {
  if (!token || !token.trim()) {
    return null
  }
  try {
    const parts = token.split('.')
    if (parts.length !== 3) {
      return null
    }
    let base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    while (base64.length % 4 !== 0) {
      base64 += '='
    }
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join(''),
    )
    const payload = JSON.parse(json) as { exp?: unknown }
    if (typeof payload.exp !== 'number') {
      return null
    }
    return payload.exp * 1000
  } catch {
    return null
  }
}

export function isJwtExpired(token: string | null): boolean {
  const expiresAt = getJwtExpiresAtMs(token)
  if (expiresAt === null) {
    return true
  }
  // Consider expired if within 5 seconds of expiration timestamp
  return expiresAt <= Date.now() + 5000
}
