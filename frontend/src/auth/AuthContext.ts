import { createContext } from 'react'

import type {
  MeResponse,
} from '../types/auth'

export interface AuthContextValue {
  user: MeResponse | null
  accessToken: string | null
  isAuthenticated: boolean
  isAuthLoading: boolean
  sessionExpiredMessage: string | null
  signIn: (
    email: string,
    password: string,
  ) => Promise<void>
  setSessionToken: (accessToken: string) => Promise<void>
  signOut: (message?: string) => void
  clearSessionExpiredMessage: () => void
}

export const AuthContext =
  createContext<AuthContextValue | undefined>(
    undefined,
  )
