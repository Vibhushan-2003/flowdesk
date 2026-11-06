import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import {
  Inbox,
  Search,
  AlertCircle,
  ArrowRight,
  UserCheck,
  Archive,
} from 'lucide-react'

import { useTicketListUrlFilters } from '../hooks/useTicketListUrlFilters'
import { getMyAssignedTickets, getSupportTicketHistory } from '../api/ticket'
import { useAuth } from '../auth/useAuth'
import { TicketListOverview } from '../components/TicketListOverview'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { StatusBadge } from '../components/ui/StatusBadge'
import { PriorityBadge } from '../components/ui/PriorityBadge'
import { DataTable, type Column } from '../components/ui/DataTable'
import { Pagination } from '../components/ui/Pagination'
import { EmptyState } from '../components/ui/EmptyState'
import { formatDate, formatLabel, formatSlaStatus } from '../utils/formatters'

import type {
  PageResponse,
  SupportTicketSummary,
  TicketPriority,
  TicketStatus,
} from '../types/ticket'

const PAGE_SIZE = 10

const ACTIVE_STATUSES: TicketStatus[] = [
  'ASSIGNED',
  'IN_PROGRESS',
  'WAITING_FOR_USER',
  'RESOLVED',
  'CLOSED',
]

const HISTORY_STATUSES: TicketStatus[] = ['RESOLVED', 'CLOSED']

const PRIORITIES: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

