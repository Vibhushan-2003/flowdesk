export type NotificationType =
  | 'TICKET_COMMENT_ADDED'
  | 'TICKET_ASSIGNED'
  | 'TICKET_STATUS_CHANGED'
  | 'TICKET_RESOLVED'
  | 'SLA_RESPONSE_BREACHED'
  | 'SLA_RESOLUTION_BREACHED'

export interface NotificationResponse {
  notificationId: string
  type: NotificationType

  title: string
  message: string

  actorUserId: string | null
  actorName: string | null

  ticketId: string | null
  ticketNumber: string | null

  read: boolean
  readAt: string | null
  createdAt: string
}

export interface UnreadNotificationCountResponse {
  unreadCount: number
}

export interface NotificationPageResponse {
  content: NotificationResponse[]

  page: number
  size: number

  totalElements: number
  totalPages: number

  first: boolean
  last: boolean
}
