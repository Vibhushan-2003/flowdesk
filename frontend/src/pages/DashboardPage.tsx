import { useNavigate } from 'react-router-dom'

import { useAuth } from '../auth/useAuth'
import { NotificationBell } from '../components/NotificationBell'

export function DashboardPage() {
  const {
    user,
    signOut,
  } = useAuth()

  const navigate = useNavigate()

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
    </main>
  )
}