export function MyAssignedTicketsPage() {
  const { user, accessToken } = useAuth()
  const navigate = useNavigate()

  const isSupport = user?.roles.includes('SUPPORT_ENGINEER') ?? false
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
  const isHistoryMode = viewParam === 'history'

  const [ticketPage, setTicketPage] = useState<PageResponse<SupportTicketSummary> | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  function setViewMode(mode: 'active' | 'history') {
    const nextParams = new URLSearchParams()
    if (mode === 'history') {
      nextParams.set('view', 'history')
    }

    // Keep applied status only if valid in target mode
    const validStatuses = mode === 'history' ? HISTORY_STATUSES : ACTIVE_STATUSES
    if (appliedFilters.status && validStatuses.includes(appliedFilters.status as TicketStatus)) {
      nextParams.set('status', appliedFilters.status)
    }
    if (appliedFilters.priority) {
      nextParams.set('priority', appliedFilters.priority)
    }
    if (appliedFilters.search) {
      nextParams.set('search', appliedFilters.search)
    }
    if (appliedFilters.sort === 'oldest') {
      nextParams.set('sort', 'oldest')
    }

    setSearchParams(nextParams)
  }

  useEffect(() => {
    let cancelled = false

    async function loadTickets() {
      if (!accessToken) {
        if (!cancelled) {
          setError('Your session is no longer valid')
          setIsLoading(false)
        }
        return
      }

      setIsLoading(true)
      setError(null)

      try {
        const response = isHistoryMode
          ? await getSupportTicketHistory(accessToken, page, PAGE_SIZE, appliedFilters)
          : await getMyAssignedTickets(accessToken, page, PAGE_SIZE, appliedFilters)

        if (!cancelled) {
          setTicketPage(response)
        }
      } catch (caughtError) {
        if (cancelled) return
        setError(
          caughtError instanceof Error
            ? caughtError.message
            : 'Unable to load tickets. Please try again.',
        )
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
  }, [accessToken, page, appliedFilters, isHistoryMode])

  // Table columns
  const columns: Column<SupportTicketSummary>[] = [
    {
      key: 'ticketNumber',
      header: 'Ticket #',
      render: (ticket: SupportTicketSummary) => (
        <button
          type="button"
          className="ticket-number-link"
          style={{
            background: 'none',
            border: 'none',
            padding: 0,
            cursor: 'pointer',
            fontFamily: 'var(--font-family-mono)',
            fontWeight: 'var(--font-weight-bold)',
            color: 'var(--color-primary)',
          }}
          onClick={(e) => {
            e.stopPropagation()
            navigate(`/support/tickets/${ticket.ticketNumber}`)
          }}
        >
          {ticket.ticketNumber}
        </button>
      ),
      width: '120px',
    },
    {
      key: 'title',
      header: 'Subject',
      render: (ticket: SupportTicketSummary) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
          <span
            style={{
              fontWeight: 'var(--font-weight-medium)',
              color: 'var(--color-text-primary)',
              lineHeight: 1.3,
            }}
          >
            {ticket.title}
          </span>
          <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
            Assigned {formatDate(ticket.assignedAt || ticket.createdAt)}
          </span>
        </div>
      ),
    },
    {
      key: 'type',
      header: 'Type',
      render: (ticket: SupportTicketSummary) => (
        <Badge variant="neutral" size="sm">
          {formatLabel(ticket.type)}
        </Badge>
      ),
      width: '130px',
    },
    {
      key: 'status',
      header: 'Status',
      render: (ticket: SupportTicketSummary) => <StatusBadge status={ticket.status} size="sm" />,
      width: '120px',
    },
    {
      key: 'priority',
      header: 'Priority',
      render: (ticket: SupportTicketSummary) => <PriorityBadge priority={ticket.priority} size="sm" />,
      width: '110px',
    },
    {
      key: 'slaStatus',
      header: 'Resolution SLA',
      render: (ticket: SupportTicketSummary) => {
        if (!ticket.resolutionSlaStatus) return <span style={{ color: 'var(--color-text-muted)', fontSize: 'var(--font-size-xs)' }}>&mdash;</span>
        const isBreached = ticket.resolutionSlaStatus === 'BREACHED'
        return (
          <Badge variant={isBreached ? 'danger' : 'success'} size="sm" dot>
            {formatSlaStatus(ticket.resolutionSlaStatus)}
          </Badge>
        )
      },
      width: '130px',
    },
    {
      key: 'assignedAt',
      header: 'Assigned Date',
      render: (ticket: SupportTicketSummary) => (
        <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', whiteSpace: 'nowrap' }}>
          {formatDate(ticket.assignedAt || ticket.createdAt)}
        </span>
      ),
      width: '160px',
    },
    {
      key: 'actions',
      header: 'Open',
      align: 'right',
      render: (ticket: SupportTicketSummary) => (
        <Button
          variant="ghost"
          size="sm"
          onClick={(e) => {
            e.stopPropagation()
            navigate(`/support/tickets/${ticket.ticketNumber}`)
          }}
          leadingIcon={<ArrowRight size={14} />}
          aria-label={`Open ticket ${ticket.ticketNumber}`}
        />
      ),
      width: '70px',
    },
  ]

  const statusOptions = isHistoryMode ? HISTORY_STATUSES : ACTIVE_STATUSES

  return (
    <div className="assigned-tickets-page horizon-page-container">
      {/* Header */}
      <PageHeader
        title={isHistoryMode ? 'Resolved Ticket History' : 'My Assigned Tickets'}
        subtitle={
          isHistoryMode
            ? 'Review historical tickets you have resolved or closed.'
            : 'Track, update, and resolve tickets currently assigned to you.'
        }
        actions={
          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
            {isSupport && (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => navigate('/support/queue')}
                leadingIcon={<Inbox size={14} />}
              >
                Support Queue
              </Button>
            )}
          </div>
        }
      />

      {/* View Switcher Tabs (Active vs History) */}
      <div className="horizon-view-tabs" role="tablist" aria-label="Workload views">
        <button
          type="button"
          role="tab"
          aria-selected={!isHistoryMode}
          className={`horizon-view-tab ${!isHistoryMode ? 'is-active' : ''}`}
          onClick={() => setViewMode('active')}
        >
          <UserCheck size={16} />
          <span>Active Workload</span>
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={isHistoryMode}
          className={`horizon-view-tab ${isHistoryMode ? 'is-active' : ''}`}
          onClick={() => setViewMode('history')}
        >
          <Archive size={16} />
          <span>Resolved History</span>
        </button>
      </div>

      {/* Error alert */}
      {error && (
        <div
          className="horizon-auth-alert horizon-auth-alert--error"
          role="alert"
          style={{ marginBottom: '1.25rem' }}
        >
          <AlertCircle size={18} className="horizon-auth-alert-icon" />
          <span>{error}</span>
        </div>
      )}

      {/* Unified Search & Filter Workbench */}
      <div style={{ marginBottom: '1.25rem' }}>
        <form
          className="ticket-filters-form"
          onSubmit={applyFilters}
        >
          <div className="horizon-filter-fields">
            {/* Search Input */}
            <div className="horizon-input-wrapper" style={{ flex: '2 1 240px', minWidth: '200px' }}>
              <span className="horizon-input-icon horizon-input-icon--leading">
                <Search size={16} />
              </span>
              <input
                type="text"
                className="horizon-input has-leading-icon"
                placeholder={isHistoryMode ? 'Search resolved tickets...' : 'Search assigned tickets...'}
                value={searchText}
                onChange={(e) => setSearchText(e.target.value)}
              />
            </div>

            {/* Status Select */}
            <div className="horizon-select-wrapper" style={{ flex: '1 1 150px', minWidth: '140px' }}>
              <select
                className="horizon-select"
                value={selectedStatus}
                onChange={(e) => setSelectedStatus(e.target.value as TicketStatus | '')}
                aria-label="Filter by status"
              >
                <option value="">All Statuses</option>
                {statusOptions.map((s) => (
                  <option key={s} value={s}>
                    {formatLabel(s)}
                  </option>
                ))}
              </select>
            </div>

            {/* Priority Select */}
            <div className="horizon-select-wrapper" style={{ flex: '1 1 140px', minWidth: '130px' }}>
              <select
                className="horizon-select"
                value={selectedPriority}
                onChange={(e) => setSelectedPriority(e.target.value as TicketPriority | '')}
                aria-label="Filter by priority"
              >
                <option value="">All Priorities</option>
                {PRIORITIES.map((p) => (
                  <option key={p} value={p}>
                    {formatLabel(p)}
                  </option>
                ))}
              </select>
            </div>

            {/* Sort Select */}
            <div className="horizon-select-wrapper" style={{ flex: '1 1 140px', minWidth: '130px' }}>
              <select
                className="horizon-select"
                value={selectedSort}
                onChange={(e) => setSelectedSort(e.target.value as 'newest' | 'oldest')}
                aria-label="Sort tickets"
              >
                <option value="newest">Newest first</option>
                <option value="oldest">Oldest first</option>
              </select>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="horizon-filter-actions">
            <Button variant="primary" size="sm" type="submit">
              Apply
            </Button>
            <Button variant="secondary" size="sm" type="button" onClick={resetFilters}>
              Reset
            </Button>
          </div>
        </form>

        {/* Overview Stat Strip attached below filter bar */}
        {ticketPage && (
          <TicketListOverview
            filters={appliedFilters}
            totalElements={ticketPage.totalElements}
            visibleCount={ticketPage.content.length}
            isLoading={isLoading}
            onRefresh={() => setPage(page)}
          />
        )}
      </div>

      {/* Desktop Table View (>=768px) */}
      <div className="desktop-ticket-table-view">
        <DataTable<SupportTicketSummary>
          columns={columns}
          data={ticketPage?.content || []}
          keyExtractor={(ticket) => ticket.ticketNumber}
          isLoading={isLoading}
          onRowClick={(ticket) => navigate(`/support/tickets/${ticket.ticketNumber}`)}
          emptyMessage={
            isHistoryMode
              ? 'No resolved or closed tickets match your current filters.'
              : 'You have no active tickets assigned to you.'
          }
          emptyAction={
            isHistoryMode ? (
              <Button variant="secondary" size="sm" onClick={() => setViewMode('active')} leadingIcon={<UserCheck size={14} />}>
                View Active Workload
              </Button>
            ) : isSupport ? (
              <Button variant="primary" size="sm" onClick={() => navigate('/support/queue')} leadingIcon={<Inbox size={14} />}>
                Browse Support Queue
              </Button>
            ) : undefined
          }
        />
      </div>

      {/* Mobile Card View (<768px) */}
      <div className="mobile-ticket-card-view" style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        {isLoading ? (
          <div style={{ textAlign: 'center', padding: '3rem 1rem', color: 'var(--color-text-secondary)' }}>
            <p>Loading assigned tickets...</p>
          </div>
        ) : !ticketPage || ticketPage.content.length === 0 ? (
          <EmptyState
            icon={isHistoryMode ? <Archive size={32} /> : <UserCheck size={32} />}
            title={isHistoryMode ? 'No resolved tickets' : 'No active tickets'}
            description={
              isHistoryMode
                ? 'Tickets you resolve or close will be recorded here.'
                : 'Visit the Support Queue to claim a ticket.'
            }
            action={
              isHistoryMode ? (
                <Button variant="secondary" size="sm" onClick={() => setViewMode('active')} leadingIcon={<UserCheck size={14} />}>
                  View Active Workload
                </Button>
              ) : isSupport ? (
                <Button variant="primary" size="sm" onClick={() => navigate('/support/queue')} leadingIcon={<Inbox size={14} />}>
                  Browse Support Queue
                </Button>
              ) : undefined
            }
          />
        ) : (
          ticketPage.content.map((ticket) => (
            <Card
              key={ticket.ticketNumber}
              padded
              className="horizon-ticket-mobile-card"
              onClick={() => navigate(`/support/tickets/${ticket.ticketNumber}`)}
              style={{ cursor: 'pointer' }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
                <span
                  style={{
                    fontFamily: 'var(--font-family-mono)',
                    fontWeight: 'var(--font-weight-bold)',
                    color: 'var(--color-primary)',
                  }}
                >
                  {ticket.ticketNumber}
                </span>
                <div style={{ display: 'flex', gap: '4px' }}>
                  <PriorityBadge priority={ticket.priority} size="sm" />
                  <StatusBadge status={ticket.status} size="sm" />
                </div>
              </div>

              <h4 style={{ margin: '0 0 6px', fontSize: 'var(--font-size-base)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)' }}>
                {ticket.title}
              </h4>

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
                <span>Assigned {formatDate(ticket.assignedAt || ticket.createdAt)}</span>
                {ticket.resolutionSlaStatus && (
                  <Badge variant={ticket.resolutionSlaStatus === 'BREACHED' ? 'danger' : 'success'} size="sm" dot>
                    {formatSlaStatus(ticket.resolutionSlaStatus)}
                  </Badge>
                )}
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', marginTop: '10px', paddingTop: '8px', borderTop: '1px solid var(--color-divider)', fontSize: 'var(--font-size-xs)' }}>
                <span style={{ color: 'var(--color-primary)', fontWeight: 'var(--font-weight-medium)', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                  View Ticket <ArrowRight size={12} />
                </span>
              </div>
            </Card>
          ))
        )}
      </div>

      {/* Pagination Controls */}
      {ticketPage && ticketPage.totalPages > 1 && (
        <Pagination
          page={ticketPage.page}
          totalPages={ticketPage.totalPages}
          totalElements={ticketPage.totalElements}
          onPageChange={(p) => setPage(p)}
        />
      )}
    </div>
  )
}
