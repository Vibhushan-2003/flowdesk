export type Role =
  | 'EMPLOYEE'
  | 'SUPPORT_ENGINEER'
  | 'TEAM_LEAD'
  | 'ADMIN'

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  userId: string
  email: string
  roles: Role[]
}

export interface MeResponse {
  userId: string
  email: string
  roles: Role[]
}