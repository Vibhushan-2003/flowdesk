import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Ticket,
  Search,
  Inbox,
  Clock,
  CheckCircle2,
  AlertCircle,
  UserCheck,
  CheckSquare,
  BarChart3,
  Users,
  ShieldCheck,
  ShieldAlert,
  Plus,
  Loader2,
  RefreshCw,
  BookOpen,
  AlertTriangle,
  ArrowRight,
  Archive,
} from 'lucide-react'

import { getDashboardStats } from '../api/dashboard'
import { useAuth } from '../auth/useAuth'
import { PageHeader } from '../components/ui/PageHeader'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { KPIStatCard } from '../components/ui/KPIStatCard'
import { Card } from '../components/ui/Card'
import type { DashboardMetricsResponse } from '../types/dashboard'

export function DashboardPage() {
  const {
    user,
    accessToken,
  } = useAuth()

  const navigate = useNavigate()

  const [metrics, setMetrics] = useState<DashboardMetricsResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  const roles = user?.roles || []
  const isSupportEngineer = roles.includes('SUPPORT_ENGINEER')
  const isTeamLead = roles.includes('TEAM_LEAD')
  const isAdmin = roles.includes('ADMIN')
  const canViewOperations = isTeamLead || isAdmin

  // Determine highest priority role for dashboard identity
  let roleTitle = 'Employee Self-Service'
  let roleSubtitle = 'Report incidents, request IT services, and track real-time resolution status.'
  let roleBadgeLabel = 'Employee'

  if (isAdmin) {
    roleTitle = 'Platform Administration'
    roleSubtitle = 'Oversee system security, user directories, audit trails, and service reliability.'
    roleBadgeLabel = 'Administrator'
  } else if (isTeamLead) {
    roleTitle = 'Supervisory Operations'
    roleSubtitle = 'Oversee team workload, review pending ticket approvals, and track SLA health.'
    roleBadgeLabel = 'Team Lead'
  } else if (isSupportEngineer) {
    roleTitle = 'Support Operations Hub'
    roleSubtitle = 'Manage assigned tickets, claim unassigned queue items, and resolve technical issues.'
    roleBadgeLabel = 'Support Engineer'
  }

  useEffect(() => {
    let cancelled = false

    async function loadMetrics() {
      if (!accessToken) {
        if (!cancelled) {
          setError('Your session is no longer valid')
          setIsLoading(false)
        }
        return
      }

      setError(null)

      try {
        const response = await getDashboardStats(accessToken)
        if (!cancelled) {
          setMetrics(response)
        }
      } catch (caughtError) {
        if (cancelled) return
        setError(
          caughtError instanceof Error
            ? caughtError.message
            : 'Unable to load operational statistics.',
        )
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    void loadMetrics()

    return () => {
      cancelled = true
    }
  }, [accessToken, refreshKey])

  function handleRetry() {
    setIsLoading(true)
    setRefreshKey((k) => k + 1)
  }

  // Safe fallback normalization for metrics
  const employeeData = metrics?.employee ?? {
    openTickets: (metrics as unknown as { openTickets?: number })?.openTickets ?? 0,
    ticketsAwaitingResponse: (metrics as unknown as { ticketsAwaitingResponse?: number; inProgressTickets?: number })?.ticketsAwaitingResponse ?? (metrics as unknown as { inProgressTickets?: number })?.inProgressTickets ?? 0,
    resolvedTickets: (metrics as unknown as { resolvedTickets?: number })?.resolvedTickets ?? 0,
    pendingApprovals: (metrics as unknown as { pendingApprovals?: number | null })?.pendingApprovals ?? null,
  }

  // Safe normalization for operations metrics and breach evaluation
  const hasOperationsData = Boolean(metrics?.operations)
  const rawBreaches = metrics?.operations?.activeSlaBreaches ?? metrics?.operations?.slaBreaches
  const isBreachesKnown = hasOperationsData && typeof rawBreaches === 'number'
  const activeSlaBreachesCount = isBreachesKnown ? rawBreaches : null

  return (
    <div className="dashboard-page horizon-dashboard">
      {/* Top Page Header */}
      <PageHeader
        title="Dashboard"
        subtitle={`${roleTitle} — ${roleSubtitle}`}
        badge={
          <Badge variant="primary" size="md">
            {roleBadgeLabel}
          </Badge>
        }
        actions={
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
            <Button
              variant="outline"
              size="sm"
              onClick={handleRetry}
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
              New Ticket
            </Button>
          </div>
        }
      />

      {/* Loading Shimmer / Banner */}
      {isLoading && (
        <div className="dashboard-metrics-loading horizon-dashboard-loading" role="status" aria-live="polite">
          <Loader2 size={18} className="horizon-btn__spinner" />
          <span>Loading live operational statistics...</span>
        </div>
      )}

      {/* Error Alert with Retry CTA */}
      {error && (
        <div className="dashboard-metrics-error horizon-dashboard-error" role="alert">
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <AlertCircle size={18} />
            <span>{error}</span>
          </div>
          <button
            type="button"
            className="horizon-btn horizon-btn--secondary horizon-btn--sm"
            onClick={handleRetry}
          >
            Retry
          </button>
        </div>
      )}

      {/* =========================================================
          ROLE-SPECIFIC KPI METRICS STRIP
          ========================================================= */}
      {metrics && (
        <div
          className="dashboard-kpi-grid"
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 220px), 1fr))',
            gap: '16px',
            marginBottom: '28px',
          }}
        >
          {/* Employee KPI Metrics */}
          {!isSupportEngineer && !canViewOperations && (
            <>
              <KPIStatCard
                label="Open Tickets"
                value={employeeData.openTickets}
                icon={<Inbox size={18} />}
                onClick={() => navigate('/tickets')}
              />
              <KPIStatCard
                label="Awaiting Your Reply"
                value={employeeData.ticketsAwaitingResponse}
                alert={employeeData.ticketsAwaitingResponse > 0}
                icon={<Clock size={18} />}
                onClick={() => navigate('/tickets?status=WAITING_FOR_USER')}
              />
              <KPIStatCard
                label="Resolved Tickets"
                value={employeeData.resolvedTickets}
                icon={<CheckCircle2 size={18} />}
                onClick={() => navigate('/tickets?status=RESOLVED')}
              />
              <KPIStatCard
                label="Pending Approvals"
                value={employeeData.pendingApprovals ?? 0}
                icon={<CheckSquare size={18} />}
              />
            </>
          )}

          {/* Support Engineer KPI Metrics */}
          {isSupportEngineer && !canViewOperations && (
            <>
              <KPIStatCard
                label="My Assigned Work"
                value={metrics.support?.assignedActiveTickets ?? 0}
                icon={<UserCheck size={18} />}
                onClick={() => navigate('/support/tickets')}
              />
              <KPIStatCard
                label="Unassigned Queue"
                value={metrics.support?.unassignedQueueTickets ?? 0}
                icon={<Inbox size={18} />}
                onClick={() => navigate('/support/queue')}
              />
              <KPIStatCard
                label="Assigned SLA Breaches"
                value={metrics.support?.assignedSlaBreaches ?? 0}
                alert={(metrics.support?.assignedSlaBreaches ?? 0) > 0}
                icon={<AlertTriangle size={18} />}
                onClick={() => navigate('/support/tickets')}
              />
              <KPIStatCard
                label="My Personal Tickets"
                value={employeeData.openTickets}
                icon={<Ticket size={18} />}
                onClick={() => navigate('/tickets')}
              />
            </>
          )}

          {/* Supervisory / Operations / Admin KPI Metrics */}
          {canViewOperations && (
            <>
              <KPIStatCard
                label="Total Active Tickets"
                value={metrics.operations?.totalActiveTickets ?? 0}
                icon={<Ticket size={18} />}
                onClick={() => navigate('/tickets')}
              />
              <KPIStatCard
                label="Unassigned Queue"
                value={metrics.operations?.unassignedQueueTickets ?? 0}
                icon={<Inbox size={18} />}
                onClick={() => navigate('/support/tickets')}
              />
              <KPIStatCard
                label="Pending Approvals"
                value={metrics.operations?.pendingApprovals ?? 0}
                alert={(metrics.operations?.pendingApprovals ?? 0) > 0}
                icon={<CheckSquare size={18} />}
                onClick={() => navigate('/approvals')}
              />
              <KPIStatCard
                label="Active SLA Breaches"
                value={isBreachesKnown ? activeSlaBreachesCount! : (isLoading ? '...' : '—')}
                alert={Boolean(isBreachesKnown && activeSlaBreachesCount! > 0)}
                icon={<AlertTriangle size={18} />}
                onClick={() => navigate('/sla/dashboard')}
              />
            </>
          )}
        </div>
      )}

      {/* =========================================================
          ROLE-SPECIFIC WORKSPACE WORKBENCHES
          ========================================================= */}

      {/* 1. EMPLOYEE DASHBOARD WORKBENCH */}
      {!isSupportEngineer && !canViewOperations && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Action Required Banner if waiting for response */}
          {employeeData.ticketsAwaitingResponse > 0 && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                flexWrap: 'wrap',
                gap: '12px',
                padding: '14px 18px',
                background: 'var(--color-warning-subtle)',
                border: '1px solid var(--color-warning-border)',
                borderRadius: 'var(--radius-lg)',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <Clock size={20} style={{ color: 'var(--color-warning)', flexShrink: 0 }} />
                <div>
                  <strong style={{ color: 'var(--color-warning)', fontSize: 'var(--font-size-sm)' }}>
                    Action Required: {employeeData.ticketsAwaitingResponse} ticket{employeeData.ticketsAwaitingResponse > 1 ? 's' : ''} awaiting your reply
                  </strong>
                  <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', marginTop: '2px' }}>
                    IT support engineers have requested further details or confirmation on your open inquiries.
                  </div>
                </div>
              </div>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => navigate('/tickets?status=WAITING_FOR_USER')}
                trailingIcon={<ArrowRight size={14} />}
              >
                Review Tickets
              </Button>
            </div>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 340px), 1fr))', gap: '20px' }}>
            {/* Self-Service & Quick Help Hub */}
            <Card
              padded
              title="IT Self-Service Hub"
              subtitle="Quickly submit inquiries, look up guides, or search verified solutions."
              headerActions={<Badge variant="primary" size="sm">Self-Service</Badge>}
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                {/* Search Bar launcher */}
                <form
                  onSubmit={(e) => {
                    e.preventDefault()
                    const form = e.currentTarget
                    const q = new FormData(form).get('kbSearch') as string
                    navigate(`/kb${q ? `?q=${encodeURIComponent(q)}` : ''}`)
                  }}
                  style={{ display: 'flex', gap: '8px' }}
                >
                  <div className="horizon-input-wrapper" style={{ flex: 1 }}>
                    <Search size={16} className="horizon-input-icon horizon-input-icon--leading" />
                    <input
                      name="kbSearch"
                      type="search"
                      className="horizon-input has-leading-icon"
                      placeholder="Search Knowledge Base (VPN, WiFi, Email)..."
                    />
                  </div>
                  <Button type="submit" variant="secondary" size="md">
                    Search
                  </Button>
                </form>

                {/* Direct Action Buttons */}
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 130px), 1fr))', gap: '10px' }}>
                  <Button
                    variant="primary"
                    size="md"
                    onClick={() => navigate('/tickets/new')}
                    leadingIcon={<Plus size={16} />}
                  >
                    Create Ticket
                  </Button>
                  <Button
                    variant="outline"
                    size="md"
                    onClick={() => navigate('/tickets')}
                    leadingIcon={<Ticket size={16} />}
                  >
                    My Tickets
                  </Button>
                  <Button
                    variant="outline"
                    size="md"
                    onClick={() => navigate('/kb')}
                    leadingIcon={<BookOpen size={16} />}
                  >
                    Knowledge Base
                  </Button>
                </div>

                {/* Helpful quick guide tips */}
                <div style={{ padding: '10px 14px', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
                  <strong>Need immediate resolution?</strong> Most connectivity and password issues are resolved in under 2 minutes using self-help troubleshooting guides.
                </div>
              </div>
            </Card>

            {/* Service Level Commitments & Security */}
            <Card
              padded
              title="Service Level Commitments"
              subtitle="Guaranteed IT response targets and data protection standards."
              headerActions={<Badge variant="success" size="sm">SLA Targets</Badge>}
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', lineHeight: 1.6 }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--color-border)' }}>
                    <span>Critical Priority (Outages):</span>
                    <Badge variant="danger" size="sm">15m Resp / 4h Res</Badge>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--color-border)' }}>
                    <span>High Priority:</span>
                    <Badge variant="warning" size="sm">30m Resp / 8h Res</Badge>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--color-border)' }}>
                    <span>Standard / Medium Priority:</span>
                    <Badge variant="primary" size="sm">1h Resp / 24h Res</Badge>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--color-border)' }}>
                    <span>General / Low Priority:</span>
                    <Badge variant="neutral" size="sm">4h Resp / 48h Res</Badge>
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px', padding: '10px 12px', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
                  <ShieldCheck size={18} style={{ color: 'var(--color-success)', marginTop: '2px', flexShrink: 0 }} />
                  <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
                    <strong>Malware & Attachment Protection:</strong> All uploaded screenshots and logs are verified in real time through ClamAV stream protocol before reaching staff.
                  </div>
                </div>
              </div>
            </Card>
          </div>
        </div>
      )}

      {/* 2. SUPPORT ENGINEER DASHBOARD WORKBENCH */}
      {isSupportEngineer && !canViewOperations && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 320px), 1fr))', gap: '20px' }}>
          {/* Card A: My Assigned Work */}
          <Card
            padded
            title="My Assigned Work"
            subtitle="Tickets currently assigned to your queue."
            headerActions={<Badge variant="primary" size="sm">{metrics?.support?.assignedActiveTickets ?? 0} Active</Badge>}
          >
            <p style={{ margin: '0 0 16px', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
              Investigate tickets, update customer communications, or draft resolution summaries with AI Copilot.
            </p>
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
              <Button
                variant="primary"
                size="md"
                onClick={() => navigate('/support/tickets')}
                leadingIcon={<UserCheck size={16} />}
              >
                Assigned Tickets ({metrics?.support?.assignedActiveTickets ?? 0})
              </Button>
              <Button
                variant="outline"
                size="md"
                onClick={() => navigate('/support/tickets?view=history')}
                leadingIcon={<Archive size={16} />}
              >
                Resolved History
              </Button>
            </div>
          </Card>

          {/* Card B: Support Queue */}
          <Card
            padded
            title="Support Queue"
            subtitle="Unassigned tickets waiting for an engineer."
            headerActions={
              (metrics?.support?.unassignedQueueTickets ?? 0) > 0 ? (
                <Badge variant="warning" size="sm" dot>Intake Active</Badge>
              ) : (
                <Badge variant="success" size="sm">Queue Clear</Badge>
              )
            }
          >
            <p style={{ margin: '0 0 16px', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
              Review incoming submissions, check SLA deadlines, and claim tickets for investigation.
            </p>
            <Button
              variant="outline"
              size="md"
              onClick={() => navigate('/support/queue')}
              leadingIcon={<Inbox size={16} />}
            >
              Browse Support Queue ({metrics?.support?.unassignedQueueTickets ?? 0})
            </Button>
          </Card>

          {/* Card C: Help Articles & AI Assistance */}
          <Card
            padded
            title="Help Articles & AI Tools"
            subtitle="Resolution guides and AI copilot resources."
          >
            <p style={{ margin: '0 0 16px', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
              Search verified guides, author new troubleshooting articles, or draft solutions inside tickets.
            </p>
            <Button
              variant="outline"
              size="md"
              onClick={() => navigate('/kb')}
              leadingIcon={<BookOpen size={16} />}
            >
              Search Help Articles
            </Button>
          </Card>
        </div>
      )}

      {/* 3. TEAM LEAD & ADMIN DASHBOARD WORKBENCH — OPERATIONAL COMMAND CENTER */}
      {canViewOperations && (
        <div>
          {/* Section Heading specifically for SLA Operations (matches e2e tests) + Quick Actions */}
          <div style={{ marginBottom: '20px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
            <div>
              <h2
                className="horizon-workspace-card__title"
                style={{
                  fontSize: 'var(--font-size-lg)',
                  fontWeight: 600,
                  color: 'var(--color-text-primary)',
                  margin: 0,
                }}
              >
                SLA Operations & Operations Overview
              </h2>
              <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
                {isAdmin
                  ? 'Real-time incident posture, SLA threshold compliance, and security controls'
                  : 'Team workload, supervisory reviews, and SLA performance tracking'}
              </span>
            </div>

            {/* Quick Console Navigation Toolbar */}
            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
              <Button
                variant="outline"
                size="sm"
                onClick={() => navigate('/sla/dashboard')}
                leadingIcon={<Clock size={14} />}
              >
                SLA Monitor
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => navigate('/reports')}
                leadingIcon={<BarChart3 size={14} />}
              >
                Reports
              </Button>
              {isAdmin && (
                <>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => navigate('/admin/users')}
                    leadingIcon={<Users size={14} />}
                  >
                    User Directory
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => navigate('/audit')}
                    leadingIcon={<ShieldCheck size={14} />}
                  >
                    Audit Ledger
                  </Button>
                </>
              )}
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 340px), 1fr))', gap: '20px' }}>
            {/* Widget 1: SLA Queue Breaches & Policy Targets */}
            <Card
              padded
              title="SLA Queue Health & Policy Targets"
              subtitle="Active breach monitoring and response/resolution commitments."
              headerActions={
                isLoading ? (
                  <Badge variant="neutral" size="sm">Evaluating...</Badge>
                ) : !isBreachesKnown ? (
                  <Badge variant="neutral" size="sm">Status Unavailable</Badge>
                ) : activeSlaBreachesCount! > 0 ? (
                  <Badge variant="danger" size="sm" dot>Active Breaches ({activeSlaBreachesCount})</Badge>
                ) : (
                  <Badge variant="success" size="sm">0 Active Breaches</Badge>
                )
              }
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <div
                  style={{
                    padding: '10px 12px',
                    borderRadius: 'var(--radius-md)',
                    background: isBreachesKnown && activeSlaBreachesCount! > 0
                      ? 'var(--color-danger-subtle)'
                      : isBreachesKnown
                      ? 'var(--color-success-subtle)'
                      : 'var(--color-surface-subtle)',
                    border: `1px solid ${
                      isBreachesKnown && activeSlaBreachesCount! > 0
                        ? 'var(--color-danger-border)'
                        : isBreachesKnown
                        ? 'var(--color-success-border)'
                        : 'var(--color-border)'
                    }`,
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px',
                  }}
                >
                  {isBreachesKnown && activeSlaBreachesCount! > 0 ? (
                    <AlertTriangle size={18} style={{ color: 'var(--color-danger-text)', flexShrink: 0 }} />
                  ) : isBreachesKnown ? (
                    <CheckCircle2 size={18} style={{ color: 'var(--color-success-text)', flexShrink: 0 }} />
                  ) : (
                    <Clock size={18} style={{ color: 'var(--color-text-muted)', flexShrink: 0 }} />
                  )}
                  <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-primary)', lineHeight: 1.4 }}>
                    {isBreachesKnown && activeSlaBreachesCount! > 0 ? (
                      <>
                        <strong>{activeSlaBreachesCount} Active SLA Breaches</strong> in open queue tickets. Immediate investigation required.
                      </>
                    ) : isBreachesKnown ? (
                      <>
                        <strong>0 Active SLA Breaches</strong> in production queues. All active tickets currently within policy response and resolution windows.
                      </>
                    ) : isLoading ? (
                      <span>Evaluating queue SLA status...</span>
                    ) : (
                      <span>Queue SLA status is currently unavailable.</span>
                    )}
                  </div>
                </div>

                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                    <span style={{ fontSize: 'var(--font-size-xs)', fontWeight: 600, color: 'var(--color-text-primary)' }}>
                      Baseline SLA Policy Targets
                    </span>
                    <span style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>
                      Default policy targets
                    </span>
                  </div>

                  <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', lineHeight: 1.6 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0', borderBottom: '1px solid var(--color-border)' }}>
                      <span>Critical Priority:</span>
                      <strong style={{ color: 'var(--color-danger)' }}>15m Response / 4h Resolution</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0', borderBottom: '1px solid var(--color-border)' }}>
                      <span>High Priority:</span>
                      <strong style={{ color: 'var(--color-warning)' }}>30m Response / 8h Resolution</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0', borderBottom: '1px solid var(--color-border)' }}>
                      <span>Medium Priority:</span>
                      <strong style={{ color: 'var(--color-primary)' }}>1h Response / 24h Resolution</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0' }}>
                      <span>Low Priority:</span>
                      <strong style={{ color: 'var(--color-text-secondary)' }}>4h Response / 48h Resolution</strong>
                    </div>
                  </div>
                </div>

                <Button
                  variant="primary"
                  size="md"
                  onClick={() => navigate('/sla/dashboard')}
                  leadingIcon={<Clock size={16} />}
                  trailingIcon={<ArrowRight size={14} />}
                >
                  Inspect SLA Dashboard
                </Button>
              </div>
            </Card>

            {/* Widget 2: Pending Approvals */}
            <Card
              padded
              title="Pending Approvals"
              subtitle="Service authorizations and supervisory reviews."
              headerActions={
                (metrics?.operations?.pendingApprovals ?? 0) > 0 ? (
                  <Badge variant="warning" size="sm" dot>Action Required</Badge>
                ) : (
                  <Badge variant="success" size="sm">Queue Clear</Badge>
                )
              }
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <div
                  style={{
                    padding: '10px 12px',
                    borderRadius: 'var(--radius-md)',
                    background: (metrics?.operations?.pendingApprovals ?? 0) > 0 ? 'var(--color-warning-subtle)' : 'var(--color-surface-subtle)',
                    border: `1px solid ${(metrics?.operations?.pendingApprovals ?? 0) > 0 ? 'var(--color-warning-border)' : 'var(--color-border)'}`,
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px',
                  }}
                >
                  <CheckSquare size={18} style={{ color: 'var(--color-warning-text)', flexShrink: 0 }} />
                  <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-primary)', lineHeight: 1.4 }}>
                    <strong>{metrics?.operations?.pendingApprovals ?? 0} Pending Requests</strong> awaiting supervisory review for hardware & access grants.
                  </div>
                </div>

                <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
                  Review requests with elevated requirements. Team lead or admin approval is mandatory before hardware provisioning or elevated role assignments.
                </p>

                <Button
                  variant={(metrics?.operations?.pendingApprovals ?? 0) > 0 ? 'primary' : 'outline'}
                  size="md"
                  onClick={() => navigate('/approvals')}
                  leadingIcon={<CheckSquare size={16} />}
                  trailingIcon={<ArrowRight size={14} />}
                >
                  Approval Inbox ({metrics?.operations?.pendingApprovals ?? 0})
                </Button>
              </div>
            </Card>

            {/* Widget 3: Platform Security Architecture (Admin) or Team Reports (Team Lead) */}
            {isAdmin ? (
              <Card
                padded
                title="Platform Security Architecture"
                subtitle="Configured technical safeguards, audit controls, and onboarding policies."
                headerActions={<Badge variant="neutral" size="sm">Security Features</Badge>}
              >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 12px', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-primary)' }}>
                      <ShieldAlert size={16} style={{ color: 'var(--color-primary)' }} />
                      <span>Attachment Malware Scanning</span>
                    </div>
                    <Badge variant="neutral" size="sm">ClamAV Stream Protocol</Badge>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 12px', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-primary)' }}>
                      <ShieldCheck size={16} style={{ color: 'var(--color-primary)' }} />
                      <span>Security Audit Trail</span>
                    </div>
                    <Badge variant="neutral" size="sm">PostgreSQL Prevent-Mutation Trigger</Badge>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 12px', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-primary)' }}>
                      <Users size={16} style={{ color: 'var(--color-primary)' }} />
                      <span>User Provisioning Policy</span>
                    </div>
                    <Badge variant="neutral" size="sm">Invitation-Only Flow</Badge>
                  </div>

                  <Button
                    variant="outline"
                    size="md"
                    onClick={() => navigate('/admin/attachments')}
                    leadingIcon={<ShieldAlert size={16} />}
                    trailingIcon={<ArrowRight size={14} />}
                  >
                    Attachment Security Console
                  </Button>
                </div>
              </Card>
            ) : (
              <Card
                padded
                title="Operational Reports"
                subtitle="Team resolution analytics and workload distribution."
                headerActions={<Badge variant="neutral" size="sm">Analytics</Badge>}
              >
                <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                  <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
                    Inspect ticket resolution times, engineer workloads, and SLA compliance across all support categories.
                  </p>
                  <Button
                    variant="outline"
                    size="md"
                    onClick={() => navigate('/reports')}
                    leadingIcon={<BarChart3 size={16} />}
                    trailingIcon={<ArrowRight size={14} />}
                  >
                    Compliance & Reports
                  </Button>
                </div>
              </Card>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
