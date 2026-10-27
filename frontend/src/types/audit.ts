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
  | 'USER_CREATED'
  | 'USER_ROLES_UPDATED'
  | 'USER_ACTIVATED'
  | 'USER_DEACTIVATED'
  | 'AI_TRIAGE_GENERATED'
  | 'AI_TRIAGE_APPLIED'
  | 'AI_REPLY_DRAFT_GENERATED'
  | 'AI_RESOLUTION_SUMMARY_DRAFT_GENERATED'
  | 'AI_SUPPORT_COPILOT_USED'
  | 'USER_INVITED'
  | 'USER_INVITATION_RESENT'
  | 'USER_INVITATION_ACCEPTED'

export type AuditTargetType =
  | 'TICKET'
  | 'APPROVAL'
  | 'USER'

export interface AuditEvent {
  id: string
  actorType: AuditActorType
  actorUserId: string | null
  actorEmail: string | null
  action: AuditAction
  targetType: AuditTargetType
  targetId: string
  targetReference: string | null
  metadata: Record<string, unknown>
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