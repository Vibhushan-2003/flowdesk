import {
  useCallback,
  useEffect,
  useState,
} from 'react'

import { useNavigate } from 'react-router-dom'

import { useAuth } from '../auth/useAuth'

import {
  getMyNotifications,
  getUnreadNotificationCount,
  markNotificationAsRead,
} from '../api/notification'

import type { NotificationResponse } from '../types/notification'

export function NotificationBell() {
  const {
    accessToken,
    user,
  } = useAuth()

  const navigate = useNavigate()

  const [isOpen, setIsOpen] =
    useState(false)

  const [notifications, setNotifications] =
    useState<NotificationResponse[]>([])

  const [unreadCount, setUnreadCount] =
    useState(0)

  const [isLoading, setIsLoading] =
    useState(false)

  const [error, setError] =
    useState<string | null>(null)

  const isSupportEngineer =
    user?.roles.includes(
      'SUPPORT_ENGINEER',
    ) ?? false

  /*
   * Load the unread count when the authenticated
   * user reaches the dashboard.
   *
   * The state update happens from the resolved
   * Promise callback rather than synchronously
   * inside the effect body.
   */
  useEffect(() => {
    if (!accessToken) {
      return
    }

    let cancelled = false

    getUnreadNotificationCount(
      accessToken,
    )
      .then((count) => {
        if (!cancelled) {
          setUnreadCount(count)
        }
      })
      .catch(() => {
        /*
         * A temporary count failure should not
         * prevent the user from using the dashboard.
         */
      })

    return () => {
      cancelled = true
    }
  }, [accessToken])

  const loadNotifications =
    useCallback(async () => {
      if (!accessToken) {
        return
      }

      setIsLoading(true)
      setError(null)

      try {
        const response =
          await getMyNotifications(
            accessToken,
            0,
            10,
          )

        setNotifications(
          response.content,
        )

        const count =
          await getUnreadNotificationCount(
            accessToken,
          )

        setUnreadCount(count)
      } catch (requestError) {
        setError(
          requestError instanceof Error
            ? requestError.message
            : 'Failed to load notifications.',
        )
      } finally {
        setIsLoading(false)
      }
    }, [accessToken])

  async function handleBellClick() {
    const nextOpenState =
      !isOpen

    setIsOpen(
      nextOpenState,
    )

    if (nextOpenState) {
      await loadNotifications()
    }
  }

  async function handleNotificationClick(
    notification: NotificationResponse,
  ) {
    if (!accessToken) {
      return
    }

    setError(null)

    try {
      let updatedNotification =
        notification

      if (!notification.read) {
        updatedNotification =
          await markNotificationAsRead(
            accessToken,
            notification.notificationId,
          )

        setNotifications(
          (currentNotifications) =>
            currentNotifications.map(
              (currentNotification) =>
                currentNotification
                  .notificationId ===
                notification.notificationId
                  ? updatedNotification
                  : currentNotification,
            ),
        )

        setUnreadCount(
          (currentCount) =>
            Math.max(
              0,
              currentCount - 1,
            ),
        )
      }

      setIsOpen(false)

      const ticketPath =
        getTicketPath(
          updatedNotification,
          isSupportEngineer,
        )

      if (ticketPath) {
        navigate(
          ticketPath,
        )
      }
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : 'Failed to open notification.',
      )
    }
  }

  return (
    <div className="notification-bell-wrapper">
      <button
        type="button"
        className="notification-bell-button"
        aria-label="Notifications"
        aria-haspopup="true"
        aria-expanded={isOpen}
        onClick={() => {
          void handleBellClick()
        }}
      >
        <span
          className="notification-bell-icon"
          aria-hidden="true"
        >
          🔔
        </span>

        {unreadCount > 0 && (
          <span className="notification-badge">
            {unreadCount > 99
              ? '99+'
              : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div className="notification-dropdown">
          <div className="notification-dropdown-header">
            <div>
              <p className="dashboard-card-label">
                Updates
              </p>

              <h2>
                Notifications
              </h2>
            </div>

            <button
              type="button"
              className="notification-refresh-button"
              disabled={isLoading}
              onClick={() => {
                void loadNotifications()
              }}
            >
              Refresh
            </button>
          </div>

          {error && (
            <p className="notification-error">
              {error}
            </p>
          )}

          {isLoading &&
            notifications.length === 0 && (
              <p className="notification-empty">
                Loading notifications...
              </p>
            )}

          {!isLoading &&
            notifications.length === 0 &&
            !error && (
              <p className="notification-empty">
                You have no notifications yet.
              </p>
            )}

          {notifications.length > 0 && (
            <div className="notification-list">
              {notifications.map(
                (notification) => (
                  <button
                    key={
                      notification.notificationId
                    }
                    type="button"
                    className={
                      notification.read
                        ? 'notification-item'
                        : 'notification-item notification-item-unread'
                    }
                    onClick={() => {
                      void handleNotificationClick(
                        notification,
                      )
                    }}
                  >
                    <div className="notification-item-heading">
                      <strong>
                        {notification.title}
                      </strong>

                      {!notification.read && (
                        <span
                          className="notification-unread-dot"
                          aria-label="Unread"
                        />
                      )}
                    </div>

                    <p>
                      {notification.message}
                    </p>

                    <div className="notification-item-meta">
                      {notification.ticketNumber && (
                        <span>
                          {
                            notification.ticketNumber
                          }
                        </span>
                      )}

                      <span>
                        {formatNotificationTime(
                          notification.createdAt,
                        )}
                      </span>
                    </div>
                  </button>
                ),
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function getTicketPath(
  notification: NotificationResponse,
  isSupportEngineer: boolean,
) {
  if (!notification.ticketNumber) {
    return null
  }

  /*
   * Employee reply notification:
   *
   * employee
   *   → sends comment
   *   → support engineer receives notification
   *
   * Therefore open the support workspace.
   */
  if (
    isSupportEngineer &&
    notification.type ===
      'TICKET_COMMENT_ADDED' &&
    notification.title ===
      'New requester reply'
  ) {
    return `/support/tickets/${encodeURIComponent(
      notification.ticketNumber,
    )}`
  }

  /*
   * Support reply and status notifications
   * are intended for the employee.
   */
  return `/tickets/${encodeURIComponent(
    notification.ticketNumber,
  )}`
}

function formatNotificationTime(
  createdAt: string,
) {
  const date =
    new Date(createdAt)

  if (
    Number.isNaN(
      date.getTime(),
    )
  ) {
    return ''
  }

  return new Intl.DateTimeFormat(
    undefined,
    {
      dateStyle: 'medium',
      timeStyle: 'short',
    },
  ).format(date)
}