import {
  useEffect,
  useState,
} from 'react'

import { useNavigate } from 'react-router-dom'

import {
  claimTicket,
  getSupportQueue,
} from '../api/ticket'

import { useAuth } from '../auth/useAuth'

import type {
  PageResponse,
  SlaStatus,
  TicketSummary,
} from '../types/ticket'

const PAGE_SIZE = 10

function formatLabel(value: string) {
  return value.replaceAll('_', ' ')
}

function formatDate(
  value: string | null,
) {
  if (!value) {
    return 'Not started'
  }

  const date =
    new Date(value)

  if (
    Number.isNaN(
      date.getTime(),
    )
  ) {
    return value
  }

  return date.toLocaleString()
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

export function SupportQueuePage() {
  const { accessToken } = useAuth()

  const navigate = useNavigate()

  const [page, setPage] = useState(0)

  const [queue, setQueue] =
    useState<PageResponse<TicketSummary> | null>(
      null,
    )

  const [isLoading, setIsLoading] =
    useState(true)

  const [error, setError] =
    useState<string | null>(null)

  const [
    claimingTicketNumber,
    setClaimingTicketNumber,
  ] = useState<string | null>(null)

  const [refreshKey, setRefreshKey] =
    useState(0)

  useEffect(() => {
    let cancelled = false

    async function loadQueue() {
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
          await getSupportQueue(
            accessToken,
            page,
            PAGE_SIZE,
          )

        if (!cancelled) {
          setQueue(response)
        }
      } catch (caughtError) {
        if (cancelled) {
          return
        }

        if (caughtError instanceof Error) {
          setError(caughtError.message)
        } else {
          setError(
            'Unable to load the support queue',
          )
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    void loadQueue()

    return () => {
      cancelled = true
    }
  }, [
    accessToken,
    page,
    refreshKey,
  ])

  async function handleClaim(
    ticketNumber: string,
  ) {
    if (!accessToken) {
      setError(
        'Your session is no longer valid',
      )

      return
    }

    setClaimingTicketNumber(ticketNumber)
    setError(null)

    try {
      const response = await claimTicket(
        accessToken,
        ticketNumber,
      )

      navigate(
        `/support/tickets/${response.ticketNumber}`,
      )
    } catch (caughtError) {
      if (caughtError instanceof Error) {
        setError(caughtError.message)
      } else {
        setError(
          'Unable to claim ticket',
        )
      }

      setRefreshKey(
        (current) => current + 1,
      )
    } finally {
      setClaimingTicketNumber(null)
    }
  }

  return (
    <main className="support-queue-page">
      <header className="support-queue-header">
        <div>
          <p className="eyebrow">
            FlowDesk Operations
          </p>

          <h1>Support queue</h1>

          <p>
            Review unassigned support tickets and
            claim the next issue you are ready to
            handle.
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
              navigate('/dashboard')
            }
          >
            Dashboard
          </button>
        </div>
      </header>

      <section className="queue-overview">
        <div>
          <span className="queue-overview-label">
            Open tickets
          </span>

          <strong>
            {queue?.totalElements ?? 0}
          </strong>
        </div>

        <div>
          <span className="queue-overview-label">
            Queue order
          </span>

          <strong>
            Oldest first
          </strong>
        </div>

        <div>
          <span className="queue-overview-label">
            Assignment
          </span>

          <strong>
            Claim based
          </strong>
        </div>
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
            Loading support queue...
          </p>
        </section>
      )}

      {!isLoading &&
        !error &&
        queue &&
        queue.content.length === 0 && (
          <section className="support-empty-state">
            <div className="support-empty-mark">
              ✓
            </div>

            <h2>Queue is clear</h2>

            <p>
              There are currently no open tickets
              waiting to be claimed.
            </p>

            <button
              type="button"
              className="primary-button"
              onClick={() =>
                navigate(
                  '/support/tickets',
                )
              }
            >
              View my assignments
            </button>
          </section>
        )}

      {!isLoading &&
        !error &&
        queue &&
        queue.content.length > 0 && (
          <section className="support-queue-list">
            {queue.content.map((ticket) => {
              const isClaiming =
                claimingTicketNumber ===
                ticket.ticketNumber

              return (
                <article
                  className="support-ticket-card"
                  key={ticket.id}
                >
                  <div className="support-ticket-main">
                    <div className="support-ticket-top">
                      <div>
                        <p className="support-ticket-number">
                          {ticket.ticketNumber}
                        </p>

                        <h2>
                          {ticket.title}
                        </h2>
                      </div>

                      <span className="queue-status-badge">
                        {formatLabel(
                          ticket.status,
                        )}
                      </span>
                    </div>

                    <div className="support-ticket-meta">
                      <div>
                        <span>
                          Type
                        </span>

                        <strong>
                          {formatLabel(
                            ticket.type,
                          )}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Priority
                        </span>

                        <strong>
                          {ticket.priority}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Created
                        </span>

                        <strong>
                          {formatDate(
                            ticket.createdAt,
                          )}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Response SLA
                        </span>

                        <strong>
                          {formatSlaStatus(
                            ticket.responseSlaStatus,
                          )}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Response due
                        </span>

                        <strong>
                          {formatDate(
                            ticket.responseDueAt,
                          )}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Resolution SLA
                        </span>

                        <strong>
                          {formatSlaStatus(
                            ticket.resolutionSlaStatus,
                          )}
                        </strong>
                      </div>

                      <div>
                        <span>
                          Resolution due
                        </span>

                        <strong>
                          {formatDate(
                            ticket.resolutionDueAt,
                          )}
                        </strong>
                      </div>
                    </div>
                  </div>

                  <div className="support-ticket-action">
                    <button
                      type="button"
                      disabled={
                        claimingTicketNumber !==
                        null
                      }
                      onClick={() =>
                        void handleClaim(
                          ticket.ticketNumber,
                        )
                      }
                    >
                      {isClaiming
                        ? 'Claiming...'
                        : 'Claim ticket'}
                    </button>
                  </div>
                </article>
              )
            })}
          </section>
        )}

      {!isLoading &&
        !error &&
        queue &&
        queue.totalElements > 0 && (
          <nav
            className="pagination"
            aria-label="Support queue pagination"
          >
            <button
              type="button"
              disabled={queue.first}
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
              Page {queue.page + 1} of{' '}
              {queue.totalPages}
            </span>

            <button
              type="button"
              disabled={queue.last}
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
