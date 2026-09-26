import {
  useEffect,
  useState,
} from 'react'

import {
  useNavigate,
} from 'react-router-dom'

import {
  getAuditEvents,
} from '../api/audit'

import {
  useAuth,
} from '../auth/useAuth'

import {
  NotificationBell,
} from '../components/NotificationBell'

import type {
  AuditAction,
  AuditEvent,
  AuditEventsPage,
} from '../types/audit'

const PAGE_SIZE = 20

export function AuditTrailPage() {
  const {
    accessToken,
    signOut,
  } = useAuth()

  const navigate =
    useNavigate()

  const [
    page,
    setPage,
  ] =
    useState(0)

  const [
    auditPage,
    setAuditPage,
  ] =
    useState<AuditEventsPage | null>(
      null,
    )

  const [
    isLoading,
    setIsLoading,
  ] =
    useState(true)

  const [
    error,
    setError,
  ] =
    useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadAuditEvents() {
      if (!accessToken) {
        if (!cancelled) {
          setError(
            'Your session is no longer valid',
          )

          setIsLoading(false)
        }

        return
      }

      setIsLoading(true)
      setError(null)

      try {
        const response =
          await getAuditEvents(
            accessToken,
            page,
            PAGE_SIZE,
          )

        if (!cancelled) {
          setAuditPage(
            response,
          )
        }
      } catch (caughtError) {
        if (cancelled) {
          return
        }

        setError(
          caughtError instanceof Error
            ? caughtError.message
            : 'Unable to load audit history.',
        )
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    void loadAuditEvents()

    return () => {
      cancelled = true
    }
  }, [
    accessToken,
    page,
  ])

  function handleSignOut() {
    signOut()

    navigate('/login')
  }

  function handlePreviousPage() {
    setPage(
      (currentPage) =>
        Math.max(
          0,
          currentPage - 1,
        ),
    )
  }

  function handleNextPage() {
    if (
      auditPage?.last
    ) {
      return
    }

    setPage(
      (currentPage) =>
        currentPage + 1,
    )
  }

  return (
    <main className="dashboard-page">
      <header className="dashboard-header">
        <div>
          <p className="eyebrow">
            FlowDesk
          </p>

          <h1>
            Audit Trail
          </h1>

          <p className="dashboard-subtitle">
            Review immutable business activity,
            system events, and operational changes.
          </p>
        </div>

        <div className="dashboard-header-actions">
          <NotificationBell />

          <button
            type="button"
            className="dashboard-secondary-button"
            onClick={() =>
              navigate(
                '/dashboard',
              )
            }
          >
            Dashboard
          </button>

          <button
            type="button"
            onClick={handleSignOut}
          >
            Sign out
          </button>
        </div>
      </header>

      {isLoading && (
        <section className="user-card">
          <p>
            Loading audit history...
          </p>
        </section>
      )}

      {!isLoading &&
        error && (
        <section className="user-card">
          <p className="notification-error">
            {error}
          </p>
        </section>
      )}

      {!isLoading &&
        !error &&
        auditPage && (
        <>
          <section className="user-card">
            <div className="account-card-heading">
              <div>
                <p className="dashboard-card-label">
                  Immutable history
                </p>

                <h2>
                  Recent audit events
                </h2>
              </div>

              <span className="account-status">
                {
                  auditPage
                    .totalElements
                }{' '}
                events
              </span>
            </div>

            {auditPage
              .content
              .length === 0 ? (
              <div className="empty-state">
                <h2>
                  No audit events yet
                </h2>

                <p>
                  Business activity will appear
                  here after audited actions are
                  recorded.
                </p>
              </div>
            ) : (
              <div className="dashboard-workspace-grid">
                {auditPage
                  .content
                  .map(
                    (event) => (
                      <AuditEventCard
                        key={
                          event.id
                        }
                        event={
                          event
                        }
                      />
                    ),
                  )}
              </div>
            )}
          </section>

          {auditPage
            .totalPages > 1 && (
            <nav
              className="pagination"
              aria-label="Audit history pagination"
            >
              <button
                type="button"
                disabled={
                  auditPage.first
                }
                onClick={
                  handlePreviousPage
                }
              >
                Previous
              </button>

              <span>
                Page{' '}
                {
                  auditPage.page +
                  1
                }{' '}
                of{' '}
                {
                  auditPage
                    .totalPages
                }
              </span>

              <button
                type="button"
                disabled={
                  auditPage.last
                }
                onClick={
                  handleNextPage
                }
              >
                Next
              </button>
            </nav>
          )}
        </>
      )}
    </main>
  )
}

interface AuditEventCardProps {
  event: AuditEvent
}

