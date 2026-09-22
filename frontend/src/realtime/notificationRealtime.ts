import {
  Client,
  type IMessage,
} from '@stomp/stompjs'

import {
  WEBSOCKET_URL,
} from '../api/config'

import type {
  NotificationResponse,
} from '../types/notification'

const NOTIFICATION_DESTINATION =
  '/user/queue/notifications'

type NotificationHandler = (
  notification: NotificationResponse,
) => void

export function createNotificationRealtimeClient(
  accessToken: string,
  onNotification: NotificationHandler,
) {
  const client =
    new Client({
      brokerURL: WEBSOCKET_URL,

      /*
       * The browser WebSocket handshake itself
       * does not contain our JWT.
       *
       * This header is placed inside the STOMP
       * CONNECT frame instead.
       */
      connectHeaders: {
        Authorization:
          `Bearer ${accessToken}`,
      },

      /*
       * Automatically reconnect after a temporary
       * network/server interruption.
       */
      reconnectDelay: 5000,

      /*
       * Detect dead connections.
       */
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      connectionTimeout: 10000,

      /*
       * Keep STOMP's verbose debugging disabled
       * in the normal application UI.
       */
      debug: () => undefined,
    })

  client.onConnect = () => {
    client.subscribe(
      NOTIFICATION_DESTINATION,
      (message: IMessage) => {
        const notification =
          parseNotificationMessage(
            message.body,
          )

        if (!notification) {
          return
        }

        onNotification(
          notification,
        )
      },
    )
  }

  return client
}

function parseNotificationMessage(
  body: string,
): NotificationResponse | null {
  try {
    const parsed: unknown =
      JSON.parse(body)

    if (
      !isNotificationResponse(
        parsed,
      )
    ) {
      return null
    }

    return parsed
  } catch {
    return null
  }
}

function isNotificationResponse(
  value: unknown,
): value is NotificationResponse {
  if (
    typeof value !== 'object' ||
    value === null
  ) {
    return false
  }

  const candidate =
    value as Partial<NotificationResponse>

  const validTypes:
    NotificationResponse['type'][] = [
      'TICKET_COMMENT_ADDED',
      'TICKET_ASSIGNED',
      'TICKET_STATUS_CHANGED',
      'TICKET_RESOLVED',
    ]

  return (
    typeof candidate.notificationId ===
      'string' &&
    typeof candidate.type ===
      'string' &&
    validTypes.includes(
      candidate.type as NotificationResponse['type'],
    ) &&
    typeof candidate.title ===
      'string' &&
    typeof candidate.message ===
      'string' &&
    typeof candidate.read ===
      'boolean' &&
    typeof candidate.createdAt ===
      'string'
  )
}