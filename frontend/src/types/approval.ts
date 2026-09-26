export type ApprovalStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'

export type ApprovalTicketType =
  | 'INCIDENT'
  | 'SERVICE_REQUEST'

export type ApprovalTicketPriority =
  | 'LOW'
  | 'MEDIUM'
  | 'HIGH'
  | 'CRITICAL'

export type ApprovalTicketStatus =
  | 'PENDING_APPROVAL'
  | 'OPEN'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'WAITING_FOR_USER'
  | 'RESOLVED'
  | 'CLOSED'
  | 'CANCELLED'

export interface Approval {
  id: string

  ticketId: string

  ticketNumber: string

  ticketTitle: string

  ticketDescription: string

  ticketType: ApprovalTicketType

  priority: ApprovalTicketPriority

  ticketStatus: ApprovalTicketStatus

  requestedByUserId: string

  requestedByEmail: string

  status: ApprovalStatus

  decidedByUserId: string | null

  decidedByEmail: string | null

  decisionNote: string | null

  requestedAt: string

  decidedAt: string | null

  responseDueAt: string | null

  resolutionDueAt: string | null
}

export interface ApprovalsPage {
  content: Approval[]

  page: number

  size: number

  totalElements: number

  totalPages: number

  first: boolean

  last: boolean
}