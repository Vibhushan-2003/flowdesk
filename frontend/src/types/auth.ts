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

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface ChangePasswordResponse {
  message: string
}

export interface ForgotPasswordRequest {
  email: string
}

export interface ForgotPasswordResponse {
  message: string
}

export interface ResetPasswordRequest {
  token: string
  newPassword: string
  confirmPassword: string
}

export interface ResetPasswordResponse {
  message: string
}

export interface AcceptInvitationRequest {
  token: string
  newPassword: string
  confirmPassword: string
}

export interface AcceptInvitationResponse {
  message: string
}

export interface GoogleOAuthConfigResponse {
  enabled: boolean
  clientId: string
  redirectUri: string
  authUri: string
}

export interface GoogleAuthRequest {
  code?: string
  idToken?: string
  state?: string
  codeVerifier?: string
  invitationToken?: string
  linkToken?: string
  flowDeskPassword?: string
}

export interface GoogleLinkAccountRequest {
  linkToken: string
  flowDeskPassword: string
}

export interface OAuthLinkingRequiredResponse {
  status: 409
  error: string
  message: string
  path: string
  linkToken: string
  email: string
  provider: string
  requestId?: string
}
