import { API_BASE_URL } from './config'
import type {
  AdminCreateUserRequest,
  AdminUserResponse,
  UpdateUserRolesRequest,
  UpdateUserStatusRequest,
  UserDirectoryFilter,
  UserDirectoryPage,
} from '../types/userManagement'

async function extractErrorMessage(response: Response, defaultMessage: string): Promise<string> {
  try {
    const errorBody = await response.json()
    if (errorBody && typeof errorBody.message === 'string' && errorBody.message.trim()) {
      return errorBody.message
    }
  } catch {
    // Non-JSON or empty response
  }
  return defaultMessage
}

export async function getUsers(
  accessToken: string,
  page = 0,
  size = 20,
  filters?: UserDirectoryFilter,
): Promise<UserDirectoryPage> {
  const searchParams = new URLSearchParams({
    page: String(page),
    size: String(size),
  })

  if (filters?.search && filters.search.trim()) {
    searchParams.set('search', filters.search.trim())
  }

  if (filters?.role) {
    searchParams.set('role', filters.role)
  }

  if (filters?.status) {
    searchParams.set('status', filters.status)
  }

  const response = await fetch(`${API_BASE_URL}/api/admin/users?${searchParams.toString()}`, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  })

  if (!response.ok) {
    const message = await extractErrorMessage(response, 'Unable to load users. Please try again.')
    throw new Error(message)
  }

  return response.json() as Promise<UserDirectoryPage>
}

export async function getUserById(
  accessToken: string,
  userId: string,
): Promise<AdminUserResponse> {
  const response = await fetch(`${API_BASE_URL}/api/admin/users/${userId}`, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  })

  if (!response.ok) {
    const message = await extractErrorMessage(response, 'Unable to load user details.')
    throw new Error(message)
  }

  return response.json() as Promise<AdminUserResponse>
}

export async function createUser(
  accessToken: string,
  request: AdminCreateUserRequest,
): Promise<AdminUserResponse> {
  const response = await fetch(`${API_BASE_URL}/api/admin/users`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    const message = await extractErrorMessage(response, 'Failed to create user.')
    throw new Error(message)
  }

  return response.json() as Promise<AdminUserResponse>
}

export async function updateUserRoles(
  accessToken: string,
  userId: string,
  request: UpdateUserRolesRequest,
): Promise<AdminUserResponse> {
  const response = await fetch(`${API_BASE_URL}/api/admin/users/${userId}/roles`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    const message = await extractErrorMessage(response, 'Failed to update user roles.')
    throw new Error(message)
  }

  return response.json() as Promise<AdminUserResponse>
}

export async function updateUserStatus(
  accessToken: string,
  userId: string,
  request: UpdateUserStatusRequest,
): Promise<AdminUserResponse> {
  const response = await fetch(`${API_BASE_URL}/api/admin/users/${userId}/status`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    const message = await extractErrorMessage(response, 'Failed to update user status.')
    throw new Error(message)
  }

  return response.json() as Promise<AdminUserResponse>
}
