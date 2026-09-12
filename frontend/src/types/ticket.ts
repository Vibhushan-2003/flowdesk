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
}