export type NotificationType =
  | "TICKET_COMMENT_ADDED"
  | "TICKET_ASSIGNED"
  | "TICKET_STATUS_CHANGED"
  | "TICKET_RESOLVED";

export interface NotificationResponse {
  notificationId: string;
  type: NotificationType;

  title: string;
  message: string;

  actorUserId: string | null;
  actorName: string | null;

  ticketId: string | null;
  ticketNumber: string | null;

  read: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface UnreadNotificationCountResponse {
  unreadCount: number;
}

export interface NotificationPageResponse {
  content: NotificationResponse[];

  page: number;
  size: number;

  totalElements: number;
  totalPages: number;

  first: boolean;
  last: boolean;
}