function AuditEventCard({
  event,
}: AuditEventCardProps) {
  return (
    <article className="dashboard-workspace-card">
      <p className="dashboard-card-label">
        {getActionLabel(
          event.action,
        )}
      </p>

      <h2>
        {event.targetReference ??
          formatLabel(
            event.targetType,
          )}
      </h2>

      <p>
        {getActionDescription(
          event,
        )}
      </p>

      <div className="account-details-grid">
        <div>
          <span>
            Actor
          </span>

          <strong>
            {getActorLabel(
              event,
            )}
          </strong>
        </div>

        <div>
          <span>
            Actor type
          </span>

          <strong>
            {formatLabel(
              event.actorType,
            )}
          </strong>
        </div>

        <div>
          <span>
            Occurred
          </span>

          <strong>
            {formatDate(
              event.occurredAt,
            )}
          </strong>
        </div>
      </div>

      <AuditMetadata
        event={
          event
        }
      />
    </article>
  )
}

interface AuditMetadataProps {
  event: AuditEvent
}

function AuditMetadata({
  event,
}: AuditMetadataProps) {
  if (
    event.action ===
      'TICKET_CREATED'
  ) {
    return (
      <div className="account-details-grid">
        <MetadataValue
          label="Type"
          value={getStringMetadata(
            event,
            'type',
          )}
        />

        <MetadataValue
          label="Priority"
          value={getStringMetadata(
            event,
            'priority',
          )}
        />

        <MetadataValue
          label="Status"
          value={getStringMetadata(
            event,
            'status',
          )}
        />
      </div>
    )
  }

  if (
    event.action ===
      'TICKET_CLAIMED' ||
    event.action ===
      'TICKET_STATUS_CHANGED'
  ) {
    return (
      <div className="account-details-grid">
        <MetadataValue
          label="From"
          value={getStringMetadata(
            event,
            'fromStatus',
          )}
        />

        <MetadataValue
          label="To"
          value={getStringMetadata(
            event,
            'toStatus',
          )}
        />

        <MetadataValue
          label="Target"
          value={
            event.targetReference ??
            event.targetId
          }
        />
      </div>
    )
  }

  if (
    event.action ===
      'SLA_RESPONSE_BREACHED' ||
    event.action ===
      'SLA_RESOLUTION_BREACHED'
  ) {
    return (
      <div className="account-details-grid">
        <MetadataValue
          label="Deadline"
          value={formatOptionalDate(
            getStringMetadata(
              event,
              'deadline',
            ),
          )}
        />

        <MetadataValue
          label="Event type"
          value={getStringMetadata(
            event,
            'eventType',
          )}
        />

        <MetadataValue
          label="Source"
          value="System monitoring"
        />
      </div>
    )
  }

  return null
}

interface MetadataValueProps {
  label: string
  value: string
}

function MetadataValue({
  label,
  value,
}: MetadataValueProps) {
  return (
    <div>
      <span>
        {label}
      </span>

      <strong>
        {formatLabel(
          value,
        )}
      </strong>
    </div>
  )
}

function getActorLabel(
  event: AuditEvent,
) {
  if (
    event.actorType ===
      'SYSTEM'
  ) {
    return 'FlowDesk System'
  }

  return event.actorEmail ??
    'Unknown user'
}

function getActionLabel(
  action: AuditAction,
) {
  switch (action) {
    case 'TICKET_CREATED':
      return 'Ticket created'

    case 'TICKET_CLAIMED':
      return 'Ticket claimed'

    case 'TICKET_STATUS_CHANGED':
      return 'Ticket status changed'

    case 'SLA_RESPONSE_BREACHED':
      return 'Response SLA breached'

    case 'SLA_RESOLUTION_BREACHED':
      return 'Resolution SLA breached'
  }
}

function getActionDescription(
  event: AuditEvent,
) {
  const reference =
    event.targetReference ??
    'the target resource'

  switch (event.action) {
    case 'TICKET_CREATED':
      return `${reference} was created.`

    case 'TICKET_CLAIMED':
      return `${reference} was claimed by a support engineer.`

    case 'TICKET_STATUS_CHANGED': {
      const fromStatus =
        getStringMetadata(
          event,
          'fromStatus',
        )

      const toStatus =
        getStringMetadata(
          event,
          'toStatus',
        )

      return `${reference} moved from ${formatLabel(
        fromStatus,
      )} to ${formatLabel(
        toStatus,
      )}.`
    }

    case 'SLA_RESPONSE_BREACHED':
      return `${reference} missed its response SLA deadline.`

    case 'SLA_RESOLUTION_BREACHED':
      return `${reference} missed its resolution SLA deadline.`
  }
}

function getStringMetadata(
  event: AuditEvent,
  key: string,
) {
  const value =
    event.metadata[key]

  if (
    typeof value ===
      'string' &&
    value.trim() !== ''
  ) {
    return value
  }

  return 'Not available'
}

function formatLabel(
  value: string,
) {
  return value
    .replaceAll(
      '_',
      ' ',
    )
    .toLowerCase()
    .replace(
      /\b\w/g,
      (character) =>
        character.toUpperCase(),
    )
}

function formatOptionalDate(
  value: string,
) {
  if (
    value ===
    'Not available'
  ) {
    return value
  }

  return formatDate(
    value,
  )
}

function formatDate(
  value: string,
) {
  const date =
    new Date(
      value,
    )

  if (
    Number.isNaN(
      date.getTime(),
    )
  ) {
    return value
  }

  return date.toLocaleString()
}