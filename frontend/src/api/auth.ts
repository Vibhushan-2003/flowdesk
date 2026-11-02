import { API_BASE_URL } from './config'
import { notifySessionExpired } from '../auth/sessionEvents'
import type {
  AcceptInvitationRequest,
  AcceptInvitationResponse,
  ChangePasswordRequest,
  ChangePasswordResponse,
  ForgotPasswordRequest,
  ForgotPasswordResponse,
  GoogleAuthRequest,
  GoogleLinkAccountRequest,
  GoogleOAuthConfigResponse,
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
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-Requested-With': 'XMLHttpRequest',
      'X-FlowDesk-Client': 'web',
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

export async function refreshToken(): Promise<LoginResponse> {
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
    if (response.status === 401) {
      throw new Error('Refresh token has expired or is invalid')
    }
    throw new Error('Unable to refresh session')
  }

  return response.json() as Promise<LoginResponse>
}

export async function logoutApi(): Promise<void> {
  try {
    await fetch(`${API_BASE_URL}/api/auth/logout`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-Requested-With': 'XMLHttpRequest',
        'X-FlowDesk-Client': 'web',
      },
    })
  } catch {
    // Fail silently on logout network failure
  }
}

export async function getCurrentUser(
  accessToken: string,
): Promise<MeResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'X-Requested-With': 'XMLHttpRequest',
    },
  })

  if (!response.ok) {
    if (response.status === 401) {
      notifySessionExpired()
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
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
      'X-Requested-With': 'XMLHttpRequest',
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
      'X-Requested-With': 'XMLHttpRequest',
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
      'X-Requested-With': 'XMLHttpRequest',
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

export async function acceptInvitation(
  request: AcceptInvitationRequest,
): Promise<AcceptInvitationResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/accept-invitation`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Requested-With': 'XMLHttpRequest',
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
      throw new Error('This invitation link is invalid or has expired.')
    }

    throw new Error('Unable to complete invitation. Please try again.')
  }

  return response.json() as Promise<AcceptInvitationResponse>
}

export async function getGoogleOAuthConfig(): Promise<GoogleOAuthConfigResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/oauth2/config`, {
    method: 'GET',
    headers: {
      'X-Requested-With': 'XMLHttpRequest',
    },
  })

  if (!response.ok) {
    throw new Error('Failed to load Google OAuth configuration')
  }

  return response.json() as Promise<GoogleOAuthConfigResponse>
}

export async function googleLogin(
  request: GoogleAuthRequest,
): Promise<LoginResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/oauth2/google`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-Requested-With': 'XMLHttpRequest',
      'X-FlowDesk-Client': 'web',
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    let errorMessage = 'Google authentication failed. Please try again.'
    try {
      const errorBody = await response.json()
      if (errorBody && typeof errorBody.message === 'string') {
        errorMessage = errorBody.message
      }
    } catch {
      // ignore json parse error
    }

    const error = new Error(errorMessage) as Error & { status?: number }
    error.status = response.status
    throw error
  }

  return response.json() as Promise<LoginResponse>
}

export const completeGoogleLink = completeGoogleLinking

export async function completeGoogleLinking(
  request: GoogleLinkAccountRequest,
): Promise<LoginResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/oauth2/google/link`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-Requested-With': 'XMLHttpRequest',
      'X-FlowDesk-Client': 'web',
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    let errorMessage = 'Failed to confirm account linking. Please check your password and try again.'
    try {
      const errorBody = await response.json()
      if (errorBody && typeof errorBody.message === 'string') {
        errorMessage = errorBody.message
      }
    } catch {
      // ignore json parse error
    }

    const error = new Error(errorMessage) as Error & { status?: number }
    error.status = response.status
    throw error
  }

  return response.json() as Promise<LoginResponse>
}
