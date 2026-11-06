import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, RefreshCw, Search, ArrowRight, AlertCircle } from 'lucide-react'
import { useTicketListUrlFilters } from '../hooks/useTicketListUrlFilters'

import { getMyTickets } from '../api/ticket'
import { useAuth } from '../auth/useAuth'
import { TicketListOverview } from '../components/TicketListOverview'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { StatusBadge } from '../components/ui/StatusBadge'
import { PriorityBadge } from '../components/ui/PriorityBadge'
import { Badge } from '../components/ui/Badge'
import { Pagination } from '../components/ui/Pagination'
import { EmptyState } from '../components/ui/EmptyState'

import type {
  PageResponse,
  TicketPriority,
  TicketStatus,
  TicketSummary,
} from '../types/ticket'

import {
  formatDate,
  formatLabel,
  formatSlaStatus,
} from '../utils/formatters'

import '../styles/ticket-filters.css'
import '../styles/ticket-list-overview.css'

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

export function MyTicketsPage() {
  const { accessToken } = useAuth()
  const navigate = useNavigate()

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

  const [ticketPage, setTicketPage] = useState<PageResponse<TicketSummary> | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refreshVersion, setRefreshVersion] = useState(0)

  function refreshTickets() {
    setRefreshVersion((version) => version + 1)
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
        if (!cancelled) {
          setTicketPage(null)
          setError(
            caughtError instanceof Error
              ? caughtError.message
              : 'Unable to load tickets',
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
  }, [accessToken, page, appliedFilters, refreshVersion])

  const hasFilters = Boolean(
    appliedFilters.search ||
    appliedFilters.status ||
    appliedFilters.priority,
  )

  return (
    <div className="tickets-page horizon-tickets-workspace">
      {/* Horizon Page Header */}
      <PageHeader
        title="My Tickets"
        subtitle="View and track the IT support tickets and service requests you have created."
        actions={
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Button
              variant="outline"
              size="sm"
              onClick={refreshTickets}
              disabled={isLoading}
              leadingIcon={<RefreshCw size={14} className={isLoading ? 'horizon-btn__spinner' : ''} />}
            >
              Refresh
            </Button>
            <Button
              variant="primary"
              size="sm"
              onClick={() => navigate('/tickets/new')}
              leadingIcon={<Plus size={16} />}
            >
              Create ticket
            </Button>
          </div>
        }
      />

      {/* Structured Filter Bar */}
      <form
        onSubmit={applyFilters}
        aria-label="Ticket filters"
        className="horizon-filter-bar ticket-filters-form"
      >
        <div className="horizon-filter-fields">
          {/* Keyword Search */}
          <div className="horizon-form-field" style={{ minWidth: '220px', flex: 1, margin: 0 }}>
            <label htmlFor="ticket-search" className="horizon-label sr-only">
              Search title or ticket number
            </label>
            <div className="horizon-input-wrapper">
              <Search size={16} className="horizon-input-icon horizon-input-icon--leading" />
              <input
                id="ticket-search"
                type="search"
                className="horizon-input has-leading-icon"
                value={searchText}
                maxLength={100}
                onChange={(event) => setSearchText(event.target.value)}
                placeholder="Search title or ticket number..."
              />
            </div>
          </div>

          {/* Status Filter */}
          <div className="horizon-form-field" style={{ width: '160px', margin: 0 }}>
            <label htmlFor="ticket-status-filter" className="horizon-label sr-only">
              Status
            </label>
            <div className="horizon-select-wrapper">
              <select
                id="ticket-status-filter"
                className="horizon-select"
                value={selectedStatus}
                onChange={(event) =>
                  setSelectedStatus(event.target.value as TicketStatus | '')
                }
              >
                <option value="">All statuses</option>
                {STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {formatLabel(status)}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Priority Filter */}
          <div className="horizon-form-field" style={{ width: '150px', margin: 0 }}>
            <label htmlFor="ticket-priority-filter" className="horizon-label sr-only">
              Priority
            </label>
            <div className="horizon-select-wrapper">
              <select
                id="ticket-priority-filter"
                className="horizon-select"
                value={selectedPriority}
                onChange={(event) =>
                  setSelectedPriority(event.target.value as TicketPriority | '')
                }
              >
                <option value="">All priorities</option>
                {PRIORITIES.map((priority) => (
                  <option key={priority} value={priority}>
                    {formatLabel(priority)}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Sort Order */}
          <div className="horizon-form-field" style={{ width: '150px', margin: 0 }}>
            <label htmlFor="ticket-sort-filter" className="horizon-label sr-only">
              Order
            </label>
            <div className="horizon-select-wrapper">
              <select
                id="ticket-sort-filter"
                className="horizon-select"
                value={selectedSort}
                onChange={(event) =>
                  setSelectedSort(event.target.value as 'newest' | 'oldest')
                }
              >
                <option value="newest">Newest first</option>
                <option value="oldest">Oldest first</option>
              </select>
            </div>
          </div>
        </div>

        <div className="horizon-filter-actions">
          <button
            type="submit"
            className="horizon-btn horizon-btn--primary horizon-btn--sm"
          >
            Apply filters
          </button>
          <button
            type="button"
            className="horizon-btn horizon-btn--ghost horizon-btn--sm"
            onClick={resetFilters}
          >
            Clear
          </button>
        </div>
      </form>

      {/* Live Operational Stats Summary */}
      <TicketListOverview
        filters={appliedFilters}
        totalElements={error ? null : ticketPage?.totalElements ?? null}
        visibleCount={error ? 0 : ticketPage?.content.length ?? 0}
        isLoading={isLoading}
        onRefresh={refreshTickets}
      />

      {/* Loading feedback */}
      {isLoading && (
        <div style={{ textAlign: 'center', padding: '2rem', color: 'var(--color-text-secondary)' }}>
          <p>Loading tickets...</p>
        </div>
      )}

      {/* Error state */}
      {error && (
        <div className="form-error horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ margin: '1.5rem 0' }}>
          <AlertCircle size={18} className="horizon-auth-alert-icon" />
          <span>{error}</span>
        </div>
      )}

      {/* Empty State */}
      {!isLoading && !error && ticketPage && ticketPage.content.length === 0 && (
        <section className="empty-state" style={{ margin: '2rem 0' }}>
          <EmptyState
            title={hasFilters ? 'No matching tickets' : 'No tickets yet'}
            description={
              hasFilters
                ? 'Try changing your search or filters to find what you are looking for.'
                : 'You have not created any support tickets yet. Submit an issue or request to get assistance from IT.'
            }
            action={
              <Button
                variant={hasFilters ? 'secondary' : 'primary'}
                size="md"
                onClick={() =>
                  hasFilters ? resetFilters() : navigate('/tickets/new')
                }
              >
                {hasFilters ? 'Clear filters' : 'Create your first ticket'}
              </Button>
            }
          />
        </section>
      )}

      {/* Data Presentation: Desktop Table & Mobile Cards */}
      {!isLoading && !error && ticketPage && ticketPage.content.length > 0 && (
        <>
          {/* Desktop Table View (>= 768px) */}
          <div className="desktop-ticket-table-view horizon-table-container">
            <table className="horizon-table">
              <thead>
                <tr>
                  <th style={{ width: '130px' }}>Ticket #</th>
                  <th>Subject</th>
                  <th style={{ width: '140px' }}>Type</th>
                  <th style={{ width: '110px' }}>Priority</th>
                  <th style={{ width: '140px' }}>Status</th>
                  <th style={{ width: '150px' }}>Created</th>
                  <th style={{ width: '120px' }}>Response SLA</th>
                  <th style={{ width: '110px', textAlign: 'right' }}>Action</th>
                </tr>
              </thead>
              <tbody>
                {ticketPage.content.map((ticket) => (
                  <tr
                    key={ticket.id}
                    className="horizon-table-row--clickable"
                    onClick={() => navigate(`/tickets/${ticket.ticketNumber}`)}
                  >
                    <td>
                      <button
                        type="button"
                        className="ticket-number-link"
                        style={{
                          fontWeight: 'var(--font-weight-bold)',
                          color: 'var(--color-primary)',
                          background: 'none',
                          border: 'none',
                          cursor: 'pointer',
                          fontFamily: 'var(--font-family-mono)',
                          padding: 0,
                        }}
                        onClick={(e) => {
                          e.stopPropagation()
                          navigate(`/tickets/${ticket.ticketNumber}`)
                        }}
                      >
                        {ticket.ticketNumber}
                      </button>
                    </td>
                    <td>
                      <span style={{ fontWeight: 'var(--font-weight-medium)', color: 'var(--color-text-primary)' }}>
                        {ticket.title}
                      </span>
                    </td>
                    <td>
                      <Badge variant="neutral" size="sm">
                        {formatLabel(ticket.type)}
                      </Badge>
                    </td>
                    <td>
                      <PriorityBadge priority={ticket.priority} size="sm" />
                    </td>
                    <td>
                      <StatusBadge status={ticket.status} size="sm" />
                    </td>
                    <td style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-xs)' }}>
                      {formatDate(ticket.createdAt)}
                    </td>
                    <td>
                      <span
                        style={{
                          fontSize: 'var(--font-size-xs)',
                          fontWeight: 'var(--font-weight-medium)',
                          color:
                            ticket.responseSlaStatus === 'BREACHED'
                              ? 'var(--color-danger)'
                              : ticket.responseSlaStatus === 'MET'
                              ? 'var(--color-success)'
                              : 'var(--color-text-secondary)',
                        }}
                      >
                        {formatSlaStatus(ticket.responseSlaStatus)}
                      </span>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <button
                        type="button"
                        className="horizon-btn horizon-btn--ghost horizon-btn--sm"
                        onClick={(e) => {
                          e.stopPropagation()
                          navigate(`/tickets/${ticket.ticketNumber}`)
                        }}
                      >
                        <span>View ticket</span>
                        <ArrowRight size={14} aria-hidden="true" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile Card View (< 768px) and E2E DOM compatibility */}
          <section className="tickets-list mobile-ticket-card-view">
            {ticketPage.content.map((ticket) => (
              <article
                className="ticket-list-card horizon-card"
                key={ticket.id}
                style={{ marginBottom: '1rem', padding: '1rem' }}
              >
                <div className="ticket-list-heading" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.75rem' }}>
                  <div>
                    <button
                      type="button"
                      className="ticket-number-link"
                      onClick={() => navigate(`/tickets/${ticket.ticketNumber}`)}
                    >
                      {ticket.ticketNumber}
                    </button>
                    <h2 style={{ fontSize: 'var(--font-size-md)', margin: '4px 0 0' }}>{ticket.title}</h2>
                  </div>
                  <StatusBadge status={ticket.status} size="sm" />
                </div>

                <div className="ticket-list-meta" style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
                  <p>
                    <strong>Type:</strong> {formatLabel(ticket.type)}
                  </p>
                  <p style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <strong>Priority:</strong>
                    <PriorityBadge priority={ticket.priority} size="sm" />
                  </p>
                  <p>
                    <strong>Created:</strong> {formatDate(ticket.createdAt)}
                  </p>
                  <p>
                    <strong>Response SLA:</strong> {formatSlaStatus(ticket.responseSlaStatus)}
                  </p>
                  <p>
                    <strong>Response due:</strong> {formatDate(ticket.responseDueAt)}
                  </p>
                  <p>
                    <strong>Resolution SLA:</strong> {formatSlaStatus(ticket.resolutionSlaStatus)}
                  </p>
                  <p>
                    <strong>Resolution due:</strong> {formatDate(ticket.resolutionDueAt)}
                  </p>
                </div>

                <div style={{ marginTop: '0.75rem', display: 'flex', justifyContent: 'flex-end' }}>
                  <button
                    type="button"
                    className="horizon-btn horizon-btn--secondary horizon-btn--sm"
                    onClick={() => navigate(`/tickets/${ticket.ticketNumber}`)}
                  >
                    View ticket
                  </button>
                </div>
              </article>
            ))}
          </section>

          {/* Standard Pagination Component */}
          <Pagination
            page={ticketPage.page}
            totalPages={ticketPage.totalPages}
            totalElements={ticketPage.totalElements}
            isFirst={ticketPage.first}
            isLast={ticketPage.last}
            onPageChange={(p) => setPage(p)}
            label="Ticket pagination"
            entityName="tickets"
          />
        </>
      )}
    </div>
  )
}
