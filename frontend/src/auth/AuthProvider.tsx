import {
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

interface AuthProviderProps {
  children: ReactNode
}

const ACCESS_TOKEN_STORAGE_KEY =
  'flowdesk_access_token'

function getStoredAccessToken(): string | null {
  const storedToken =
    sessionStorage.getItem(
      ACCESS_TOKEN_STORAGE_KEY,
    )

  if (
    !storedToken ||
    !storedToken.trim()
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

        sessionStorage.removeItem(
          ACCESS_TOKEN_STORAGE_KEY,
        )

        setAccessToken(
          null,
        )

        setUser(
          null,
        )
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
  ])

  async function signIn(
    email: string,
    password: string,
  ): Promise<void> {
    const loginResponse =
      await loginRequest({
        email,
        password,
      })

    const currentUser =
      await getCurrentUser(
        loginResponse.accessToken,
      )

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
    const currentUser =
      await getCurrentUser(
        token,
      )

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

  function signOut(): void {
    sessionStorage.removeItem(
      ACCESS_TOKEN_STORAGE_KEY,
    )

    setAccessToken(
      null,
    )

    setUser(
      null,
    )

    setIsAuthLoading(
      false,
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
        signIn,
        setSessionToken,
        signOut,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}