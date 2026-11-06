import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ArrowRight,
  CheckCircle2,
  Clock,
  Inbox,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  AlertCircle,
} from 'lucide-react'

import { getSlaDashboard } from '../api/sla'
import { useAuth } from '../auth/useAuth'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { PriorityBadge } from '../components/ui/PriorityBadge'
import { StatusBadge } from '../components/ui/StatusBadge'
import type {
  SlaDashboardResponse,
  SlaTicketPriority,
} from '../types/sla'
import type { TicketPriority, TicketStatus } from '../types/ticket'
import { formatDate, formatLabel, formatPercent } from '../utils/formatters'

const PRIORITIES: SlaTicketPriority[] = [
  'CRITICAL',
  'HIGH',
  'MEDIUM',
  'LOW',
]

export function SlaDashboardPage() {
  const { user, accessToken } = useAuth()
  const navigate = useNavigate()

  const isSupport = user?.roles.includes('SUPPORT_ENGINEER') ?? false

  const [dashboard, setDashboard] = useState<SlaDashboardResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    let cancelled = false

    async function loadDashboard() {
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
        const response = await getSlaDashboard(accessToken)
        if (!cancelled) {
          setDashboard(response)
        }
      } catch (caughtError) {
        if (cancelled) return
        setError(
          caughtError instanceof Error
            ? caughtError.message
            : 'Unable to load SLA operations.',
        )
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    void loadDashboard()

    return () => {
      cancelled = true
    }
  }, [accessToken, refreshKey])

  return (
    <div className="sla-dashboard-page horizon-page-container">
      {/* Page Header */}
      <PageHeader
        title="SLA Operations"
        subtitle="Track response and resolution deadlines across support tickets."
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

      {/* Global Alerts */}
      {error && (
        <div className="horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ marginBottom: '1.5rem' }}>
          <AlertCircle size={18} className="horizon-auth-alert-icon" />
          <span>{error}</span>
        </div>
      )}

      {/* Loading state */}
      {isLoading && (
        <div style={{ textAlign: 'center', padding: '4rem 1rem', color: 'var(--color-text-secondary)' }}>
          <RefreshCw size={24} className="horizon-spin" style={{ margin: '0 auto 12px', display: 'block', color: 'var(--color-primary)' }} />
          <p>Loading SLA operations data...</p>
        </div>
      )}

      {!isLoading && dashboard && (
        <>
          {/* Operational Metrics Grid */}
          <div className="horizon-kpi-grid">
            {/* Active Tickets */}
            <div className="horizon-kpi-card">
              <div className="horizon-kpi-header">
                <span className="horizon-kpi-title">Active Tickets</span>
                <span className="horizon-kpi-icon"><Inbox size={16} /></span>
              </div>
              <span className="horizon-kpi-value">{dashboard.activeTickets}</span>
              <p className="horizon-kpi-subtext">Tickets currently in an active operational state.</p>
            </div>

            {/* Response Breached */}
            <div className="horizon-kpi-card">
              <div className="horizon-kpi-header">
                <span className="horizon-kpi-title">Response Breached</span>
                <span
                  className="horizon-kpi-icon"
                  style={{
                    color: dashboard.responseBreached > 0 ? 'var(--color-danger)' : 'var(--color-success)',
                    background: dashboard.responseBreached > 0 ? 'var(--color-danger-subtle)' : 'var(--color-success-subtle)',
                  }}
                >
                  <Clock size={16} />
                </span>
              </div>
              <span
                className="horizon-kpi-value"
                style={{ color: dashboard.responseBreached > 0 ? 'var(--color-danger)' : 'inherit' }}
              >
                {dashboard.responseBreached}
              </span>
              <p className="horizon-kpi-subtext">Active tickets waiting for first response after the deadline.</p>
            </div>

            {/* Resolution Breached */}
            <div className="horizon-kpi-card">
              <div className="horizon-kpi-header">
                <span className="horizon-kpi-title">Resolution Breached</span>
                <span
                  className="horizon-kpi-icon"
                  style={{
                    color: dashboard.resolutionBreached > 0 ? 'var(--color-danger)' : 'var(--color-success)',
                    background: dashboard.resolutionBreached > 0 ? 'var(--color-danger-subtle)' : 'var(--color-success-subtle)',
                  }}
                >
                  <ShieldAlert size={16} />
                </span>
              </div>
              <span
                className="horizon-kpi-value"
                style={{ color: dashboard.resolutionBreached > 0 ? 'var(--color-danger)' : 'inherit' }}
              >
                {dashboard.resolutionBreached}
              </span>
              <p className="horizon-kpi-subtext">Active unresolved tickets past their resolution deadline.</p>
            </div>

            {/* Response Compliance */}
            <div className="horizon-kpi-card">
              <div className="horizon-kpi-header">
                <span className="horizon-kpi-title">Response Compliance</span>
                <span className="horizon-kpi-icon"><ShieldCheck size={16} /></span>
              </div>
              <span className="horizon-kpi-value">{formatPercent(dashboard.responseCompliancePercent)}</span>
              <div style={{ marginTop: '6px' }}>
                <div style={{ height: '4px', width: '100%', background: 'var(--color-surface-subtle)', borderRadius: '2px', overflow: 'hidden' }}>
                  <div
                    style={{
                      height: '100%',
                      width: `${Math.min(100, dashboard.responseCompliancePercent ?? 100)}%`,
                      background: (dashboard.responseCompliancePercent ?? 100) >= 90 ? 'var(--color-success)' : 'var(--color-warning)',
                    }}
                  />
                </div>
              </div>
              <p className="horizon-kpi-subtext">Known first responses completed within target SLA.</p>
            </div>

            {/* Resolution Compliance */}
            <div className="horizon-kpi-card">
              <div className="horizon-kpi-header">
                <span className="horizon-kpi-title">Resolution Compliance</span>
                <span className="horizon-kpi-icon"><CheckCircle2 size={16} /></span>
              </div>
              <span className="horizon-kpi-value">{formatPercent(dashboard.resolutionCompliancePercent)}</span>
              <div style={{ marginTop: '6px' }}>
                <div style={{ height: '4px', width: '100%', background: 'var(--color-surface-subtle)', borderRadius: '2px', overflow: 'hidden' }}>
                  <div
                    style={{
                      height: '100%',
                      width: `${Math.min(100, dashboard.resolutionCompliancePercent ?? 100)}%`,
                      background: (dashboard.resolutionCompliancePercent ?? 100) >= 90 ? 'var(--color-success)' : 'var(--color-warning)',
                    }}
                  />
                </div>
              </div>
              <p className="horizon-kpi-subtext">Known resolutions completed within target SLA.</p>
            </div>
          </div>

          {/* Overdue Tickets by Priority */}
          <Card
            title="Overdue Tickets by Priority"
            subtitle="Active tickets currently past their response or resolution target."
            density="standard"
            divider
            style={{ marginBottom: '1.5rem' }}
          >

            <div className="horizon-sla-exposure-grid">
              {PRIORITIES.map((priority) => {
                const count = dashboard.breachedByPriority[priority] ?? 0
                return (
                  <div key={priority} className="horizon-sla-exposure-card">
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <PriorityBadge priority={priority as TicketPriority} size="md" />
                      <span style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>
                        {formatLabel(priority)} Priority
                      </span>
                    </div>
                    <span
                      className="horizon-sla-exposure-count"
                      style={{
                        color: count > 0 ? (priority === 'CRITICAL' || priority === 'HIGH' ? 'var(--color-danger)' : 'var(--color-warning)') : 'inherit',
                      }}
                    >
                      {count}
                    </span>
                  </div>
                )
              })}
            </div>
          </Card>

          {/* Recent SLA Breaches Table */}
          <Card
            title="Recent SLA Breaches"
            subtitle="Overdue ticket events recorded across queues."
            density="standard"
            divider
            headerActions={
              <Badge variant="neutral" size="sm">
                {dashboard.recentBreaches.length} {dashboard.recentBreaches.length === 1 ? 'event' : 'events'}
              </Badge>
            }
          >

            {dashboard.recentBreaches.length === 0 ? (
              <div style={{ padding: '2.5rem 1rem', textAlign: 'center', color: 'var(--color-text-secondary)' }}>
                <ShieldCheck size={36} style={{ color: 'var(--color-success)', margin: '0 auto 8px', display: 'block' }} />
                <p style={{ margin: '0 0 4px', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)' }}>
                  All SLAs Currently In Compliance
                </p>
                <p style={{ margin: 0, fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
                  No recent SLA breach events have been recorded.
                </p>
              </div>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table className="horizon-data-table-simple">
                  <thead>
                    <tr>
                      <th>Ticket #</th>
                      <th>Breach Type</th>
                      <th>Priority</th>
                      <th>Ticket Status</th>
                      <th>Breached At</th>
                      <th style={{ textAlign: 'right' }}>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dashboard.recentBreaches.map((breach) => (
                      <tr key={`${breach.ticketNumber}-${breach.eventType}`}>
                        <td>
                          <button
                            type="button"
                            className="horizon-ticket-link"
                            style={{
                              border: 'none',
                              background: 'transparent',
                              cursor: 'pointer',
                              fontFamily: 'var(--font-family-mono)',
                              fontWeight: 'var(--font-weight-bold)',
                              color: 'var(--color-primary)',
                              padding: 0,
                              fontSize: 'var(--font-size-sm)',
                            }}
                            onClick={() => navigate(`/support/tickets/${breach.ticketNumber}`)}
                          >
                            {breach.ticketNumber}
                          </button>
                        </td>
                        <td>
                          <Badge variant="danger" size="sm" dot>
                            {breach.eventType === 'RESPONSE_BREACHED'
                              ? 'Response SLA Missed'
                              : 'Resolution SLA Missed'}
                          </Badge>
                        </td>
                        <td>
                          <PriorityBadge priority={breach.priority as TicketPriority} size="sm" />
                        </td>
                        <td>
                          <StatusBadge status={breach.status as TicketStatus} size="sm" />
                        </td>
                        <td style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', fontFamily: 'var(--font-family-mono)' }}>
                          {formatDate(breach.occurredAt)}
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => navigate(`/support/tickets/${breach.ticketNumber}`)}
                            leadingIcon={<ArrowRight size={14} />}
                            aria-label={`Inspect ticket ${breach.ticketNumber}`}
                          >
                            Inspect
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </>
      )}
    </div>
  )
}
