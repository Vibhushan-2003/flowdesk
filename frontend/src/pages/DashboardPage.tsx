import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Ticket,
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
  HelpCircle,
  AlertTriangle,
  ArrowRight,
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
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
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
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: '20px' }}>
          {/* Main Action Hub */}
          <Card
            padded
            title="IT Support Actions"
            subtitle="Create new inquiries, review active issues, or search knowledge base."
            headerActions={<Badge variant="primary" size="sm">Self-Service</Badge>}
          >
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
                Submit incident reports, hardware requests, software access grants, or network inquiries directly to IT Support.
              </p>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px' }}>
                <Button
                  variant="primary"
                  size="md"
                  onClick={() => navigate('/tickets/new')}
                  leadingIcon={<Plus size={16} />}
                >
                  Create New Ticket
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
            </div>
          </Card>

          {/* Self-Service & SLAs */}
          <Card
            padded
            title="Service Level Commitments"
            subtitle="Enterprise turnaround standards and verified self-help."
            headerActions={<Badge variant="success" size="sm">SLO Targets</Badge>}
          >
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px', padding: '10px 12px', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
                <HelpCircle size={18} style={{ color: 'var(--color-primary)', marginTop: '2px', flexShrink: 0 }} />
                <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
                  <strong>Need instant answers?</strong> Browse verified self-help articles in the Knowledge Base for password resets, VPN setups, and workstation troubleshooting before opening a ticket.
                </div>
              </div>

              <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', lineHeight: 1.6 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid var(--color-border)' }}>
                  <span>Critical / Urgent Priority:</span>
                  <strong style={{ color: 'var(--color-danger)' }}>15m Response / 4h Resolution</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid var(--color-border)' }}>
                  <span>High Priority:</span>
                  <strong style={{ color: 'var(--color-warning)' }}>30m Response / 8h Resolution</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid var(--color-border)' }}>
                  <span>Standard / Medium Priority:</span>
                  <strong style={{ color: 'var(--color-primary)' }}>1h Response / 24h Resolution</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0' }}>
                  <span>General / Low Priority:</span>
                  <strong style={{ color: 'var(--color-text-secondary)' }}>4h Response / 48h Resolution</strong>
                </div>
              </div>
            </div>
          </Card>
        </div>
      )}

      {/* 2. SUPPORT ENGINEER DASHBOARD WORKBENCH */}
      {isSupportEngineer && !canViewOperations && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '20px' }}>
          {/* Card A: My Assigned Work */}
          <Card
            padded
            title="Assigned Incidents"
            subtitle="Active tickets currently assigned to you for investigation."
            headerActions={<Badge variant="primary" size="sm">Active Queue</Badge>}
          >
            <p style={{ margin: '0 0 16px', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
              Respond to customer inquiries, add internal staff notes, or apply Gemini AI Copilot resolution drafts.
            </p>
            <Button
              variant="primary"
              size="md"
              onClick={() => navigate('/support/tickets')}
              leadingIcon={<UserCheck size={16} />}
            >
              Open Assigned Tickets ({metrics?.support?.assignedActiveTickets ?? 0})
            </Button>
          </Card>

          {/* Card B: Support Queue */}
          <Card
            padded
            title="Unassigned Support Queue"
            subtitle="Incoming unassigned tickets awaiting engineer claim."
            headerActions={<Badge variant="warning" size="sm">Intake</Badge>}
          >
            <p style={{ margin: '0 0 16px', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
              Review incoming triage submissions, inspect urgency and initial response deadlines, and claim ownership.
            </p>
            <Button
              variant="outline"
              size="md"
              onClick={() => navigate('/support/queue')}
              leadingIcon={<Inbox size={16} />}
            >
              View Support Queue ({metrics?.support?.unassignedQueueTickets ?? 0})
            </Button>
          </Card>

          {/* Card C: Knowledge Base */}
          <Card
            padded
            title="Knowledge Base & Deflection"
            subtitle="Browse, author, and maintain IT support resolution articles."
            headerActions={<Badge variant="neutral" size="sm">Documentation</Badge>}
          >
            <p style={{ margin: '0 0 16px', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
              Publish verified solutions to deflect recurring issues and ground Gemini AI Copilot suggestions.
            </p>
            <Button
              variant="outline"
              size="md"
              onClick={() => navigate('/kb')}
              leadingIcon={<BookOpen size={16} />}
            >
              Knowledge Base
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
                SLA Operations & Operational Command
              </h2>
              <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
                Real-time incident posture, SLA threshold compliance, and supervisory governance
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
                    Directory
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

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: '20px' }}>
            {/* Widget 1: SLA Queue Breaches & Policy Targets */}
            <Card
              padded
              title="SLA Queue Health & Policy Targets"
              subtitle="Active breach monitoring and database SLA response/resolution commitments."
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
                      Database policy defaults (V7)
                    </span>
                  </div>

                  <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', lineHeight: 1.6 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0', borderBottom: '1px solid var(--color-border)' }}>
                      <span>Critical / Urgent:</span>
                      <strong style={{ color: 'var(--color-danger)' }}>15m Response / 4h Resolution</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0', borderBottom: '1px solid var(--color-border)' }}>
                      <span>High Priority:</span>
                      <strong style={{ color: 'var(--color-warning)' }}>30m Response / 8h Resolution</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0', borderBottom: '1px solid var(--color-border)' }}>
                      <span>Standard / Medium:</span>
                      <strong style={{ color: 'var(--color-primary)' }}>1h Response / 24h Resolution</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0' }}>
                      <span>Low / Routine:</span>
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

            {/* Widget 2: Pending Approvals & Service Authorizations */}
            <Card
              padded
              title="Supervisory Approvals"
              subtitle="Pending manager reviews and service authorizations."
              headerActions={
                (metrics?.operations?.pendingApprovals ?? 0) > 0 ? (
                  <Badge variant="warning" size="sm" dot>Action Required</Badge>
                ) : (
                  <Badge variant="success" size="sm">Queue Cleared</Badge>
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
                    <strong>{metrics?.operations?.pendingApprovals ?? 0} Pending Requests</strong> awaiting supervisory sign-off for hardware & access grants.
                  </div>
                </div>

                <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
                  Review requests with elevated authorization requirements. Team lead approval is mandatory before hardware provisioning or elevated role assignments.
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

            {/* Widget 3: Platform Security Architecture */}
            <Card
              padded
              title="Platform Security Architecture"
              subtitle="Configured technical safeguards, audit controls, and onboarding policies."
              headerActions={<Badge variant="neutral" size="sm">Engineered Safeguards</Badge>}
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
                  onClick={() => navigate(isAdmin ? '/admin/attachments' : '/reports')}
                  leadingIcon={isAdmin ? <ShieldAlert size={16} /> : <BarChart3 size={16} />}
                  trailingIcon={<ArrowRight size={14} />}
                >
                  {isAdmin ? 'Attachment Security Console' : 'Compliance & Reports'}
                </Button>
              </div>
            </Card>
          </div>
        </div>
      )}
    </div>
  )
}
