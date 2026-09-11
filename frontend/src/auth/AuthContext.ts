import { createContext } from 'react'
import type { MeResponse } from '../types/auth'

export interface AuthContextValue {
  user: MeResponse | null
  accessToken: string | null
  isAuthenticated: boolean
  signIn: (email: string, password: string) => Promise<void>
  signOut: () => void
}

export const AuthContext =
  createContext<AuthContextValue | undefined>(undefined)