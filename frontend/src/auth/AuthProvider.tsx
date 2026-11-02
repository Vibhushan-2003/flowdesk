import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react'

import type {
  ReactNode,
} from 'react'

import {
  getCurrentUser,
  login as loginRequest,
  logoutApi,
  refreshToken as refreshSessionRequest,
} from '../api/auth'

import type {
  MeResponse,
} from '../types/auth'

import {
  AuthContext,
} from './AuthContext'

import {
  getJwtExpiresAtMs,
  isJwtExpired,
  SESSION_EXPIRED_EVENT,
  TOKEN_REFRESHED_EVENT,
} from './sessionEvents'

interface AuthProviderProps {
  children: ReactNode
}

const ACCESS_TOKEN_STORAGE_KEY = 'flowdesk_access_token'

function isStoredTokenExpired(): boolean {
  const storedToken = sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)
  return Boolean(storedToken && storedToken.trim() && isJwtExpired(storedToken))
}

function getStoredAccessToken(): string | null {
  const storedToken = sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)

  if (!storedToken || !storedToken.trim() || isJwtExpired(storedToken)) {
    return null
  }

  return storedToken
}

export function AuthProvider({
  children,
}: AuthProviderProps) {
  const [accessToken, setAccessToken] = useState<string | null>(getStoredAccessToken)
  const [user, setUser] = useState<MeResponse | null>(null)
  const [isAuthLoading, setIsAuthLoading] = useState<boolean>(true)
  const [sessionExpiredMessage, setSessionExpiredMessage] = useState<string | null>(() => {
    if (isStoredTokenExpired()) {
      return 'Your session has expired. Please sign in again.'
    }
    return null
  })

  const refreshTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const clearSessionExpiredMessage = useCallback(() => {
    setSessionExpiredMessage(null)
  }, [])

  const signOut = useCallback((message?: string): void => {
    if (refreshTimeoutRef.current) {
      clearTimeout(refreshTimeoutRef.current)
      refreshTimeoutRef.current = null
    }

    sessionStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY)
    sessionStorage.removeItem('flowdesk_ticket_draft')

    void logoutApi()

    setAccessToken(null)
    setUser(null)
    setIsAuthLoading(false)

    if (message) {
      setSessionExpiredMessage(message)
    }
  }, [])

  // 1. Initial Session Restoration & Silent Refresh on Reload
  useEffect(() => {
    let cancelled = false

    async function initSession() {
      const stored = getStoredAccessToken()

      if (stored) {
        // We have an active unexpired access token in sessionStorage
        try {
          const currentUser = await getCurrentUser(stored)
          if (!cancelled) {
            setUser(currentUser)
            setIsAuthLoading(false)
          }
          return
        } catch {
          // Fall through to silent refresh via HttpOnly cookie
        }
      }

      // No valid access token in storage: attempt silent refresh using HttpOnly cookie
      try {
        const refreshResult = await refreshSessionRequest()
        if (cancelled) return

        sessionStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, refreshResult.accessToken)
        setAccessToken(refreshResult.accessToken)

        const currentUser = await getCurrentUser(refreshResult.accessToken)
        if (cancelled) return

        setUser(currentUser)
        setIsAuthLoading(false)
      } catch {
        if (!cancelled) {
          sessionStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY)
          setAccessToken(null)
          setUser(null)
          setIsAuthLoading(false)
        }
      }
    }

    void initSession()

    return () => {
      cancelled = true
    }
  }, [])

  // 2. Proactive Refresh Timer (refreshes 60s before token expiration)
  useEffect(() => {
    if (!accessToken) {
      if (refreshTimeoutRef.current) {
        clearTimeout(refreshTimeoutRef.current)
        refreshTimeoutRef.current = null
      }
      return
    }

    const expiresAtMs = getJwtExpiresAtMs(accessToken)
    if (!expiresAtMs) return

    // Refresh 60 seconds before token expires (or immediately if already within window)
    const msUntilRefresh = Math.max(1000, expiresAtMs - Date.now() - 60000)

    if (refreshTimeoutRef.current) {
      clearTimeout(refreshTimeoutRef.current)
    }

    refreshTimeoutRef.current = setTimeout(async () => {
      try {
        const refreshResult = await refreshSessionRequest()
        sessionStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, refreshResult.accessToken)
        setAccessToken(refreshResult.accessToken)
      } catch {
        // If proactive refresh fails, authFetch will retry or handle on actual 401
      }
    }, msUntilRefresh)

    return () => {
      if (refreshTimeoutRef.current) {
        clearTimeout(refreshTimeoutRef.current)
        refreshTimeoutRef.current = null
      }
    }
  }, [accessToken])

  // 3. Listen for token refresh and session expiration events
  useEffect(() => {
    function handleTokenRefreshed(event: Event) {
      const customEvent = event as CustomEvent<{ accessToken: string }>
      if (customEvent.detail?.accessToken) {
        setAccessToken(customEvent.detail.accessToken)
      }
    }

    function handleExpiredEvent(event: Event) {
      const customEvent = event as CustomEvent<{ message?: string }>
      signOut(
        customEvent.detail?.message ||
          'Your session has expired. Please sign in again.',
      )
    }

    window.addEventListener(TOKEN_REFRESHED_EVENT, handleTokenRefreshed)
    window.addEventListener(SESSION_EXPIRED_EVENT, handleExpiredEvent)

    return () => {
      window.removeEventListener(TOKEN_REFRESHED_EVENT, handleTokenRefreshed)
      window.removeEventListener(SESSION_EXPIRED_EVENT, handleExpiredEvent)
    }
  }, [signOut])

  async function signIn(
    email: string,
    password: string,
  ): Promise<void> {
    clearSessionExpiredMessage()

    const loginResponse = await loginRequest({
      email,
      password,
    })

    const currentUser = await getCurrentUser(loginResponse.accessToken)

    // Draft isolation: discard drafts created by a different user
    const existingDraft = sessionStorage.getItem('flowdesk_ticket_draft')
    if (existingDraft) {
      try {
        const parsed = JSON.parse(existingDraft)
        if (parsed.userId && parsed.userId !== currentUser.userId) {
          sessionStorage.removeItem('flowdesk_ticket_draft')
        }
      } catch {
        sessionStorage.removeItem('flowdesk_ticket_draft')
      }
    }

    sessionStorage.setItem(
      ACCESS_TOKEN_STORAGE_KEY,
      loginResponse.accessToken,
    )

    setAccessToken(loginResponse.accessToken)
    setUser(currentUser)
  }

  async function setSessionToken(
    token: string,
  ): Promise<void> {
    clearSessionExpiredMessage()

    const currentUser = await getCurrentUser(token)

    const existingDraft = sessionStorage.getItem('flowdesk_ticket_draft')
    if (existingDraft) {
      try {
        const parsed = JSON.parse(existingDraft)
        if (parsed.userId && parsed.userId !== currentUser.userId) {
          sessionStorage.removeItem('flowdesk_ticket_draft')
        }
      } catch {
        sessionStorage.removeItem('flowdesk_ticket_draft')
      }
    }

    sessionStorage.setItem(
      ACCESS_TOKEN_STORAGE_KEY,
      token,
    )

    setAccessToken(token)
    setUser(currentUser)
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        accessToken,
        isAuthenticated: accessToken !== null && user !== null,
        isAuthLoading,
        sessionExpiredMessage,
        signIn,
        setSessionToken,
        signOut,
        clearSessionExpiredMessage,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}
