import type { Role } from './auth'

export type UserStatus = 'ACTIVE' | 'INACTIVE'

export interface AdminUserResponse {
  id: string
  email: string
  firstName: string
  lastName: string
  status: UserStatus
  roles: Role[]
  createdAt: string
}

export interface AdminCreateUserRequest {
  email: string
  password: string
  firstName: string
  lastName: string
  roles: Role[]
}

export interface UpdateUserRolesRequest {
  roles: Role[]
}

export interface UpdateUserStatusRequest {
  status: UserStatus
}

export interface UserDirectoryFilter {
  search?: string
  role?: Role | ''
  status?: UserStatus | ''
}

export interface UserDirectoryPage {
  content: AdminUserResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}
