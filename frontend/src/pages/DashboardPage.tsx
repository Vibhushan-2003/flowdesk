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

  const activeSlaBreachesCount =
    metrics?.operations?.activeSlaBreaches ?? metrics?.operations?.slaBreaches ?? 0

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
            <div
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                padding: '4px 10px',
                background: 'var(--color-surface)',
                border: '1px solid var(--color-border)',
                borderRadius: 'var(--radius-full)',
                fontSize: 'var(--font-size-xs)',
                color: 'var(--color-text-secondary)',
              }}
            >
              <span
                style={{
                  width: '7px',
                  height: '7px',
                  borderRadius: '50%',
                  backgroundColor: 'var(--color-success)',
                  display: 'inline-block',
                }}
              />
              <span>Live Operations</span>
            </div>
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
                value={activeSlaBreachesCount}
                alert={activeSlaBreachesCount > 0}
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
                  <span>Urgent / Critical Priority:</span>
                  <strong style={{ color: 'var(--color-danger)' }}>1h Response / 8h Resolution</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid var(--color-border)' }}>
                  <span>High Priority:</span>
                  <strong style={{ color: 'var(--color-warning)' }}>2h Response / 12h Resolution</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid var(--color-border)' }}>
                  <span>Standard / Medium Priority:</span>
                  <strong style={{ color: 'var(--color-primary)' }}>4h Response / 24h Resolution</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0' }}>
                  <span>General / Low Priority:</span>
                  <strong style={{ color: 'var(--color-text-secondary)' }}>8h Response / 48h Resolution</strong>
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

      {/* 3. TEAM LEAD & ADMIN DASHBOARD WORKBENCH */}
      {canViewOperations && (
        <div>
          {/* Section Heading specifically for SLA Operations (matches e2e tests) */}
          <div style={{ marginBottom: '16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <h2
              className="horizon-workspace-card__title"
              style={{
                fontSize: 'var(--font-size-lg)',
                fontWeight: 600,
                color: 'var(--color-text-primary)',
                margin: 0,
              }}
            >
              SLA Operations & Oversight
            </h2>
            <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
              Operational Governance & Incident Management
            </span>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '20px' }}>
            {/* Approvals */}
            <Card
              padded
              title="Approval Inbox"
              subtitle="Pending manager reviews and service authorizations."
              headerActions={
                (metrics?.operations?.pendingApprovals ?? 0) > 0 ? (
                  <Badge variant="warning" size="sm" dot>Action Required</Badge>
                ) : (
                  <Badge variant="success" size="sm">Up to Date</Badge>
                )
              }
            >
              <p style={{ margin: '0 0 16px', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
                Review high-impact hardware and access authorization requests requiring supervisory sign-off.
              </p>
              <Button
                variant="primary"
                size="md"
                onClick={() => navigate('/approvals')}
                leadingIcon={<CheckSquare size={16} />}
                trailingIcon={<ArrowRight size={14} />}
              >
                Approval Inbox ({metrics?.operations?.pendingApprovals ?? 0})
              </Button>
            </Card>

            {/* SLA Monitoring */}
            <Card
              padded
              title="SLA Monitoring"
              subtitle="Real-time response & resolution deadline health."
              headerActions={
                activeSlaBreachesCount > 0 ? (
                  <Badge variant="danger" size="sm" dot>Breach Detected</Badge>
                ) : (
                  <Badge variant="success" size="sm">Target Met</Badge>
                )
              }
            >
              <p style={{ margin: '0 0 16px', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
                Track upcoming deadlines, evaluate compliance ratios, and review breached milestones.
              </p>
              <Button
                variant="outline"
                size="md"
                onClick={() => navigate('/sla/dashboard')}
                leadingIcon={<Clock size={16} />}
                trailingIcon={<ArrowRight size={14} />}
              >
                SLA Dashboard
              </Button>
            </Card>

            {/* Reports */}
            <Card
              padded
              title="Reports & Analytics"
              subtitle="Operational KPIs, resolution metrics, and workload balance."
              headerActions={<Badge variant="neutral" size="sm">Intelligence</Badge>}
            >
              <p style={{ margin: '0 0 16px', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
                Analyze ticket volumes, median resolution durations, team workload, and export audit CSVs.
              </p>
              <Button
                variant="outline"
                size="md"
                onClick={() => navigate('/reports')}
                leadingIcon={<BarChart3 size={16} />}
                trailingIcon={<ArrowRight size={14} />}
              >
                Operational Reports
              </Button>
            </Card>

            {/* Admin only: User Management */}
            {isAdmin && (
              <Card
                padded
                title="User Management"
                subtitle="Manage accounts, invitations, and role assignments."
                headerActions={<Badge variant="primary" size="sm">RBAC Access</Badge>}
              >
                <p style={{ margin: '0 0 16px', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
                  Invite team members, assign RBAC permissions, and oversee account activation statuses.
                </p>
                <Button
                  variant="outline"
                  size="md"
                  onClick={() => navigate('/admin/users')}
                  leadingIcon={<Users size={16} />}
                  trailingIcon={<ArrowRight size={14} />}
                >
                  Manage Users
                </Button>
              </Card>
            )}

            {/* Admin only: Audit Trail */}
            {isAdmin && (
              <Card
                padded
                title="Audit Trail"
                subtitle="Immutable compliance log of security & operational events."
                headerActions={<Badge variant="neutral" size="sm">Audit Log</Badge>}
              >
                <p style={{ margin: '0 0 16px', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
                  Inspect structured event metadata, role changes, priority updates, and system activities.
                </p>
                <Button
                  variant="outline"
                  size="md"
                  onClick={() => navigate('/audit')}
                  leadingIcon={<ShieldCheck size={16} />}
                  trailingIcon={<ArrowRight size={14} />}
                >
                  Audit Trail
                </Button>
              </Card>
            )}

            {/* Admin only: Attachment Security */}
            {isAdmin && (
              <Card
                padded
                title="Attachment Security"
                subtitle="Malware scanner oversight and administrative rescans."
                headerActions={<Badge variant="neutral" size="sm">Antivirus</Badge>}
              >
                <p style={{ margin: '0 0 16px', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', lineHeight: 1.5 }}>
                  Monitor quarantine statuses, inspect file hashes, and execute authorized background scans.
                </p>
                <Button
                  variant="outline"
                  size="md"
                  onClick={() => navigate('/admin/attachments')}
                  leadingIcon={<ShieldAlert size={16} />}
                  trailingIcon={<ArrowRight size={14} />}
                >
                  Attachment Security
                </Button>
              </Card>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
