import {
  useCallback,
  useEffect,
  useState,
} from 'react'

import type {
  ReactNode,
} from 'react'

import {
  getCurrentUser,
  login as loginRequest,
} from '../api/auth'

import type {
  MeResponse,
} from '../types/auth'

import {
  AuthContext,
} from './AuthContext'

import {
  isJwtExpired,
  SESSION_EXPIRED_EVENT,
} from './sessionEvents'

interface AuthProviderProps {
  children: ReactNode
}

const ACCESS_TOKEN_STORAGE_KEY =
  'flowdesk_access_token'

function isStoredTokenExpired(): boolean {
  const storedToken = sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)
  return Boolean(storedToken && storedToken.trim() && isJwtExpired(storedToken))
}

function getStoredAccessToken(): string | null {
  const storedToken =
    sessionStorage.getItem(
      ACCESS_TOKEN_STORAGE_KEY,
    )

  if (
    !storedToken ||
    !storedToken.trim() ||
    isJwtExpired(storedToken)
  ) {
    return null
  }

  return storedToken
}

export function AuthProvider({
  children,
}: AuthProviderProps) {
  const [
    accessToken,
    setAccessToken,
  ] =
    useState<string | null>(
      getStoredAccessToken,
    )

  const [
    user,
    setUser,
  ] =
    useState<MeResponse | null>(
      null,
    )

  const [
    isAuthLoading,
    setIsAuthLoading,
  ] =
    useState(
      () =>
        getStoredAccessToken() !== null,
    )

  const [
    sessionExpiredMessage,
    setSessionExpiredMessage,
  ] =
    useState<string | null>(() => {
      if (isStoredTokenExpired()) {
        return 'Your session has expired. Please sign in again.'
      }
      return null
    })

  // Clean up any stale expired token on initial mount
  useEffect(() => {
    if (isStoredTokenExpired()) {
      sessionStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY)
    }
  }, [])

  const signOut = useCallback((message?: string): void => {
    sessionStorage.removeItem(
      ACCESS_TOKEN_STORAGE_KEY,
    )
    sessionStorage.removeItem('flowdesk_ticket_draft')

    setAccessToken(
      null,
    )

    setUser(
      null,
    )

    setIsAuthLoading(
      false,
    )

    if (message) {
      setSessionExpiredMessage(message)
    }
  }, [])

  const clearSessionExpiredMessage = useCallback(() => {
    setSessionExpiredMessage(null)
  }, [])

  // Listen for centralized session expiration events triggered by API interceptors
  useEffect(() => {
    function handleExpiredEvent(event: Event) {
      const customEvent = event as CustomEvent<{ message?: string }>
      signOut(
        customEvent.detail?.message ||
          'Your session has expired. Please sign in again.',
      )
    }

    window.addEventListener(
      SESSION_EXPIRED_EVENT,
      handleExpiredEvent,
    )

    return () => {
      window.removeEventListener(
        SESSION_EXPIRED_EVENT,
        handleExpiredEvent,
      )
    }
  }, [signOut])

  useEffect(() => {
    let cancelled = false

    async function restoreSession() {
      if (!accessToken) {
        setIsAuthLoading(false)
        return
      }

      setIsAuthLoading(true)

      try {
        const currentUser =
          await getCurrentUser(
            accessToken,
          )

        if (cancelled) {
          return
        }

        setUser(
          currentUser,
        )
      } catch {
        if (cancelled) {
          return
        }

        signOut('Your session has expired. Please sign in again.')
      } finally {
        if (!cancelled) {
          setIsAuthLoading(
            false,
          )
        }
      }
    }

    void restoreSession()

    return () => {
      cancelled = true
    }
  }, [
    accessToken,
    signOut,
  ])

  async function signIn(
    email: string,
    password: string,
  ): Promise<void> {
    clearSessionExpiredMessage()

    const loginResponse =
      await loginRequest({
        email,
        password,
      })

    const currentUser =
      await getCurrentUser(
        loginResponse.accessToken,
      )

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

    setAccessToken(
      loginResponse.accessToken,
    )

    setUser(
      currentUser,
    )
  }

  async function setSessionToken(
    token: string,
  ): Promise<void> {
    clearSessionExpiredMessage()

    const currentUser =
      await getCurrentUser(
        token,
      )

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

    setAccessToken(
      token,
    )

    setUser(
      currentUser,
    )
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        accessToken,
        isAuthenticated:
          accessToken !== null &&
          user !== null,
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
