import type { Role } from './auth'

export type UserStatus = 'ACTIVE' | 'INACTIVE' | 'INVITED'

export interface AdminUserResponse {
  id: string
  email: string
  firstName: string
  lastName: string
  status: UserStatus
  roles: Role[]
  createdAt: string
  invitationUrl?: string
}

export interface CreateUserInvitationRequest {
  email: string
  firstName: string
  lastName: string
  roles: Role[]
}

export interface ResendInvitationResponse {
  message: string
  invitationUrl?: string
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
