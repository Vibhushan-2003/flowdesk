import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Inbox,
  RefreshCw,
  UserCheck,
  Clock,
  Search,
  AlertCircle,
  CheckCircle2,
  Filter,
} from 'lucide-react'

import { claimTicket, getSupportQueue } from '../api/ticket'
import { useAuth } from '../auth/useAuth'
import type { PageResponse, TicketSummary } from '../types/ticket'
import { formatDate, formatLabel, formatSlaStatus } from '../utils/formatters'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { StatusBadge } from '../components/ui/StatusBadge'
import { PriorityBadge } from '../components/ui/PriorityBadge'
import { DataTable, type Column } from '../components/ui/DataTable'
import { Pagination } from '../components/ui/Pagination'
import { EmptyState } from '../components/ui/EmptyState'

const PAGE_SIZE = 10

export function SupportQueuePage() {
  const { accessToken } = useAuth()
  const navigate = useNavigate()

  const [page, setPage] = useState(0)
  const [queue, setQueue] = useState<PageResponse<TicketSummary> | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [successNotice, setSuccessNotice] = useState<string | null>(null)
  const [claimingTicketNumber, setClaimingTicketNumber] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  // Local filter states for active queue page
  const [searchFilter, setSearchFilter] = useState('')
  const [priorityFilter, setPriorityFilter] = useState<string>('ALL')
  const [sortOrder, setSortOrder] = useState<'oldest' | 'newest'>('oldest')

  useEffect(() => {
    let cancelled = false

    async function loadQueue() {
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
        const response = await getSupportQueue(accessToken, page, PAGE_SIZE)
        if (!cancelled) {
          setQueue(response)
        }
      } catch (caughtError) {
        if (cancelled) return
        setError(
          caughtError instanceof Error
            ? caughtError.message
            : 'Unable to load the support queue. Please try again.',
        )
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
  }, [accessToken, page, refreshKey])

  async function handleClaim(ticketNumber: string) {
    if (!accessToken) {
      setError('Your session is no longer valid')
      return
    }

    setClaimingTicketNumber(ticketNumber)
    setError(null)
    setSuccessNotice(null)

    try {
      const response = await claimTicket(accessToken, ticketNumber)
      setSuccessNotice(`Ticket ${response.ticketNumber} successfully claimed! Redirecting...`)
      navigate(`/support/tickets/${response.ticketNumber}`)
    } catch (caughtError) {
      setError(
        caughtError instanceof Error
          ? caughtError.message
          : 'Unable to claim ticket. Another engineer may have already claimed it.',
      )
      setRefreshKey((k) => k + 1)
    } finally {
      setClaimingTicketNumber(null)
    }
  }

  // Filtered and sorted tickets on current page
  let displayedTickets = queue?.content ? [...queue.content] : []
  if (searchFilter.trim()) {
    const q = searchFilter.toLowerCase().trim()
    displayedTickets = displayedTickets.filter(
      (t) =>
        t.ticketNumber.toLowerCase().includes(q) ||
        t.title.toLowerCase().includes(q),
    )
  }

  if (priorityFilter !== 'ALL') {
    displayedTickets = displayedTickets.filter((t) => t.priority === priorityFilter)
  }

  if (sortOrder === 'newest') {
    displayedTickets.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
  } else {
    displayedTickets.sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
  }

  // Table column configuration
  const columns: Column<TicketSummary>[] = [
    {
      key: 'ticketNumber',
      header: 'Ticket #',
      render: (ticket: TicketSummary) => (
        <span
          style={{
            fontFamily: 'var(--font-family-mono)',
            fontWeight: 'var(--font-weight-bold)',
            color: 'var(--color-primary)',
          }}
        >
          {ticket.ticketNumber}
        </span>
      ),
      width: '120px',
    },
    {
      key: 'title',
      header: 'Subject',
      render: (ticket: TicketSummary) => (
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
            Created {formatDate(ticket.createdAt)}
          </span>
        </div>
      ),
    },
    {
      key: 'type',
      header: 'Type',
      render: (ticket: TicketSummary) => (
        <Badge variant="neutral" size="sm">
          {formatLabel(ticket.type)}
        </Badge>
      ),
      width: '130px',
    },
    {
      key: 'priority',
      header: 'Priority',
      render: (ticket: TicketSummary) => <PriorityBadge priority={ticket.priority} size="sm" />,
      width: '110px',
    },
    {
      key: 'status',
      header: 'Status',
      render: (ticket: TicketSummary) => <StatusBadge status={ticket.status} size="sm" />,
      width: '110px',
    },
    {
      key: 'sla',
      header: 'Response SLA',
      render: (ticket: TicketSummary) => {
        if (!ticket.responseSlaStatus) return <span style={{ color: 'var(--color-text-muted)' }}>&mdash;</span>
        return (
          <Badge variant={ticket.responseSlaStatus === 'BREACHED' ? 'danger' : 'success'} size="sm" dot>
            {formatSlaStatus(ticket.responseSlaStatus)}
          </Badge>
        )
      },
      width: '130px',
    },
    {
      key: 'actions',
      header: 'Action',
      align: 'right',
      render: (ticket: TicketSummary) => (
        <Button
          variant="primary"
          size="sm"
          isLoading={claimingTicketNumber === ticket.ticketNumber}
          disabled={claimingTicketNumber !== null}
          onClick={(e) => {
            e.stopPropagation()
            void handleClaim(ticket.ticketNumber)
          }}
        >
          Claim Ticket
        </Button>
      ),
      width: '130px',
    },
  ]

  return (
    <div className="support-queue-page horizon-page-container">
      {/* Page Header */}
      <PageHeader
        title="Support Queue"
        subtitle="Review and claim unassigned open tickets. Queue is strictly ordered oldest first (FIFO)."
        actions={
          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => navigate('/support/tickets')}
              leadingIcon={<UserCheck size={14} />}
            >
              My Assigned Tickets
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setRefreshKey((k) => k + 1)}
              leadingIcon={<RefreshCw size={14} className={isLoading ? 'horizon-spin' : ''} />}
              disabled={isLoading}
            >
              Refresh
            </Button>
          </div>
        }
      />

      {/* Queue Stat Overview Cards */}
      <div className="horizon-queue-stats">
        <div className="horizon-queue-stat-card">
          <div className="horizon-queue-stat-icon horizon-queue-stat-icon--primary">
            <Inbox size={22} />
          </div>
          <div className="horizon-queue-stat-info">
            <span className="horizon-queue-stat-label">Open in Queue</span>
            <span className="horizon-queue-stat-value">{queue?.totalElements ?? 0}</span>
          </div>
        </div>

        <div className="horizon-queue-stat-card">
          <div className="horizon-queue-stat-icon horizon-queue-stat-icon--warning">
            <Clock size={22} />
          </div>
          <div className="horizon-queue-stat-info">
            <span className="horizon-queue-stat-label">Dispatch Strategy</span>
            <span className="horizon-queue-stat-value" style={{ fontSize: 'var(--font-size-base)', marginTop: '4px' }}>
              FIFO (Oldest First)
            </span>
          </div>
        </div>

        <div className="horizon-queue-stat-card">
          <div className="horizon-queue-stat-icon horizon-queue-stat-icon--success">
            <UserCheck size={22} />
          </div>
          <div className="horizon-queue-stat-info">
            <span className="horizon-queue-stat-label">Triage Readiness</span>
            <span className="horizon-queue-stat-value" style={{ fontSize: 'var(--font-size-base)', marginTop: '4px' }}>
              {queue?.totalElements === 0 ? 'Queue Clear' : 'Action Required'}
            </span>
          </div>
        </div>
      </div>

      {/* Notifications and Alerts */}
      {error && (
        <div
          className="horizon-auth-alert horizon-auth-alert--error"
          role="alert"
          style={{ marginBottom: '1.5rem' }}
        >
          <AlertCircle size={18} className="horizon-auth-alert-icon" />
          <span>{error}</span>
        </div>
      )}

      {successNotice && (
        <div
          className="horizon-auth-alert horizon-auth-alert--success"
          role="status"
          style={{ marginBottom: '1.5rem' }}
        >
          <CheckCircle2 size={18} className="horizon-auth-alert-icon" />
          <span>{successNotice}</span>
        </div>
      )}

      {/* Filter & Search Bar */}
      <Card padded style={{ marginBottom: '1.5rem' }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '1rem',
            flexWrap: 'wrap',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flex: 1, minWidth: '240px' }}>
            <div className="horizon-input-wrapper" style={{ flex: 1 }}>
              <span className="horizon-input-icon horizon-input-icon--leading">
                <Search size={16} />
              </span>
              <input
                type="text"
                className="horizon-input has-leading-icon"
                placeholder="Filter current queue by ticket # or title..."
                value={searchFilter}
                onChange={(e) => setSearchFilter(e.target.value)}
              />
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
            {/* Priority Filter */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              <Filter size={14} style={{ color: 'var(--color-text-muted)' }} />
              <div className="horizon-select-wrapper">
                <select
                  className="horizon-select"
                  style={{ padding: '6px 28px 6px 10px', fontSize: 'var(--font-size-xs)' }}
                  value={priorityFilter}
                  onChange={(e) => setPriorityFilter(e.target.value)}
                >
                  <option value="ALL">All Priorities</option>
                  <option value="CRITICAL">Critical</option>
                  <option value="HIGH">High</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="LOW">Low</option>
                </select>
              </div>
            </div>

            {/* Sort Toggle */}
            <div className="horizon-select-wrapper">
              <select
                className="horizon-select"
                style={{ padding: '6px 28px 6px 10px', fontSize: 'var(--font-size-xs)' }}
                value={sortOrder}
                onChange={(e) => setSortOrder(e.target.value as 'oldest' | 'newest')}
              >
                <option value="oldest">Sort: Oldest first (FIFO)</option>
                <option value="newest">Sort: Newest first</option>
              </select>
            </div>
          </div>
        </div>
      </Card>

      {/* Desktop Table View (>=768px) */}
      <div className="desktop-ticket-table-view">
        <DataTable<TicketSummary>
          columns={columns}
          data={displayedTickets}
          keyExtractor={(t) => t.ticketNumber}
          isLoading={isLoading}
          emptyMessage={
            searchFilter || priorityFilter !== 'ALL'
              ? 'No unassigned tickets match your filter criteria.'
              : 'There are no unassigned open tickets awaiting claim in the support queue.'
          }
          emptyAction={
            searchFilter || priorityFilter !== 'ALL' ? (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => {
                  setSearchFilter('')
                  setPriorityFilter('ALL')
                }}
              >
                Reset filters
              </Button>
            ) : (
              <Button variant="secondary" size="sm" onClick={() => setRefreshKey((k) => k + 1)}>
                Check for new tickets
              </Button>
            )
          }
        />
      </div>

      {/* Mobile Card View (<768px) */}
      <div className="mobile-ticket-card-view" style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        {isLoading ? (
          <div style={{ textAlign: 'center', padding: '3rem 1rem', color: 'var(--color-text-secondary)' }}>
            <p>Loading support queue...</p>
          </div>
        ) : displayedTickets.length === 0 ? (
          <EmptyState
            icon={<Inbox size={32} />}
            title="Queue is clear!"
            description="There are no unassigned tickets awaiting triage."
          />
        ) : (
          displayedTickets.map((ticket) => (
            <Card key={ticket.ticketNumber} padded className="horizon-ticket-mobile-card">
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

              <p style={{ margin: '0 0 12px', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
                Created {formatDate(ticket.createdAt)}
              </p>

              <div style={{ display: 'flex', justifyContent: 'flex-end', paddingTop: '10px', borderTop: '1px solid var(--color-divider)' }}>
                <Button
                  variant="primary"
                  size="sm"
                  isLoading={claimingTicketNumber === ticket.ticketNumber}
                  disabled={claimingTicketNumber !== null}
                  onClick={() => void handleClaim(ticket.ticketNumber)}
                >
                  Claim Ticket
                </Button>
              </div>
            </Card>
          ))
        )}
      </div>

      {/* Pagination Controls */}
      {queue && queue.totalPages > 1 && (
        <Pagination
          page={queue.page}
          totalPages={queue.totalPages}
          totalElements={queue.totalElements}
          onPageChange={(p) => setPage(p)}
        />
      )}
    </div>
  )
}
