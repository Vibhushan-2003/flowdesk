
import {
  type FormEvent,
  useEffect,
  useState,
} from 'react'

import { useNavigate } from 'react-router-dom'

import {
  getMyTickets,
  type TicketListFilters,
} from '../api/ticket'

import { useAuth } from '../auth/useAuth'


import '../styles/ticket-filters.css'


import type {
  PageResponse,
  SlaStatus,
  TicketPriority,
  TicketStatus,
  TicketSummary,
} from '../types/ticket'

const PAGE_SIZE = 10

const STATUSES: TicketStatus[] = [
  'PENDING_APPROVAL',
  'OPEN',
  'ASSIGNED',
  'IN_PROGRESS',
  'WAITING_FOR_USER',
  'RESOLVED',
  'CLOSED',
  'CANCELLED',
]

const PRIORITIES: TicketPriority[] = [
  'LOW',
  'MEDIUM',
  'HIGH',
  'CRITICAL',
]

const DEFAULT_FILTERS: TicketListFilters = {
  search: '',
  status: '',
  priority: '',
  sort: 'newest',
}

function formatLabel(value: string) {
  return value.replaceAll('_', ' ')
}

function formatDate(value: string | null) {
  if (!value) {
    return 'Not started'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString()
}

function formatSlaStatus(status: SlaStatus) {
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
  const { accessToken } = useAuth()
  const navigate = useNavigate()

  const [page, setPage] = useState(0)

  const [searchText, setSearchText] = useState('')

  const [selectedStatus, setSelectedStatus] =
    useState<TicketStatus | ''>('')

  const [selectedPriority, setSelectedPriority] =
    useState<TicketPriority | ''>('')

  const [selectedSort, setSelectedSort] =
    useState<'newest' | 'oldest'>('newest')

  const [appliedFilters, setAppliedFilters] =
    useState<TicketListFilters>(DEFAULT_FILTERS)

  const [ticketPage, setTicketPage] =
    useState<PageResponse<TicketSummary> | null>(
      null,
    )

  const [isLoading, setIsLoading] = useState(true)

  const [error, setError] =
    useState<string | null>(null)

  function applyFilters(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    setPage(0)

    setAppliedFilters({
      search: searchText.trim(),
      status: selectedStatus,
      priority: selectedPriority,
      sort: selectedSort,
    })
  }

  function resetFilters() {
    setSearchText('')
    setSelectedStatus('')
    setSelectedPriority('')
    setSelectedSort('newest')

    setPage(0)
    setAppliedFilters({ ...DEFAULT_FILTERS })
  }

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
        const response = await getMyTickets(
          accessToken,
          page,
          PAGE_SIZE,
          appliedFilters,
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
          setError('Unable to load tickets')
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
    appliedFilters,
  ])

  const hasFilters = Boolean(
    appliedFilters.search ||
    appliedFilters.status ||
    appliedFilters.priority,
  )

  return (
    <main className="tickets-page">
      <header className="tickets-header">
        <div>
          <p className="eyebrow">
            FlowDesk
          </p>

          <h1>My tickets</h1>

          <p>
            View and track the IT support tickets
            you have created.
          </p>
        </div>

        <div className="tickets-header-actions">
          <button
            type="button"
            onClick={() => navigate('/dashboard')}
          >
            Dashboard
          </button>

          <button
            type="button"
            onClick={() => navigate('/tickets/new')}
          >
            Create ticket
          </button>
        </div>
      </header>

      <form
        onSubmit={applyFilters}
        aria-label="Ticket filters"
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 12,
          alignItems: 'end',
          margin: '20px 0',
        }}
      >
        <label>
          Search title or ticket number
          <input
            type="search"
            value={searchText}
            maxLength={100}
            onChange={(event) =>
              setSearchText(event.target.value)
            }
            placeholder="Search tickets"
          />
        </label>

        <label>
          Status
          <select
            value={selectedStatus}
            onChange={(event) =>
              setSelectedStatus(
                event.target.value as TicketStatus | '',
              )
            }
          >
            <option value="">All statuses</option>

            {STATUSES.map((status) => (
              <option
                key={status}
                value={status}
              >
                {formatLabel(status)}
              </option>
            ))}
          </select>
        </label>

        <label>
          Priority
          <select
            value={selectedPriority}
            onChange={(event) =>
              setSelectedPriority(
                event.target.value as TicketPriority | '',
              )
            }
          >
            <option value="">All priorities</option>

            {PRIORITIES.map((priority) => (
              <option
                key={priority}
                value={priority}
              >
                {priority}
              </option>
            ))}
          </select>
        </label>

        <label>
          Order
          <select
            value={selectedSort}
            onChange={(event) =>
              setSelectedSort(
                event.target.value as 'newest' | 'oldest',
              )
            }
          >
            <option value="newest">
              Newest first
            </option>
            <option value="oldest">
              Oldest first
            </option>
          </select>
        </label>

        <button type="submit">
          Apply filters
        </button>

        <button
          type="button"
          onClick={resetFilters}
        >
          Clear
        </button>
      </form>

      {isLoading && (
        <p>Loading tickets...</p>
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
              {hasFilters
                ? 'No matching tickets'
                : 'No tickets yet'}
            </h2>

            <p>
              {hasFilters
                ? 'Try changing your search or filters.'
                : 'You have not created any support tickets.'}
            </p>

            <button
              type="button"
              onClick={() => {
                if (hasFilters) {
                  resetFilters()
                } else {
                  navigate('/tickets/new')
                }
              }}
            >
              {hasFilters
                ? 'Clear filters'
                : 'Create your first ticket'}
            </button>
          </section>
        )}

      {!isLoading &&
        !error &&
        ticketPage &&
        ticketPage.content.length > 0 && (
          <>
            <section className="tickets-list">
              {ticketPage.content.map((ticket) => (
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
                        {ticket.ticketNumber}
                      </button>

                      <h2>{ticket.title}</h2>
                    </div>

                    <span className="ticket-status">
                      {formatLabel(ticket.status)}
                    </span>
                  </div>

                  <div className="ticket-list-meta">
                    <p>
                      <strong>Type:</strong>{' '}
                      {formatLabel(ticket.type)}
                    </p>

                    <p>
                      <strong>Priority:</strong>{' '}
                      {ticket.priority}
                    </p>

                    <p>
                      <strong>Created:</strong>{' '}
                      {formatDate(ticket.createdAt)}
                    </p>

                    <p>
                      <strong>Response SLA:</strong>{' '}
                      {formatSlaStatus(
                        ticket.responseSlaStatus,
                      )}
                    </p>

                    <p>
                      <strong>Response due:</strong>{' '}
                      {formatDate(ticket.responseDueAt)}
                    </p>

                    <p>
                      <strong>Resolution SLA:</strong>{' '}
                      {formatSlaStatus(
                        ticket.resolutionSlaStatus,
                      )}
                    </p>

                    <p>
                      <strong>Resolution due:</strong>{' '}
                      {formatDate(ticket.resolutionDueAt)}
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
              ))}
            </section>

            <nav
              className="pagination"
              aria-label="Ticket pagination"
            >
              <button
                type="button"
                disabled={ticketPage.first}
                onClick={() =>
                  setPage((currentPage) =>
                    Math.max(0, currentPage - 1),
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
                  setPage((currentPage) =>
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
                {ticketPage.totalElements}
              </strong>
            </p>
          </>
        )}
    </main>
  )
}
