export type TicketType =
  | 'INCIDENT'
  | 'SERVICE_REQUEST'

export type TicketPriority =
  | 'LOW'
  | 'MEDIUM'
  | 'HIGH'
  | 'CRITICAL'

export type TicketStatus =
  | 'OPEN'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'WAITING_FOR_USER'
  | 'RESOLVED'
  | 'CLOSED'
  | 'CANCELLED'

export type SlaStatus =
  | 'PENDING'
  | 'MET'
  | 'BREACHED'
  | 'UNKNOWN'

export interface CreateTicketRequest {
  type: TicketType
  title: string
  description: string
}

export interface TicketResponse {
  id: string
  ticketNumber: string
  type: TicketType
  title: string
  description: string
  priority: TicketPriority
  status: TicketStatus
  createdByUserId: string
  createdByEmail: string
  createdAt: string
  updatedAt: string
  responseDueAt: string
  resolutionDueAt: string
  firstRespondedAt: string | null
  resolvedAt: string | null
  responseSlaStatus: SlaStatus
  resolutionSlaStatus: SlaStatus
}

export interface TicketSummary {
  id: string
  ticketNumber: string
  type: TicketType
  title: string
  priority: TicketPriority
  status: TicketStatus
  createdAt: string
  updatedAt: string
  responseDueAt: string
  resolutionDueAt: string
  responseSlaStatus: SlaStatus
  resolutionSlaStatus: SlaStatus
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface ClaimTicketResponse {
  assignmentId: string
  ticketId: string
  ticketNumber: string
  status: TicketStatus
  assignedToUserId: string
  assignedToEmail: string
  assignedAt: string
}

export interface SupportTicketSummary {
  ticketId: string
  ticketNumber: string
  type: TicketType
  title: string
  priority: TicketPriority
  status: TicketStatus
  createdAt: string
  assignedAt: string
  responseDueAt: string
  resolutionDueAt: string
  responseSlaStatus: SlaStatus
  resolutionSlaStatus: SlaStatus
}

export interface SupportTicketResponse {
  ticketId: string
  ticketNumber: string
  type: TicketType
  title: string
  description: string
  priority: TicketPriority
  status: TicketStatus
  createdByUserId: string
  createdByEmail: string
  createdAt: string
  updatedAt: string
  assignedAt: string
  responseDueAt: string
  resolutionDueAt: string
  firstRespondedAt: string | null
  resolvedAt: string | null
  responseSlaStatus: SlaStatus
  resolutionSlaStatus: SlaStatus
}

export interface UpdateTicketStatusRequest {
  status: TicketStatus
}

export interface CreateTicketCommentRequest {
  body: string
}

export interface TicketCommentResponse {
  commentId: string
  authorUserId: string
  authorName: string
  body: string
  createdAt: string
}
