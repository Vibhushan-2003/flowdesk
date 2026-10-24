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

const AUDIT_ACTIONS: AuditAction[] = [
  'TICKET_CREATED',
  'TICKET_CLAIMED',
  'TICKET_STATUS_CHANGED',
  'SLA_RESPONSE_BREACHED',
  'SLA_RESOLUTION_BREACHED',
  'APPROVAL_REQUESTED',
  'APPROVAL_APPROVED',
  'APPROVAL_REJECTED',
  'USER_CREATED',
  'USER_ROLES_UPDATED',
  'USER_ACTIVATED',
  'USER_DEACTIVATED',
  'AI_TRIAGE_GENERATED',
  'AI_TRIAGE_APPLIED',
  'AI_REPLY_DRAFT_GENERATED',
  'AI_RESOLUTION_SUMMARY_DRAFT_GENERATED',
  'AI_SUPPORT_COPILOT_USED',
]

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
    selectedAction,
    setSelectedAction,
  ] = useState<AuditAction | ''>('')

  const [
    searchQuery,
    setSearchQuery,
  ] = useState('')

  const [
    appliedAction,
    setAppliedAction,
  ] = useState<AuditAction | undefined>(undefined)

  const [
    appliedSearch,
    setAppliedSearch,
  ] = useState<string | undefined>(undefined)

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

  function handleFilterSubmit(event: React.FormEvent) {
    event.preventDefault()
    setPage(0)
    setAppliedAction(selectedAction ? selectedAction : undefined)
    setAppliedSearch(searchQuery.trim() ? searchQuery.trim() : undefined)
  }

  function handleResetFilters() {
    setSelectedAction('')
    setSearchQuery('')
    setAppliedAction(undefined)
    setAppliedSearch(undefined)
    setPage(0)
  }

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
            {
              action: appliedAction,
              search: appliedSearch,
            },
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
    appliedAction,
    appliedSearch,
  ])

  function handleSignOut() {
    signOut()
    navigate('/login')
  }

  function handlePreviousPage() {
    setPage((currentPage) => Math.max(0, currentPage - 1))
  }

  function handleNextPage() {
    if (auditPage?.last) {
      return
    }
    setPage((currentPage) => currentPage + 1)
  }

  const hasActiveFilters = Boolean(appliedAction || appliedSearch)

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

      <form
        className="audit-filters-form"
        onSubmit={handleFilterSubmit}
        aria-label="Audit filters"
      >
        <label>
          Search target or actor
          <input
            type="search"
            value={searchQuery}
            maxLength={100}
            onChange={(event) => setSearchQuery(event.target.value)}
            placeholder="e.g. FD-000028 or user@example.com"
          />
        </label>

        <label>
          Action
          <select
            value={selectedAction}
            onChange={(event) =>
              setSelectedAction(event.target.value as AuditAction | '')
            }
          >
            <option value="">All actions</option>
            {AUDIT_ACTIONS.map((action) => (
              <option key={action} value={action}>
                {getActionLabel(action)}
              </option>
            ))}
          </select>
        </label>

        <button type="submit" className="primary-button">
          Filter
        </button>
        <button
          type="button"
          className="secondary-button"
          onClick={handleResetFilters}
        >
          Clear
        </button>
      </form>

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
                    {hasActiveFilters
                      ? 'No matching audit events'
                      : 'No audit events yet'}
                  </h2>

                  <p>
                    {hasActiveFilters
                      ? 'Try adjusting your search query or action filter.'
                      : 'Business activity will appear here after audited actions are recorded.'}
                  </p>
                  {hasActiveFilters && (
                    <button
                      type="button"
                      className="secondary-button"
                      onClick={handleResetFilters}
                      style={{ marginTop: '0.75rem' }}
                    >
                      Clear filters
                    </button>
                  )}
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
  const displayTitle =
    event.targetReference ??
    (typeof event.metadata['ticketNumber'] === 'string'
      ? event.metadata['ticketNumber']
      : `${formatLabel(event.targetType)} Event`)

  return (
    <article className="dashboard-workspace-card audit-card">
      <p className="dashboard-card-label">
        {getActionLabel(
          event.action,
        )}
      </p>

      <h2 style={{ overflowWrap: 'anywhere' }}>
        {displayTitle}
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

          <strong style={{ overflowWrap: 'anywhere' }}>
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

  if (
    event.action ===
      'APPROVAL_REQUESTED'
  ) {
    return (
      <div className="account-details-grid">
        <MetadataValue
          label="Approval status"
          value={getStringMetadata(
            event,
            'approvalStatus',
          )}
        />

        <MetadataValue
          label="Ticket status"
          value={getStringMetadata(
            event,
            'ticketStatus',
          )}
        />

        <MetadataValue
          label="Ticket"
          value={getStringMetadata(
            event,
            'ticketNumber',
          )}
        />
      </div>
    )
  }

  if (
    event.action ===
      'APPROVAL_APPROVED' ||
    event.action ===
      'APPROVAL_REJECTED'
  ) {
    return (
      <div className="account-details-grid">
        <MetadataValue
          label="Approval status"
          value={getStringMetadata(
            event,
            'approvalStatus',
          )}
        />

        <MetadataValue
          label="From"
          value={getStringMetadata(
            event,
            'fromTicketStatus',
          )}
        />

        <MetadataValue
          label="To"
          value={getStringMetadata(
            event,
            'toTicketStatus',
          )}
        />
      </div>
    )
  }

  if (event.action === 'USER_CREATED') {
    return (
      <div className="account-details-grid">
        <MetadataValue
          label="Assigned roles"
          value={getRoleListMetadata(event, 'assignedRoles')}
        />
      </div>
    )
  }

  if (event.action === 'USER_ROLES_UPDATED') {
    return (
      <div className="account-details-grid">
        <MetadataValue
          label="Previous roles"
          value={getRoleListMetadata(event, 'previousRoles')}
        />
        <MetadataValue
          label="New roles"
          value={getRoleListMetadata(event, 'newRoles')}
        />
      </div>
    )
  }

  if (
    event.action === 'USER_ACTIVATED' ||
    event.action === 'USER_DEACTIVATED'
  ) {
    return (
      <div className="account-details-grid">
        <MetadataValue
          label="Previous status"
          value={getStringMetadata(event, 'previousStatus')}
        />
        <MetadataValue
          label="New status"
          value={getStringMetadata(event, 'newStatus')}
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

function getActionLabel(action: AuditAction): string {
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
    case 'APPROVAL_REQUESTED':
      return 'Approval requested'
    case 'APPROVAL_APPROVED':
      return 'Approval approved'
    case 'APPROVAL_REJECTED':
      return 'Approval rejected'

    case 'USER_CREATED':
      return 'User created'
    case 'USER_ROLES_UPDATED':
      return 'User roles updated'
    case 'USER_ACTIVATED':
      return 'User account activated'
    case 'USER_DEACTIVATED':
      return 'User account deactivated'

    case 'AI_TRIAGE_GENERATED':
      return 'AI triage generated'
    case 'AI_TRIAGE_APPLIED':
      return 'AI triage applied'
    case 'AI_REPLY_DRAFT_GENERATED':
      return 'AI reply draft generated'
    case 'AI_RESOLUTION_SUMMARY_DRAFT_GENERATED':
      return 'AI resolution summary generated'
    case 'AI_SUPPORT_COPILOT_USED':
      return 'AI support copilot used'

    default:
      return formatLabel(action)
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

    case 'APPROVAL_REQUESTED':
      return `${reference} was submitted for approval.`

    case 'APPROVAL_APPROVED': {
      const fromStatus =
        getStringMetadata(
          event,
          'fromTicketStatus',
        )

      const toStatus =
        getStringMetadata(
          event,
          'toTicketStatus',
        )

      return `${reference} was approved and moved from ${formatLabel(
        fromStatus,
      )} to ${formatLabel(
        toStatus,
      )}.`
    }

    case 'APPROVAL_REJECTED': {
      const fromStatus =
        getStringMetadata(
          event,
          'fromTicketStatus',
        )

      const toStatus =
        getStringMetadata(
          event,
          'toTicketStatus',
        )

      return `${reference} was rejected and moved from ${formatLabel(
        fromStatus,
      )} to ${formatLabel(
        toStatus,
      )}.`
    }
    case 'USER_CREATED':
      return `User account ${reference} was created.`

    case 'USER_ROLES_UPDATED':
      return `Roles were updated for ${reference}.`

    case 'USER_ACTIVATED':
      return `User account ${reference} was activated.`

    case 'USER_DEACTIVATED':
      return `User account ${reference} was deactivated.`

    case 'AI_TRIAGE_GENERATED':
      return `AI triage suggestions were generated for ${reference}.`

    case 'AI_TRIAGE_APPLIED':
      return `AI triage suggestions were applied to ${reference}.`

    case 'AI_REPLY_DRAFT_GENERATED':
      return `An AI reply draft was generated for ${reference}.`

    case 'AI_RESOLUTION_SUMMARY_DRAFT_GENERATED':
      return `An AI resolution summary draft was generated for ${reference}.`

    case 'AI_SUPPORT_COPILOT_USED':
      return `The AI support copilot was used for ${reference}.`

    default:
      return `${formatLabel(event.action)} for ${reference}.`
  }
}

function getRoleListMetadata(
  event: AuditEvent,
  key: string,
): string {
  const value = event.metadata[key]

  if (!Array.isArray(value)) {
    return 'Not available'
  }

  const roles = value.filter(
    (role): role is string =>
      typeof role === 'string' && role.trim() !== '',
  )

  if (roles.length === 0) {
    return 'Not available'
  }

  return roles.map((role) => formatLabel(role)).join(', ')
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