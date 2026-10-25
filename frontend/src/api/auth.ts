import { API_BASE_URL } from './config'
import type {
  ChangePasswordRequest,
  ChangePasswordResponse,
  ForgotPasswordRequest,
  ForgotPasswordResponse,
  LoginRequest,
  LoginResponse,
  MeResponse,
  ResetPasswordRequest,
  ResetPasswordResponse,
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
    if (response.status === 429) {
      throw new Error('Too many login attempts. Please try again later.')
    }

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

export async function changePassword(
  accessToken: string,
  request: ChangePasswordRequest,
): Promise<ChangePasswordResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/change-password`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Your session is no longer valid. Please sign in again.')
    }

    try {
      const errorBody = await response.json()
      if (errorBody && typeof errorBody.message === 'string') {
        throw new Error(errorBody.message)
      }
    } catch (e) {
      if (e instanceof Error && e.message !== 'Unexpected end of JSON input') {
        throw e
      }
    }

    if (response.status === 400) {
      throw new Error('Please check the password information you entered')
    }

    throw new Error('Unable to change password. Please try again.')
  }

  return response.json() as Promise<ChangePasswordResponse>
}

export async function forgotPassword(
  request: ForgotPasswordRequest,
): Promise<ForgotPasswordResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/forgot-password`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    if (response.status === 429) {
      throw new Error('Too many password reset requests. Please try again later.')
    }

    try {
      const errorBody = await response.json()
      if (errorBody && typeof errorBody.message === 'string') {
        throw new Error(errorBody.message)
      }
    } catch (e) {
      if (e instanceof Error && e.message !== 'Unexpected end of JSON input') {
        throw e
      }
    }

    if (response.status === 400) {
      throw new Error('Please enter a valid email address.')
    }

    throw new Error('Unable to process password reset request. Please try again.')
  }

  return response.json() as Promise<ForgotPasswordResponse>
}

export async function resetPassword(
  request: ResetPasswordRequest,
): Promise<ResetPasswordResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/reset-password`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    if (response.status === 429) {
      throw new Error('Too many attempts. Please try again later.')
    }

    try {
      const errorBody = await response.json()
      if (errorBody && typeof errorBody.message === 'string') {
        throw new Error(errorBody.message)
      }
    } catch (e) {
      if (e instanceof Error && e.message !== 'Unexpected end of JSON input') {
        throw e
      }
    }

    if (response.status === 400) {
      throw new Error('Please check the password information you entered.')
    }

    throw new Error('Unable to reset password. Please try again or request a new reset link.')
  }

  return response.json() as Promise<ResetPasswordResponse>
}
