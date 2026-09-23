import {
  useEffect,
  useState,
} from 'react'

import { useNavigate } from 'react-router-dom'

import { getMyAssignedTickets } from '../api/ticket'
import { useAuth } from '../auth/useAuth'

import type {
  PageResponse,
  SlaStatus,
  SupportTicketSummary,
} from '../types/ticket'

const PAGE_SIZE = 10

function formatLabel(value: string) {
  return value.replaceAll('_', ' ')
}

function formatDate(value: string) {
  return new Date(value).toLocaleString()
}

function formatSlaStatus(
  status: SlaStatus,
) {
  switch (status) {
    case 'MET':
      return 'Met'

    case 'BREACHED':
      return 'Breached'

    case 'PENDING':
      return 'Pending'

    case 'UNKNOWN':
      return 'Unknown'
  }
}

function getStatusClass(status: string) {
  return `support-status support-status-${status
    .toLowerCase()
    .replaceAll('_', '-')}`
}

export function MyAssignedTicketsPage() {
  const { accessToken } = useAuth()
  const navigate = useNavigate()

  const [page, setPage] = useState(0)

  const [ticketPage, setTicketPage] =
    useState<PageResponse<SupportTicketSummary> | null>(
      null,
    )

  const [isLoading, setIsLoading] =
    useState(true)

  const [error, setError] =
    useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadTickets() {
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
          await getMyAssignedTickets(
            accessToken,
            page,
            PAGE_SIZE,
          )

        if (!cancelled) {
          setTicketPage(response)
        }
      } catch (caughtError) {
        if (cancelled) {
          return
        }

        if (caughtError instanceof Error) {
          setError(caughtError.message)
        } else {
          setError(
            'Unable to load assigned tickets',
          )
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    void loadTickets()

    return () => {
      cancelled = true
    }
  }, [accessToken, page])

  return (
    <main className="support-work-page">
      <header className="support-page-header">
        <div>
          <p className="eyebrow">
            FlowDesk Operations
          </p>

          <h1>My assigned tickets</h1>

          <p className="support-page-description">
            Your active support workload. Open a
            ticket to start work, wait for the
            requester, resume work, or resolve the
            issue.
          </p>
        </div>

        <div className="support-header-actions">
          <button
            type="button"
            className="secondary-button"
            onClick={() =>
              navigate('/support/queue')
            }
          >
            Support queue
          </button>

          <button
            type="button"
            className="secondary-button"
            onClick={() =>
              navigate('/dashboard')
            }
          >
            Dashboard
          </button>
        </div>
      </header>

      <section className="support-overview">
        <article>
          <span>Active assignments</span>

          <strong>
            {ticketPage?.totalElements ?? 0}
          </strong>
        </article>

        <article>
          <span>Workspace</span>

          <strong>Support engineer</strong>
        </article>

        <article>
          <span>Workflow</span>

          <strong>Assignment based</strong>
        </article>
      </section>

      {error && (
        <div
          className="support-alert support-alert-error"
          role="alert"
        >
          {error}
        </div>
      )}

      {isLoading && (
        <section className="support-loading-state">
          <div className="support-loading-dot" />

          <p>
            Loading your assigned tickets...
          </p>
        </section>
      )}

      {!isLoading &&
        !error &&
        ticketPage &&
        ticketPage.content.length === 0 && (
          <section className="support-empty-state">
            <div className="support-empty-mark">
              ✓
            </div>

            <h2>No active assignments</h2>

            <p>
              You currently have no tickets assigned
              to you. Open the support queue when
              you are ready to take another issue.
            </p>

            <button
              type="button"
              className="primary-button"
              onClick={() =>
                navigate('/support/queue')
              }
            >
              Open support queue
            </button>
          </section>
        )}

      {!isLoading &&
        !error &&
        ticketPage &&
        ticketPage.content.length > 0 && (
          <section className="assigned-ticket-list">
            {ticketPage.content.map((ticket) => (
              <article
                className="assigned-ticket-card"
                key={ticket.ticketId}
              >
                <div className="assigned-ticket-card-main">
                  <div className="assigned-ticket-heading">
                    <div>
                      <button
                        type="button"
                        className="support-ticket-link"
                        onClick={() =>
                          navigate(
                            `/support/tickets/${ticket.ticketNumber}`,
                          )
                        }
                      >
                        {ticket.ticketNumber}
                      </button>

                      <h2>
                        {ticket.title}
                      </h2>
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
                  </div>

                  <div className="assigned-ticket-meta">
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
                      <span>Assigned</span>

                      <strong>
                        {formatDate(
                          ticket.assignedAt,
                        )}
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
                      <span>Response SLA</span>

                      <strong>
                        {formatSlaStatus(
                          ticket.responseSlaStatus,
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>Response due</span>

                      <strong>
                        {formatDate(
                          ticket.responseDueAt,
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>Resolution SLA</span>

                      <strong>
                        {formatSlaStatus(
                          ticket.resolutionSlaStatus,
                        )}
                      </strong>
                    </div>

                    <div>
                      <span>Resolution due</span>

                      <strong>
                        {formatDate(
                          ticket.resolutionDueAt,
                        )}
                      </strong>
                    </div>
                  </div>
                </div>

                <div className="assigned-ticket-card-action">
                  <button
                    type="button"
                    className="primary-button"
                    onClick={() =>
                      navigate(
                        `/support/tickets/${ticket.ticketNumber}`,
                      )
                    }
                  >
                    Open ticket
                  </button>
                </div>
              </article>
            ))}
          </section>
        )}

      {!isLoading &&
        !error &&
        ticketPage &&
        ticketPage.totalElements > 0 && (
          <nav
            className="pagination"
            aria-label="Assigned tickets pagination"
          >
            <button
              type="button"
              disabled={ticketPage.first}
              onClick={() =>
                setPage((current) =>
                  Math.max(
                    0,
                    current - 1,
                  ),
                )
              }
            >
              Previous
            </button>

            <span>
              Page {ticketPage.page + 1} of{' '}
              {ticketPage.totalPages}
            </span>

            <button
              type="button"
              disabled={ticketPage.last}
              onClick={() =>
                setPage(
                  (current) => current + 1,
                )
              }
            >
              Next
            </button>
          </nav>
        )}
    </main>
  )
}
