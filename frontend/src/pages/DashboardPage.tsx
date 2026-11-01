import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { getDashboardStats } from '../api/dashboard'
import { useAuth } from '../auth/useAuth'
import { ChangePasswordCard } from '../components/ChangePasswordCard'
import { NotificationBell } from '../components/NotificationBell'
import type { DashboardMetricsResponse } from '../types/dashboard'

export function DashboardPage() {
  const {
    user,
    accessToken,
    signOut,
  } = useAuth()

  const navigate = useNavigate()

  const [metrics, setMetrics] = useState<DashboardMetricsResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  const isSupportEngineer =
    user?.roles.includes(
      'SUPPORT_ENGINEER',
    ) ?? false

  const canViewOperations =
    user?.roles.some(
      (role) =>
        role === 'TEAM_LEAD' ||
        role === 'ADMIN',
    ) ?? false

  const isAdmin =
    user?.roles.includes(
      'ADMIN',
    ) ?? false

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

  function handleSignOut() {
    signOut()

    navigate('/login')
  }

  return (
    <main className="dashboard-page">
      <header className="dashboard-header">
        <div>
          <p className="eyebrow">
            FlowDesk
          </p>

          <h1>
            Dashboard
          </h1>

          <p className="dashboard-subtitle">
            Manage your support requests and
            operational work from one place.
          </p>
        </div>

        <div className="dashboard-header-actions">
          <NotificationBell />

          <button
            type="button"
            onClick={handleSignOut}
          >
            Sign out
          </button>
        </div>
      </header>

      {isLoading && (
        <div className="dashboard-metrics-loading" role="status" aria-live="polite">
          Loading live operational statistics...
        </div>
      )}

      {error && (
        <div className="dashboard-metrics-error" role="alert">
          <span>{error}</span>
          <button type="button" onClick={handleRetry}>
            Retry
          </button>
        </div>
      )}

      <section className="dashboard-workspace-grid">
        <article className="dashboard-workspace-card">
          <p className="dashboard-card-label">
            Employee workspace
          </p>

          <h2>
            IT support
          </h2>

          <p>
            Report an incident, request a service,
            or track tickets you have already
            created.
          </p>

          {metrics?.employee && (
            <div className="dashboard-kpi-grid" aria-label="Your IT support ticket statistics">
              <div className="dashboard-kpi-chip">
                <span className="dashboard-kpi-value">{metrics.employee.openTickets}</span>
                <span className="dashboard-kpi-label">Open Tickets</span>
              </div>
              <div className={`dashboard-kpi-chip ${metrics.employee.ticketsAwaitingResponse > 0 ? 'dashboard-kpi-chip-alert' : ''}`}>
                <span className="dashboard-kpi-value">{metrics.employee.ticketsAwaitingResponse}</span>
                <span className="dashboard-kpi-label">Awaiting Your Reply</span>
              </div>
              <div className="dashboard-kpi-chip">
                <span className="dashboard-kpi-value">{metrics.employee.resolvedTickets}</span>
                <span className="dashboard-kpi-label">Resolved Tickets</span>
              </div>
            </div>
          )}

          <div className="dashboard-action-buttons">
            <button
              type="button"
              onClick={() =>
                navigate(
                  '/tickets/new',
                )
              }
            >
              Create ticket
            </button>

            <button
              type="button"
              className="dashboard-secondary-button"
              onClick={() =>
                navigate(
                  '/tickets',
                )
              }
            >
              My tickets
            </button>

            <button
              type="button"
              className="dashboard-secondary-button"
              onClick={() =>
                navigate(
                  '/kb',
                )
              }
            >
              Knowledge Base
            </button>
          </div>
        </article>

        {isSupportEngineer && (
          <article className="dashboard-workspace-card dashboard-support-card">
            <p className="dashboard-card-label">
              Support workspace
            </p>

            <h2>
              Engineer operations
            </h2>

            <p>
              Claim incoming tickets and manage the
              active issues currently assigned to
              you.
            </p>

            {metrics?.support && (
              <div className="dashboard-kpi-grid" aria-label="Support engineer queue statistics">
                <div className="dashboard-kpi-chip">
                  <span className="dashboard-kpi-value">{metrics.support.assignedActiveTickets}</span>
                  <span className="dashboard-kpi-label">My Assigned</span>
                </div>
                <div className="dashboard-kpi-chip">
                  <span className="dashboard-kpi-value">{metrics.support.unassignedQueueTickets}</span>
                  <span className="dashboard-kpi-label">Unassigned Queue</span>
                </div>
                <div className={`dashboard-kpi-chip ${metrics.support.assignedSlaBreaches > 0 ? 'dashboard-kpi-chip-alert' : ''}`}>
                  <span className="dashboard-kpi-value">{metrics.support.assignedSlaBreaches}</span>
                  <span className="dashboard-kpi-label">SLA Breaches</span>
                </div>
              </div>
            )}

            <div className="dashboard-action-buttons">
              <button
                type="button"
                onClick={() =>
                  navigate(
                    '/support/tickets',
                  )
                }
              >
                My assigned tickets
              </button>

              <button
                type="button"
                className="dashboard-secondary-button"
                onClick={() =>
                  navigate(
                    '/support/queue',
                  )
                }
              >
                Support queue
              </button>

              <button
                type="button"
                className="dashboard-secondary-button"
                onClick={() =>
                  navigate(
                    '/kb',
                  )
                }
              >
                Knowledge Base
              </button>
            </div>
          </article>
        )}

        {canViewOperations && (
          <article className="dashboard-workspace-card">
            <p className="dashboard-card-label">
              Approval workspace
            </p>

            <h2>
              Service request approvals
            </h2>

            <p>
              Review pending service requests and
              approve or reject them before they
              enter the support workflow.
            </p>

            {metrics?.operations && (
              <div className="dashboard-kpi-grid" aria-label="Service request approvals count">
                <div className={`dashboard-kpi-chip ${metrics.operations.pendingApprovals > 0 ? 'dashboard-kpi-chip-alert' : ''}`}>
                  <span className="dashboard-kpi-value">{metrics.operations.pendingApprovals}</span>
                  <span className="dashboard-kpi-label">Pending Approvals</span>
                </div>
              </div>
            )}

            <div className="dashboard-action-buttons">
              <button
                type="button"
                onClick={() =>
                  navigate(
                    '/approvals',
                  )
                }
              >
                Open approval inbox
              </button>
            </div>
          </article>
        )}

        {canViewOperations && (
          <article className="dashboard-workspace-card">
            <p className="dashboard-card-label">
              Operations workspace
            </p>

            <h2>
              SLA operations
            </h2>

            <p>
              Review active SLA exposure,
              compliance, priority breakdowns,
              and recent breach history.
            </p>

            {metrics?.operations && (
              <div className="dashboard-kpi-grid" aria-label="Operational ticket statistics">
                <div className="dashboard-kpi-chip">
                  <span className="dashboard-kpi-value">{metrics.operations.totalActiveTickets}</span>
                  <span className="dashboard-kpi-label">Total Active</span>
                </div>
                <div className="dashboard-kpi-chip">
                  <span className="dashboard-kpi-value">{metrics.operations.unassignedQueueTickets}</span>
                  <span className="dashboard-kpi-label">Unassigned Queue</span>
                </div>
                <div className={`dashboard-kpi-chip ${metrics.operations.slaBreaches > 0 ? 'dashboard-kpi-chip-alert' : ''}`}>
                  <span className="dashboard-kpi-value">{metrics.operations.slaBreaches}</span>
                  <span className="dashboard-kpi-label">SLA Breaches</span>
                </div>
              </div>
            )}

            <div className="dashboard-action-buttons">
              <button
                type="button"
                onClick={() =>
                  navigate(
                    '/sla/dashboard',
                  )
                }
              >
                Open SLA dashboard
              </button>

              <button
                type="button"
                className="dashboard-secondary-button"
                onClick={() =>
                  navigate(
                    '/reports',
                  )
                }
              >
                Operational reports
              </button>
            </div>
          </article>
        )}

        {isAdmin && (
          <article className="dashboard-workspace-card">
            <p className="dashboard-card-label">
              Administration workspace
            </p>

            <h2>
              User management
            </h2>

            <p>
              Manage user accounts, assign roles, create users,
              and activate or deactivate system access.
            </p>

            <div className="dashboard-action-buttons">
              <button
                type="button"
                onClick={() =>
                  navigate(
                    '/admin/users',
                  )
                }
              >
                Manage users
              </button>
            </div>
          </article>
        )}

        {isAdmin && (
          <article className="dashboard-workspace-card">
            <p className="dashboard-card-label">
              Governance workspace
            </p>

            <h2>
              Audit trail
            </h2>

            <p>
              Review immutable records of important
              user actions, system events, ticket
              changes, approvals, and SLA breaches.
            </p>

            <div className="dashboard-action-buttons">
              <button
                type="button"
                onClick={() =>
                  navigate(
                    '/audit',
                  )
                }
              >
                Open audit trail
              </button>
            </div>
          </article>
        )}
      </section>

      <section className="user-card">
        <div className="account-card-heading">
          <div>
            <p className="dashboard-card-label">
              Account
            </p>

            <h2>
              Your profile
            </h2>
          </div>

          <span className="account-status">
            Active
          </span>
        </div>

        <div className="account-details-grid">
          <div>
            <span>
              Email
            </span>

            <strong>
              {user?.email}
            </strong>
          </div>

          <div>
            <span>
              User ID
            </span>

            <strong>
              {user?.userId}
            </strong>
          </div>

          <div>
            <span>
              Roles
            </span>

            <strong>
              {user?.roles.join(
                ', ',
              )}
            </strong>
          </div>
        </div>
      </section>

      <ChangePasswordCard />
    </main>
  )
}
