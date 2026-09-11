import { useState } from 'react'
import type { ReactNode } from 'react'

import {
  getCurrentUser,
  login as loginRequest,
} from '../api/auth'

import type { MeResponse } from '../types/auth'
import { AuthContext } from './AuthContext'

interface AuthProviderProps {
  children: ReactNode
}

export function AuthProvider({
  children,
}: AuthProviderProps) {
  const [accessToken, setAccessToken] =
    useState<string | null>(null)

  const [user, setUser] =
    useState<MeResponse | null>(null)

  async function signIn(
    email: string,
    password: string,
  ): Promise<void> {
    const loginResponse = await loginRequest({
      email,
      password,
    })

    const currentUser = await getCurrentUser(
      loginResponse.accessToken,
    )

    setAccessToken(loginResponse.accessToken)
    setUser(currentUser)
  }

  function signOut(): void {
    setAccessToken(null)
    setUser(null)
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        accessToken,
        isAuthenticated:
          accessToken !== null && user !== null,
        signIn,
        signOut,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}