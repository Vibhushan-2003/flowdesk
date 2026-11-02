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
  Plus,
  Loader2,
  RefreshCw,
} from 'lucide-react'

import { getDashboardStats } from '../api/dashboard'
import { useAuth } from '../auth/useAuth'
import { ChangePasswordCard } from '../components/ChangePasswordCard'
import { PageHeader } from '../components/ui/PageHeader'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { KPIStatCard } from '../components/ui/KPIStatCard'
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
  const canViewOperations = roles.includes('TEAM_LEAD') || roles.includes('ADMIN')
  const isAdmin = roles.includes('ADMIN')
  const primaryRole = roles[0] || 'EMPLOYEE'

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

  return (
    <div className="dashboard-page horizon-dashboard">
      {/* Top Page Header */}
      <PageHeader
        title="Dashboard"
        subtitle="Manage your support requests, track SLAs, and oversee operational workspaces."
        badge={
          <Badge variant="primary" size="md">
            {primaryRole.replace('_', ' ')}
          </Badge>
        }
        actions={
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
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

      {/* Workspace Grid */}
      <section className="dashboard-workspace-grid horizon-dashboard-grid">
        {/* Card 1: Employee Workspace */}
        <article className="dashboard-workspace-card horizon-workspace-card">
          <div>
            <div className="horizon-workspace-card__header">
              <p className="dashboard-card-label horizon-workspace-card__label">
                Employee workspace
              </p>
              <h2 className="horizon-workspace-card__title">
                <Ticket size={20} style={{ color: 'var(--color-primary)' }} />
                <span>IT support</span>
              </h2>
              <p className="horizon-workspace-card__description">
                Report an incident, request a service, or track tickets you have already created.
              </p>
            </div>

            {metrics?.employee && (
              <div
                className="dashboard-kpi-grid horizon-workspace-card__kpis"
                aria-label="Your IT support ticket statistics"
              >
                <KPIStatCard
                  label="Open Tickets"
                  value={metrics.employee.openTickets}
                  icon={<Inbox size={16} />}
                />
                <KPIStatCard
                  label="Awaiting Your Reply"
                  value={metrics.employee.ticketsAwaitingResponse}
                  alert={metrics.employee.ticketsAwaitingResponse > 0}
                  icon={<Clock size={16} />}
                />
                <KPIStatCard
                  label="Resolved Tickets"
                  value={metrics.employee.resolvedTickets}
                  icon={<CheckCircle2 size={16} />}
                />
              </div>
            )}
          </div>

          <div className="dashboard-action-buttons horizon-workspace-card__actions">
            <button
              type="button"
              className="horizon-btn horizon-btn--primary horizon-btn--md"
              onClick={() => navigate('/tickets/new')}
            >
              Create ticket
            </button>
            <button
              type="button"
              className="dashboard-secondary-button horizon-btn horizon-btn--secondary horizon-btn--md"
              onClick={() => navigate('/tickets')}
            >
              My tickets
            </button>
            <button
              type="button"
              className="dashboard-secondary-button horizon-btn horizon-btn--secondary horizon-btn--md"
              onClick={() => navigate('/kb')}
            >
              Knowledge Base
            </button>
          </div>
        </article>

        {/* Card 2: Support Engineer Workspace */}
        {isSupportEngineer && (
          <article className="dashboard-workspace-card dashboard-support-card horizon-workspace-card">
            <div>
              <div className="horizon-workspace-card__header">
                <p className="dashboard-card-label horizon-workspace-card__label">
                  Support workspace
                </p>
                <h2 className="horizon-workspace-card__title">
                  <UserCheck size={20} style={{ color: 'var(--color-primary)' }} />
                  <span>Engineer operations</span>
                </h2>
                <p className="horizon-workspace-card__description">
                  Claim incoming tickets and manage the active issues currently assigned to you.
                </p>
              </div>

              {metrics?.support && (
                <div
                  className="dashboard-kpi-grid horizon-workspace-card__kpis"
                  aria-label="Support engineer queue statistics"
                >
                  <KPIStatCard
                    label="My Assigned"
                    value={metrics.support.assignedActiveTickets}
                    icon={<UserCheck size={16} />}
                  />
                  <KPIStatCard
                    label="Unassigned Queue"
                    value={metrics.support.unassignedQueueTickets}
                    icon={<Inbox size={16} />}
                  />
                  <KPIStatCard
                    label="SLA Breaches"
                    value={metrics.support.assignedSlaBreaches}
                    alert={metrics.support.assignedSlaBreaches > 0}
                    icon={<AlertCircle size={16} />}
                  />
                </div>
              )}
            </div>

            <div className="dashboard-action-buttons horizon-workspace-card__actions">
              <button
                type="button"
                className="horizon-btn horizon-btn--primary horizon-btn--md"
                onClick={() => navigate('/support/tickets')}
              >
                My assigned tickets
              </button>
              <button
                type="button"
                className="dashboard-secondary-button horizon-btn horizon-btn--secondary horizon-btn--md"
                onClick={() => navigate('/support/queue')}
              >
                Support queue
              </button>
              <button
                type="button"
                className="dashboard-secondary-button horizon-btn horizon-btn--secondary horizon-btn--md"
                onClick={() => navigate('/kb')}
              >
                Knowledge Base
              </button>
            </div>
          </article>
        )}

        {/* Card 3: Approval Workspace */}
        {canViewOperations && (
          <article className="dashboard-workspace-card horizon-workspace-card">
            <div>
              <div className="horizon-workspace-card__header">
                <p className="dashboard-card-label horizon-workspace-card__label">
                  Approval workspace
                </p>
                <h2 className="horizon-workspace-card__title">
                  <CheckSquare size={20} style={{ color: 'var(--color-warning)' }} />
                  <span>Service request approvals</span>
                </h2>
                <p className="horizon-workspace-card__description">
                  Review pending service requests and approve or reject them before they enter the support workflow.
                </p>
              </div>

              {metrics?.operations && (
                <div
                  className="dashboard-kpi-grid horizon-workspace-card__kpis"
                  aria-label="Service request approvals count"
                >
                  <KPIStatCard
                    label="Pending Approvals"
                    value={metrics.operations.pendingApprovals ?? 0}
                    alert={(metrics.operations.pendingApprovals ?? 0) > 0}
                    icon={<CheckSquare size={16} />}
                  />
                </div>
              )}
            </div>

            <div className="dashboard-action-buttons horizon-workspace-card__actions">
              <button
                type="button"
                className="horizon-btn horizon-btn--primary horizon-btn--md"
                onClick={() => navigate('/approvals')}
              >
                Open approval inbox
              </button>
            </div>
          </article>
        )}

        {/* Card 4: Operations SLA Workspace */}
        {canViewOperations && (
          <article className="dashboard-workspace-card horizon-workspace-card">
            <div>
              <div className="horizon-workspace-card__header">
                <p className="dashboard-card-label horizon-workspace-card__label">
                  Operations workspace
                </p>
                <h2 className="horizon-workspace-card__title">
                  <BarChart3 size={20} style={{ color: 'var(--color-primary)' }} />
                  <span>SLA operations</span>
                </h2>
                <p className="horizon-workspace-card__description">
                  Review active SLA exposure, compliance, priority breakdowns, and recent breach history.
                </p>
              </div>

              {metrics?.operations && (
                <div
                  className="dashboard-kpi-grid horizon-workspace-card__kpis"
                  aria-label="Operational ticket statistics"
                >
                  <KPIStatCard
                    label="Total Active"
                    value={metrics.operations.totalActiveTickets}
                    icon={<Ticket size={16} />}
                  />
                  <KPIStatCard
                    label="Unassigned Queue"
                    value={metrics.operations.unassignedQueueTickets}
                    icon={<Inbox size={16} />}
                  />
                  <KPIStatCard
                    label="SLA Breaches"
                    value={metrics.operations.slaBreaches ?? metrics.operations.activeSlaBreaches ?? 0}
                    alert={(metrics.operations.slaBreaches ?? metrics.operations.activeSlaBreaches ?? 0) > 0}
                    icon={<AlertCircle size={16} />}
                  />
                </div>
              )}
            </div>

            <div className="dashboard-action-buttons horizon-workspace-card__actions">
              <button
                type="button"
                className="horizon-btn horizon-btn--primary horizon-btn--md"
                onClick={() => navigate('/sla/dashboard')}
              >
                Open SLA dashboard
              </button>
              <button
                type="button"
                className="dashboard-secondary-button horizon-btn horizon-btn--secondary horizon-btn--md"
                onClick={() => navigate('/reports')}
              >
                Operational reports
              </button>
            </div>
          </article>
        )}

        {/* Card 5: User Management */}
        {isAdmin && (
          <article className="dashboard-workspace-card horizon-workspace-card">
            <div>
              <div className="horizon-workspace-card__header">
                <p className="dashboard-card-label horizon-workspace-card__label">
                  Administration workspace
                </p>
                <h2 className="horizon-workspace-card__title">
                  <Users size={20} style={{ color: 'var(--color-primary)' }} />
                  <span>User management</span>
                </h2>
                <p className="horizon-workspace-card__description">
                  Manage user accounts, assign roles, create users, and activate or deactivate system access.
                </p>
              </div>
            </div>

            <div className="dashboard-action-buttons horizon-workspace-card__actions">
              <button
                type="button"
                className="horizon-btn horizon-btn--primary horizon-btn--md"
                onClick={() => navigate('/admin/users')}
              >
                Manage users
              </button>
            </div>
          </article>
        )}

        {/* Card 6: Audit Trail */}
        {isAdmin && (
          <article className="dashboard-workspace-card horizon-workspace-card">
            <div>
              <div className="horizon-workspace-card__header">
                <p className="dashboard-card-label horizon-workspace-card__label">
                  Governance workspace
                </p>
                <h2 className="horizon-workspace-card__title">
                  <ShieldCheck size={20} style={{ color: 'var(--color-primary)' }} />
                  <span>Audit trail</span>
                </h2>
                <p className="horizon-workspace-card__description">
                  Review immutable records of important user actions, system events, ticket changes, approvals, and SLA breaches.
                </p>
              </div>
            </div>

            <div className="dashboard-action-buttons horizon-workspace-card__actions">
              <button
                type="button"
                className="horizon-btn horizon-btn--primary horizon-btn--md"
                onClick={() => navigate('/audit')}
              >
                Open audit trail
              </button>
            </div>
          </article>
        )}
      </section>

      {/* Account Profile Card */}
      <section className="user-card horizon-account-card">
        <div className="account-card-heading horizon-account-header">
          <div>
            <p className="dashboard-card-label horizon-workspace-card__label">
              Account
            </p>
            <h2 style={{ margin: '4px 0 0', fontSize: 'var(--font-size-lg)', fontWeight: 'var(--font-weight-semibold)' }}>
              Your profile
            </h2>
          </div>
          <Badge variant="success" size="sm">
            Active
          </Badge>
        </div>

        <div className="account-details-grid horizon-account-grid">
          <div className="horizon-account-item">
            <span>Email</span>
            <strong>{user?.email}</strong>
          </div>
          <div className="horizon-account-item">
            <span>User ID</span>
            <strong>{user?.userId}</strong>
          </div>
          <div className="horizon-account-item">
            <span>Roles</span>
            <strong>{user?.roles?.join(', ')}</strong>
          </div>
        </div>
      </section>

      {/* Change Password Card */}
      <ChangePasswordCard />
    </div>
  )
}
