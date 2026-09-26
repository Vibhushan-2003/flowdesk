export type AuditActorType =
  | 'USER'
  | 'SYSTEM'

export type AuditAction =
  | 'TICKET_CREATED'
  | 'TICKET_CLAIMED'
  | 'TICKET_STATUS_CHANGED'
  | 'SLA_RESPONSE_BREACHED'
  | 'SLA_RESOLUTION_BREACHED'
  | 'APPROVAL_REQUESTED'
  | 'APPROVAL_APPROVED'
  | 'APPROVAL_REJECTED'

export type AuditTargetType =
  | 'TICKET'
  | 'APPROVAL'

export interface AuditEvent {
  id: string

  actorType: AuditActorType

  actorUserId: string | null

  actorEmail: string | null

  action: AuditAction

  targetType: AuditTargetType

  targetId: string

  targetReference: string | null

  metadata: Record<
    string,
    unknown
  >

  occurredAt: string
}

export interface AuditEventsPage {
  content: AuditEvent[]

  page: number

  size: number

  totalElements: number

  totalPages: number

  first: boolean

  last: boolean
}