export type SlaEventType =
  | 'RESPONSE_BREACHED'
  | 'RESOLUTION_BREACHED'

export type SlaTicketPriority =
  | 'LOW'
  | 'MEDIUM'
  | 'HIGH'
  | 'CRITICAL'

export type SlaTicketStatus =
  | 'OPEN'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'WAITING_FOR_USER'
  | 'RESOLVED'
  | 'CLOSED'
  | 'CANCELLED'

export interface RecentSlaBreachResponse {
  ticketNumber: string
  eventType: SlaEventType
  priority: SlaTicketPriority
  status: SlaTicketStatus
  occurredAt: string
}

export interface SlaDashboardResponse {
  activeTickets: number
  responseBreached: number
  resolutionBreached: number
  responseCompliancePercent: number | null
  resolutionCompliancePercent: number | null
  breachedByPriority: Record<
    SlaTicketPriority,
    number
  >
  recentBreaches: RecentSlaBreachResponse[]
}
