import {
  useCallback,
  useEffect,
  useState,
} from 'react'

import {
  useNavigate,
} from 'react-router-dom'

import {
  useAuth,
} from '../auth/useAuth'

import {
  getMyNotifications,
  getUnreadNotificationCount,
  markNotificationAsRead,
} from '../api/notification'

import {
  createNotificationRealtimeClient,
} from '../realtime/notificationRealtime'

import type {
  NotificationResponse,
} from '../types/notification'

export function NotificationBell() {
  const {
    accessToken,
    user,
  } = useAuth()

  const navigate =
    useNavigate()

  const [isOpen, setIsOpen] =
    useState(false)

  const [
    notifications,
    setNotifications,
  ] =
    useState<NotificationResponse[]>(
      [],
    )

  const [
    unreadCount,
    setUnreadCount,
  ] =
    useState(0)

  const [
    isLoading,
    setIsLoading,
  ] =
    useState(false)

  const [
    error,
    setError,
  ] =
    useState<string | null>(null)

  const isSupportEngineer =
    user?.roles.includes(
      'SUPPORT_ENGINEER',
    ) ?? false

  /*
   * Initial authoritative unread count.
   *
   * PostgreSQL remains the source of truth.
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
          setUnreadCount(
            count,
          )
        }
      })
      .catch(() => {
        /*
         * A temporary REST failure should
         * not block dashboard usage.
         */
      })

    return () => {
      cancelled = true
    }
  }, [accessToken])

  /*
   * Establish one authenticated STOMP connection
   * while the logged-in user has this component
   * mounted.
   */
  useEffect(() => {
    if (!accessToken) {
      return
    }

    let cancelled = false

    const client =
      createNotificationRealtimeClient(
        accessToken,
        (notification) => {
          if (cancelled) {
            return
          }

          /*
           * Show the new notification immediately
           * in the dropdown if it is already open.
           *
           * Avoid duplicate notification IDs.
           */
          setNotifications(
            (
              currentNotifications,
            ) => {
              const withoutDuplicate =
                currentNotifications.filter(
                  (
                    currentNotification,
                  ) =>
                    currentNotification
                      .notificationId !==
                    notification
                      .notificationId,
                )

              return [
                notification,
                ...withoutDuplicate,
              ].slice(
                0,
                10,
              )
            },
          )

          /*
           * Do not guess the unread total.
           *
           * Ask the PostgreSQL-backed REST API for
           * the authoritative value.
           *
           * This avoids unread-count drift after
           * reconnects or when multiple tabs exist.
           */
          getUnreadNotificationCount(
            accessToken,
          )
            .then((count) => {
              if (!cancelled) {
                setUnreadCount(
                  count,
                )
              }
            })
            .catch(() => {
              /*
               * Real-time delivery already updated
               * the visible notification list.
               *
               * A later refresh can reconcile the
               * unread count.
               */
            })
        },
      )

    client.activate()

    return () => {
      cancelled = true

      void client.deactivate()
    }
  }, [accessToken])

  const loadNotifications =
    useCallback(
      async () => {
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

          setUnreadCount(
            count,
          )
        } catch (requestError) {
          setError(
            requestError instanceof
              Error
              ? requestError.message
              : 'Failed to load notifications.',
          )
        } finally {
          setIsLoading(
            false,
          )
        }
      },
      [accessToken],
    )

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
    notification:
      NotificationResponse,
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
            notification
              .notificationId,
          )

        setNotifications(
          (
            currentNotifications,
          ) =>
            currentNotifications.map(
              (
                currentNotification,
              ) =>
                currentNotification
                  .notificationId ===
                notification
                  .notificationId
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
        getNotificationPath(
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
        requestError instanceof
          Error
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
            notifications.length ===
              0 && (
              <p className="notification-empty">
                Loading notifications...
              </p>
            )}

          {!isLoading &&
            notifications.length ===
              0 &&
            !error && (
              <p className="notification-empty">
                You have no notifications
                yet.
              </p>
            )}

          {notifications.length >
            0 && (
            <div className="notification-list">
              {notifications.map(
                (
                  notification,
                ) => (
                  <button
                    key={
                      notification
                        .notificationId
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
                        {
                          notification.title
                        }
                      </strong>

                      {!notification.read && (
                        <span
                          className="notification-unread-dot"
                          aria-label="Unread"
                        />
                      )}
                    </div>

                    <p>
                      {
                        notification.message
                      }
                    </p>

                    <div className="notification-item-meta">
                      {notification.ticketNumber && (
                        <span>
                          {
                            notification
                              .ticketNumber
                          }
                        </span>
                      )}

                      <span>
                        {formatNotificationTime(
                          notification
                            .createdAt,
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

function getNotificationPath(
  notification:
    NotificationResponse,
  isSupportEngineer: boolean,
) {
  /*
   * Approval-request notifications are intended
   * for TEAM_LEAD / ADMIN users.
   *
   * Open the approval inbox where the decision
   * can actually be made.
   */
  if (
    notification.type ===
    'APPROVAL_REQUESTED'
  ) {
    return '/approvals'
  }

  if (
    !notification.ticketNumber
  ) {
    return null
  }

  const encodedTicketNumber =
    encodeURIComponent(
      notification.ticketNumber,
    )

  /*
   * Approval decision notifications are sent
   * to the requester.
   *
   * They should open the requester's normal
   * ticket detail page.
   */
  if (
    notification.type ===
      'APPROVAL_APPROVED' ||
    notification.type ===
      'APPROVAL_REJECTED'
  ) {
    return `/tickets/${encodedTicketNumber}`
  }

  const isSlaBreachNotification =
    notification.type ===
      'SLA_RESPONSE_BREACHED' ||
    notification.type ===
      'SLA_RESOLUTION_BREACHED'

  /*
   * SLA breach notifications received by an
   * assigned support engineer should open the
   * support ticket workspace.
   */
  if (
    isSupportEngineer &&
    isSlaBreachNotification
  ) {
    return `/support/tickets/${encodedTicketNumber}`
  }

  /*
   * TEAM_LEAD / ADMIN can receive SLA escalation
   * notifications too.
   *
   * They currently do not have an ownership-safe
   * ticket-detail route for those notifications.
   */
  if (
    isSlaBreachNotification
  ) {
    return null
  }

  /*
   * When an employee replies to a ticket,
   * the assigned support engineer should open
   * the support workspace.
   */
  if (
    isSupportEngineer &&
    notification.type ===
      'TICKET_COMMENT_ADDED' &&
    notification.title ===
      'New requester reply'
  ) {
    return `/support/tickets/${encodedTicketNumber}`
  }

  /*
   * Other requester-facing ticket notifications
   * open the normal employee ticket page.
   */
  return `/tickets/${encodedTicketNumber}`
}

function formatNotificationTime(
  createdAt: string,
) {
  const date =
    new Date(
      createdAt,
    )

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
  ).format(
    date,
  )
}