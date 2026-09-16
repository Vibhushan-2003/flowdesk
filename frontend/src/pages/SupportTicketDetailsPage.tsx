import {
  useEffect,
  useState,
} from 'react'

import {
  useNavigate,
  useParams,
} from 'react-router-dom'

import {
  getSupportTicket,
  updateSupportTicketStatus,
} from '../api/ticket'

import { useAuth } from '../auth/useAuth'

import type {
  SupportTicketResponse,
  TicketStatus,
} from '../types/ticket'

function formatLabel(value: string) {
  return value.replaceAll('_', ' ')
}

function formatDate(value: string) {
  return new Date(value).toLocaleString()
}

function getStatusClass(status: string) {
  return `support-status support-status-${status
    .toLowerCase()
    .replaceAll('_', '-')}`
}

function getActionMessage(
  status: TicketStatus,
) {
  switch (status) {
    case 'IN_PROGRESS':
      return 'Ticket is now in progress.'

    case 'WAITING_FOR_USER':
      return 'Ticket is now waiting for the requester.'

    case 'RESOLVED':
      return 'Ticket has been resolved successfully.'

    default:
      return 'Ticket status updated successfully.'
  }
}

export function SupportTicketDetailsPage() {
  const { accessToken } = useAuth()

  const { ticketNumber } = useParams()

  const navigate = useNavigate()

  const [ticket, setTicket] =
    useState<SupportTicketResponse | null>(
      null,
    )

  const [isLoading, setIsLoading] =
    useState(true)

  const [isUpdating, setIsUpdating] =
    useState(false)

  const [error, setError] =
    useState<string | null>(null)

  const [successMessage, setSuccessMessage] =
    useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadTicket() {
      if (!accessToken) {
        if (!cancelled) {
          setError(
            'Your session is no longer valid',
          )

          setIsLoading(false)
        }

        return
      }

      if (!ticketNumber) {
        if (!cancelled) {
          setError(
            'Ticket number is missing',
          )

          setIsLoading(false)
        }

        return
      }

      setIsLoading(true)
      setError(null)

      try {
        const response =
          await getSupportTicket(
            accessToken,
            ticketNumber,
          )

        if (!cancelled) {
          setTicket(response)
        }
      } catch (caughtError) {
        if (cancelled) {
          return
        }

        if (caughtError instanceof Error) {
          setError(caughtError.message)
        } else {
          setError(
            'Unable to load support ticket',
          )
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    void loadTicket()

    return () => {
      cancelled = true
    }
  }, [accessToken, ticketNumber])

  async function handleStatusChange(
    nextStatus: TicketStatus,
  ) {
    if (!accessToken || !ticketNumber) {
      setError(
        'Your session is no longer valid',
      )

      return
    }

    setIsUpdating(true)
    setError(null)
    setSuccessMessage(null)

    try {
      const response =
        await updateSupportTicketStatus(
          accessToken,
          ticketNumber,
          nextStatus,
        )

      setTicket(response)

      setSuccessMessage(
        getActionMessage(
          response.status,
        ),
      )
    } catch (caughtError) {
      if (caughtError instanceof Error) {
        setError(caughtError.message)
      } else {
        setError(
          'Unable to update ticket status',
        )
      }
    } finally {
      setIsUpdating(false)
    }
  }

  return (
    <main className="support-ticket-details-page">
      <header className="support-page-header">
        <div>
          <p className="eyebrow">
            FlowDesk Operations
          </p>

          <h1>Support ticket</h1>

          <p className="support-page-description">
            Review the requester&apos;s issue and
            move the ticket through the support
            workflow.
          </p>
        </div>

        <div className="support-header-actions">
          <button
            type="button"
            className="secondary-button"
            onClick={() =>
              navigate('/support/tickets')
            }
          >
            My assignments
          </button>

          <button
            type="button"
            className="secondary-button"
            onClick={() =>
              navigate('/support/queue')
            }
          >
            Support queue
          </button>
        </div>
      </header>

      {error && (
        <div
          className="support-alert support-alert-error"
          role="alert"
        >
          {error}
        </div>
      )}

      {successMessage && (
        <div
          className="support-alert support-alert-success"
          aria-live="polite"
        >
          {successMessage}
        </div>
      )}

      {isLoading && (
        <section className="support-loading-state">
          <div className="support-loading-dot" />

          <p>Loading support ticket...</p>
        </section>
      )}

      {!isLoading &&
        !ticket &&
        error && (
          <section className="support-empty-state">
            <h2>
              Ticket unavailable
            </h2>

            <p>
              This ticket may no longer be assigned
              to you.
            </p>

            <button
              type="button"
              className="primary-button"
              onClick={() =>
                navigate('/support/tickets')
              }
            >
              Back to my assignments
            </button>
          </section>
        )}

      {!isLoading && ticket && (
        <div className="support-ticket-layout">
          <article className="support-ticket-detail-card">
            <header className="support-ticket-detail-heading">
              <div>
                <p className="support-ticket-number">
                  {ticket.ticketNumber}
                </p>

                <h2>{ticket.title}</h2>
              </div>

              <span
                className={getStatusClass(
                  ticket.status,
                )}
              >
                {formatLabel(
                  ticket.status,
                )}
              </span>
            </header>

            <section className="support-description-section">
              <p className="support-section-label">
                Issue description
              </p>

              <p className="support-ticket-description-text">
                {ticket.description}
              </p>
            </section>

            <section className="support-detail-grid">
              <div>
                <span>Type</span>

                <strong>
                  {formatLabel(
                    ticket.type,
                  )}
                </strong>
              </div>

              <div>
                <span>Priority</span>

                <strong>
                  {ticket.priority}
                </strong>
              </div>

              <div>
                <span>Requester</span>

                <strong>
                  {ticket.createdByEmail}
                </strong>
              </div>

              <div>
                <span>Created</span>

                <strong>
                  {formatDate(
                    ticket.createdAt,
                  )}
                </strong>
              </div>

              <div>
                <span>Assigned</span>

                <strong>
                  {formatDate(
                    ticket.assignedAt,
                  )}
                </strong>
              </div>

              <div>
                <span>Last updated</span>

                <strong>
                  {formatDate(
                    ticket.updatedAt,
                  )}
                </strong>
              </div>

              {ticket.resolvedAt && (
                <div>
                  <span>Resolved</span>

                  <strong>
                    {formatDate(
                      ticket.resolvedAt,
                    )}
                  </strong>
                </div>
              )}
            </section>
          </article>

          <aside className="support-workflow-panel">
            <p className="support-section-label">
              Workflow
            </p>

            <h2>Next action</h2>

            {ticket.status === 'ASSIGNED' && (
              <>
                <p>
                  The ticket is assigned to you but
                  work has not started yet.
                </p>

                <button
                  type="button"
                  className="primary-button support-workflow-button"
                  disabled={isUpdating}
                  onClick={() =>
                    void handleStatusChange(
                      'IN_PROGRESS',
                    )
                  }
                >
                  {isUpdating
                    ? 'Starting...'
                    : 'Start work'}
                </button>
              </>
            )}

            {ticket.status === 'IN_PROGRESS' && (
              <>
                <p>
                  You are actively working on this
                  ticket.
                </p>

                <div className="support-workflow-actions">
                  <button
                    type="button"
                    className="secondary-button support-workflow-button"
                    disabled={isUpdating}
                    onClick={() =>
                      void handleStatusChange(
                        'WAITING_FOR_USER',
                      )
                    }
                  >
                    {isUpdating
                      ? 'Updating...'
                      : 'Wait for user'}
                  </button>

                  <button
                    type="button"
                    className="resolve-button support-workflow-button"
                    disabled={isUpdating}
                    onClick={() =>
                      void handleStatusChange(
                        'RESOLVED',
                      )
                    }
                  >
                    {isUpdating
                      ? 'Updating...'
                      : 'Resolve ticket'}
                  </button>
                </div>
              </>
            )}

            {ticket.status ===
              'WAITING_FOR_USER' && (
              <>
                <p>
                  Work is paused while waiting for
                  information from the requester.
                </p>

                <button
                  type="button"
                  className="primary-button support-workflow-button"
                  disabled={isUpdating}
                  onClick={() =>
                    void handleStatusChange(
                      'IN_PROGRESS',
                    )
                  }
                >
                  {isUpdating
                    ? 'Resuming...'
                    : 'Resume work'}
                </button>
              </>
            )}

            {ticket.status === 'RESOLVED' && (
              <div className="resolved-workflow-state">
                <div className="resolved-workflow-icon">
                  ✓
                </div>

                <h3>Ticket resolved</h3>

                <p>
                  This ticket has been completed and
                  removed from your active
                  assignments.
                </p>

                <button
                  type="button"
                  className="primary-button support-workflow-button"
                  onClick={() =>
                    navigate('/support/tickets')
                  }
                >
                  Back to my assignments
                </button>
              </div>
            )}

            <div className="workflow-rule">
              <span>Current status</span>

              <strong>
                {formatLabel(
                  ticket.status,
                )}
              </strong>
            </div>
          </aside>
        </div>
      )}
    </main>
  )
}