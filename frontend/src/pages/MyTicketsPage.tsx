import {
  useEffect,
  useState,
} from 'react'

import { useNavigate } from 'react-router-dom'

import { getMyTickets } from '../api/ticket'
import { useAuth } from '../auth/useAuth'

import type {
  PageResponse,
  SlaStatus,
  TicketSummary,
} from '../types/ticket'

const PAGE_SIZE = 10

function formatLabel(
  value: string,
) {
  return value.replaceAll(
    '_',
    ' ',
  )
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

export function MyTicketsPage() {
  const { accessToken } =
    useAuth()

  const navigate =
    useNavigate()

  const [
    page,
    setPage,
  ] =
    useState(0)

  const [
    ticketPage,
    setTicketPage,
  ] =
    useState<PageResponse<TicketSummary> | null>(
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
    useState<string | null>(
      null,
    )

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
          await getMyTickets(
            accessToken,
            page,
            PAGE_SIZE,
          )

        if (!cancelled) {
          setTicketPage(
            response,
          )
        }
      } catch (caughtError) {
        if (cancelled) {
          return
        }

        if (
          caughtError instanceof Error
        ) {
          setError(
            caughtError.message,
          )
        } else {
          setError(
            'Unable to load tickets',
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
  }, [
    accessToken,
    page,
  ])

  return (
    <main className="tickets-page">
      <header className="tickets-header">
        <div>
          <p className="eyebrow">
            FlowDesk
          </p>

          <h1>
            My tickets
          </h1>

          <p>
            View and track the IT support tickets
            you have created.
          </p>
        </div>

        <div className="tickets-header-actions">
          <button
            type="button"
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
            onClick={() =>
              navigate(
                '/tickets/new',
              )
            }
          >
            Create ticket
          </button>
        </div>
      </header>

      {isLoading && (
        <p>
          Loading tickets...
        </p>
      )}

      {error && (
        <p
          className="form-error"
          role="alert"
        >
          {error}
        </p>
      )}

      {!isLoading &&
        !error &&
        ticketPage &&
        ticketPage.content.length === 0 && (
          <section className="empty-state">
            <h2>
              No tickets yet
            </h2>

            <p>
              You have not created any support
              tickets.
            </p>

            <button
              type="button"
              onClick={() =>
                navigate(
                  '/tickets/new',
                )
              }
            >
              Create your first ticket
            </button>
          </section>
        )}

      {!isLoading &&
        !error &&
        ticketPage &&
        ticketPage.content.length > 0 && (
          <>
            <section className="tickets-list">
              {ticketPage.content.map(
                (ticket) => (
                  <article
                    className="ticket-list-card"
                    key={ticket.id}
                  >
                    <div className="ticket-list-heading">
                      <div>
                        <button
                          type="button"
                          className="ticket-number-link"
                          onClick={() =>
                            navigate(
                              `/tickets/${ticket.ticketNumber}`,
                            )
                          }
                        >
                          {
                            ticket.ticketNumber
                          }
                        </button>

                        <h2>
                          {ticket.title}
                        </h2>
                      </div>

                      <span className="ticket-status">
                        {formatLabel(
                          ticket.status,
                        )}
                      </span>
                    </div>

                    <div className="ticket-list-meta">
                      <p>
                        <strong>
                          Type:
                        </strong>{' '}
                        {formatLabel(
                          ticket.type,
                        )}
                      </p>

                      <p>
                        <strong>
                          Priority:
                        </strong>{' '}
                        {ticket.priority}
                      </p>

                      <p>
                        <strong>
                          Created:
                        </strong>{' '}
                        {formatDate(
                          ticket.createdAt,
                        )}
                      </p>

                      <p>
                        <strong>
                          Response SLA:
                        </strong>{' '}
                        {formatSlaStatus(
                          ticket.responseSlaStatus,
                        )}
                      </p>

                      <p>
                        <strong>
                          Response due:
                        </strong>{' '}
                        {formatDate(
                          ticket.responseDueAt,
                        )}
                      </p>

                      <p>
                        <strong>
                          Resolution SLA:
                        </strong>{' '}
                        {formatSlaStatus(
                          ticket.resolutionSlaStatus,
                        )}
                      </p>

                      <p>
                        <strong>
                          Resolution due:
                        </strong>{' '}
                        {formatDate(
                          ticket.resolutionDueAt,
                        )}
                      </p>
                    </div>

                    <button
                      type="button"
                      onClick={() =>
                        navigate(
                          `/tickets/${ticket.ticketNumber}`,
                        )
                      }
                    >
                      View ticket
                    </button>
                  </article>
                ),
              )}
            </section>

            <nav
              className="pagination"
              aria-label="Ticket pagination"
            >
              <button
                type="button"
                disabled={
                  ticketPage.first
                }
                onClick={() =>
                  setPage(
                    (currentPage) =>
                      Math.max(
                        0,
                        currentPage - 1,
                      ),
                  )
                }
              >
                Previous
              </button>

              <span>
                Page{' '}
                {ticketPage.page + 1}{' '}
                of{' '}
                {ticketPage.totalPages}
              </span>

              <button
                type="button"
                disabled={
                  ticketPage.last
                }
                onClick={() =>
                  setPage(
                    (currentPage) =>
                      currentPage + 1,
                  )
                }
              >
                Next
              </button>
            </nav>

            <p>
              Total tickets:{' '}
              <strong>
                {
                  ticketPage
                    .totalElements
                }
              </strong>
            </p>
          </>
        )}
    </main>
  )
}