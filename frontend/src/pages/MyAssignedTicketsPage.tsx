import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTicketListUrlFilters } from '../hooks/useTicketListUrlFilters'

import {
  getMyAssignedTickets,
  getSupportTicketHistory,
} from '../api/ticket'
import { useAuth } from '../auth/useAuth'
import { TicketListOverview } from '../components/TicketListOverview'

import type {
  PageResponse,
  SlaStatus,
  SupportTicketSummary,
  TicketPriority,
  TicketStatus,
} from '../types/ticket'

import '../styles/ticket-filters.css'
import '../styles/ticket-list-overview.css'

const PAGE_SIZE = 10

type SupportViewMode = 'active' | 'resolved' | 'history'

const ACTIVE_STATUSES: TicketStatus[] = [
  'ASSIGNED',
  'IN_PROGRESS',
  'WAITING_FOR_USER',
  'RESOLVED',
  'CLOSED',
]

const HISTORY_STATUSES: TicketStatus[] = [
  'RESOLVED',
  'CLOSED',
]

const PRIORITIES: TicketPriority[] = [
  'LOW',
  'MEDIUM',
  'HIGH',
  'CRITICAL',
]

function formatLabel(value: string) {
  return value.replaceAll('_', ' ')
}

function formatDate(value: string | null) {
  if (!value) return 'Not started'
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString()
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

function getStatusClass(status: string) {
  return `support-status support-status-${status
    .toLowerCase()
    .replaceAll('_', '-')}`
}

export function MyAssignedTicketsPage() {
  const { accessToken } = useAuth()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()

  const {
    page,
    setPage,
    appliedFilters,
    searchText,
    setSearchText,
    selectedStatus,
    setSelectedStatus,
    selectedPriority,
    setSelectedPriority,
    selectedSort,
    setSelectedSort,
    applyFilters,
    resetFilters,
  } = useTicketListUrlFilters()

  const viewParam = searchParams.get('view')
  const isResolvedStatus = appliedFilters.status === 'RESOLVED'
  const isClosedStatus = appliedFilters.status === 'CLOSED'

  let currentView: SupportViewMode = 'active'
  if (viewParam === 'history' || isClosedStatus) {
    currentView = 'history'
  } else if (viewParam === 'resolved' || isResolvedStatus) {
    currentView = 'resolved'
  }

  const isHistoryQuery =
    currentView === 'resolved' || currentView === 'history'

  const [ticketPage, setTicketPage] =
    useState<PageResponse<SupportTicketSummary> | null>(null)

  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refreshVersion, setRefreshVersion] = useState(0)

  function refreshTickets() {
    setRefreshVersion((version) => version + 1)
  }

  function handleViewChange(newView: SupportViewMode) {
    const newParams = new URLSearchParams()
    if (appliedFilters.search) {
      newParams.set('search', appliedFilters.search)
    }
    if (appliedFilters.priority) {
      newParams.set('priority', appliedFilters.priority)
    }
    if (appliedFilters.sort === 'oldest') {
      newParams.set('sort', 'oldest')
    }

    if (newView === 'resolved') {
      newParams.set('status', 'RESOLVED')
    } else if (newView === 'history') {
      newParams.set('view', 'history')
    }
    setSearchParams(newParams)
  }

  function handleResetFilters() {
    if (currentView === 'resolved') {
      setSearchParams(new URLSearchParams({ status: 'RESOLVED' }))
    } else if (currentView === 'history') {
      setSearchParams(new URLSearchParams({ view: 'history' }))
    } else {
      resetFilters()
    }
  }

  useEffect(() => {
    let cancelled = false

    async function loadTickets() {
      if (!accessToken) {
        if (!cancelled) {
          setTicketPage(null)
          setError('Your session is no longer valid')
          setIsLoading(false)
        }
        return
      }

      setIsLoading(true)
      setError(null)

      try {
        const response = isHistoryQuery
          ? await getSupportTicketHistory(
              accessToken,
              page,
              PAGE_SIZE,
              currentView === 'resolved'
                ? { ...appliedFilters, status: 'RESOLVED' }
                : appliedFilters,
            )
          : await getMyAssignedTickets(
              accessToken,
              page,
              PAGE_SIZE,
              appliedFilters,
            )

        if (!cancelled) {
          setTicketPage(response)
        }
      } catch (caughtError) {
        if (!cancelled) {
          setTicketPage(null)
          setError(
            caughtError instanceof Error
              ? caughtError.message
              : isHistoryQuery
                ? 'Unable to load ticket history'
                : 'Unable to load assigned tickets',
          )
        }
      } finally {
        if (!cancelled) setIsLoading(false)
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
    currentView,
    isHistoryQuery,
    refreshVersion,
  ])

  const hasFilters = Boolean(
    appliedFilters.search ||
      appliedFilters.priority ||
      (currentView === 'history' && appliedFilters.status) ||
      (currentView === 'active' && appliedFilters.status),
  )

  const availableStatuses =
    currentView === 'history'
      ? HISTORY_STATUSES
      : currentView === 'resolved'
        ? (['RESOLVED'] as TicketStatus[])
        : ACTIVE_STATUSES

  return (
    <main className="support-work-page">
      <header className="support-page-header">
        <div>
          <p className="eyebrow">FlowDesk Operations</p>
          <h1>
            {currentView === 'resolved'
              ? 'Resolved tickets'
              : currentView === 'history'
                ? 'Ticket history'
                : 'My assigned tickets'}
          </h1>
          <p className="support-page-description">
            {currentView === 'resolved'
              ? 'Tickets you have successfully resolved. Review resolution details and diagnostic attachments.'
              : currentView === 'history'
                ? 'Your historical record of completed and closed tickets.'
                : 'Your active support workload. Open a ticket to start work, wait for the requester, resume work, or resolve the issue.'}
          </p>
        </div>

        <div className="support-header-actions">
          <button
            type="button"
            className="secondary-button"
            onClick={() => navigate('/support/queue')}
          >
            Support queue
          </button>
          <button
            type="button"
            className="secondary-button"
            onClick={() => navigate('/dashboard')}
          >
            Dashboard
          </button>
        </div>
      </header>

      {/* Day 51 Phase 5A: Support navigation tabs */}
      <nav
        className="support-nav-tabs"
        aria-label="Support ticket navigation"
      >
        <button
          type="button"
          className={`support-nav-tab ${
            currentView === 'active' ? 'active' : ''
          }`}
          onClick={() => handleViewChange('active')}
        >
          Active Tickets
        </button>
        <button
          type="button"
          className={`support-nav-tab ${
            currentView === 'resolved' ? 'active' : ''
          }`}
          onClick={() => handleViewChange('resolved')}
        >
          Resolved Tickets
        </button>
        <button
          type="button"
          className={`support-nav-tab ${
            currentView === 'history' ? 'active' : ''
          }`}
          onClick={() => handleViewChange('history')}
        >
          Ticket History
        </button>
      </nav>

      <form
        onSubmit={applyFilters}
        aria-label="Assigned ticket filters"
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
            value={
              currentView === 'resolved'
                ? 'RESOLVED'
                : selectedStatus
            }
            disabled={currentView === 'resolved'}
            onChange={(event) =>
              setSelectedStatus(
                event.target.value as TicketStatus | '',
              )
            }
          >
            <option value="">
              {currentView === 'history'
                ? 'All historical statuses'
                : 'All statuses'}
            </option>
            {availableStatuses.map((status) => (
              <option key={status} value={status}>
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
              <option key={priority} value={priority}>
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
            <option value="newest">Newest first</option>
            <option value="oldest">Oldest first</option>
          </select>
        </label>

        <button type="submit">Apply filters</button>
        <button
          type="button"
          onClick={handleResetFilters}
        >
          Clear
        </button>
      </form>

      <TicketListOverview
        filters={appliedFilters}
        totalElements={
          error ? null : ticketPage?.totalElements ?? null
        }
        visibleCount={
          error ? 0 : ticketPage?.content.length ?? 0
        }
        isLoading={isLoading}
        onRefresh={refreshTickets}
      />

      <section className="support-overview">
        <article>
          <span>
            {currentView === 'resolved'
              ? 'Resolved tickets'
              : currentView === 'history'
                ? 'Historical tickets'
                : 'Active assignments'}
          </span>
          <strong>
            {error ? '—' : ticketPage?.totalElements ?? 0}
          </strong>
        </article>
        <article>
          <span>Workspace</span>
          <strong>Support engineer</strong>
        </article>
        <article>
          <span>Workflow</span>
          <strong>
            {isHistoryQuery
              ? 'Resolution history'
              : 'Assignment based'}
          </strong>
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
            {isHistoryQuery
              ? 'Loading ticket history...'
              : 'Loading your assigned tickets...'}
          </p>
        </section>
      )}

      {!isLoading &&
        !error &&
        ticketPage &&
        ticketPage.content.length === 0 && (
          <section className="support-empty-state">
            <div className="support-empty-mark">✓</div>
            <h2>
              {hasFilters
                ? currentView === 'resolved'
                  ? 'No matching resolved tickets'
                  : currentView === 'history'
                    ? 'No matching historical tickets'
                    : 'No matching assignments'
                : currentView === 'resolved'
                  ? 'No resolved tickets'
                  : currentView === 'history'
                    ? 'No ticket history'
                    : 'No active assignments'}
            </h2>
            <p>
              {hasFilters
                ? 'Try changing your search or filters.'
                : currentView === 'resolved'
                  ? 'You have not resolved any tickets yet.'
                  : currentView === 'history'
                    ? 'You have no completed or closed ticket history.'
                    : 'You currently have no tickets assigned to you. Open the support queue when you are ready to take another issue.'}
            </p>
            <button
              type="button"
              className="primary-button"
              onClick={() =>
                hasFilters
                  ? handleResetFilters()
                  : navigate('/support/queue')
              }
            >
              {hasFilters
                ? 'Clear filters'
                : 'Open support queue'}
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
                      <h2>{ticket.title}</h2>
                    </div>

                    <span
                      className={getStatusClass(ticket.status)}
                    >
                      {formatLabel(ticket.status)}
                    </span>
                  </div>

                  <div className="assigned-ticket-meta">
                    <div>
                      <span>Type</span>
                      <strong>
                        {formatLabel(ticket.type)}
                      </strong>
                    </div>
                    <div>
                      <span>Priority</span>
                      <strong>{ticket.priority}</strong>
                    </div>
                    <div>
                      <span>Assigned</span>
                      <strong>
                        {formatDate(ticket.assignedAt)}
                      </strong>
                    </div>
                    <div>
                      <span>Created</span>
                      <strong>
                        {formatDate(ticket.createdAt)}
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
                        {formatDate(ticket.responseDueAt)}
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
                        {formatDate(ticket.resolutionDueAt)}
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
                    {ticket.status === 'RESOLVED' ||
                    ticket.status === 'CLOSED'
                      ? 'View details'
                      : 'Open ticket'}
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
                  Math.max(0, current - 1),
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
                setPage((current) => current + 1)
              }
            >
              Next
            </button>
          </nav>
        )}
    </main>
  )
}
