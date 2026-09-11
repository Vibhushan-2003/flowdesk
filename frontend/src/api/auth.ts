import { API_BASE_URL } from './config'
import type {
  LoginRequest,
  LoginResponse,
  MeResponse,
} from '../types/auth'

export async function login(
  request: LoginRequest,
): Promise<LoginResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Invalid email or password')
    }

    if (response.status === 400) {
      throw new Error('Please check the information you entered')
    }

    throw new Error('Unable to sign in. Please try again.')
  }

  return response.json() as Promise<LoginResponse>
}

export async function getCurrentUser(
  accessToken: string,
): Promise<MeResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  })

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Your session is no longer valid')
    }

    throw new Error('Unable to load your account')
  }

  return response.json() as Promise<MeResponse>
}