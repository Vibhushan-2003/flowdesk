import {
  useEffect,
  useState,
} from 'react'

import {
  useNavigate,
} from 'react-router-dom'

import {
  getSlaDashboard,
} from '../api/sla'

import {
  useAuth,
} from '../auth/useAuth'

import {
  NotificationBell,
} from '../components/NotificationBell'

import type {
  SlaDashboardResponse,
  SlaTicketPriority,
} from '../types/sla'

const PRIORITIES: SlaTicketPriority[] = [
  'CRITICAL',
  'HIGH',
  'MEDIUM',
  'LOW',
]

export function SlaDashboardPage() {
  const {
    accessToken,
    signOut,
  } = useAuth()

  const navigate =
    useNavigate()

  const [
    dashboard,
    setDashboard,
  ] =
    useState<SlaDashboardResponse | null>(
      null,
    )

  const [
    isLoading,
    setIsLoading,
  ] =
    useState(true)

  const [
    error,
    setError,
  ] =
    useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadDashboard() {
      if (!accessToken) {
        if (!cancelled) {
          setError(
            'Your session is no longer valid',
          )

          setIsLoading(false)
        }

        return
      }

      setIsLoading(true)
      setError(null)

      try {
        const response =
          await getSlaDashboard(
            accessToken,
          )

        if (!cancelled) {
          setDashboard(
            response,
          )
        }
      } catch (caughtError) {
        if (cancelled) {
          return
        }

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
  }, [accessToken])

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
            SLA Operations
          </h1>

          <p className="dashboard-subtitle">
            Monitor active service levels,
            compliance, and recent SLA breaches.
          </p>
        </div>

        <div className="dashboard-header-actions">
          <NotificationBell />

          <button
            type="button"
            className="dashboard-secondary-button"
            onClick={() =>
              navigate(
                '/dashboard',
              )
            }
          >
            Dashboard
          </button>

          <button
            type="button"
            onClick={handleSignOut}
          >
            Sign out
          </button>
        </div>
      </header>

      {isLoading && (
        <section className="user-card">
          <p>
            Loading SLA operations...
          </p>
        </section>
      )}

      {!isLoading && error && (
        <section className="user-card">
          <p className="notification-error">
            {error}
          </p>
        </section>
      )}

      {!isLoading &&
        dashboard && (
        <>
          <section className="dashboard-workspace-grid">
            <MetricCard
              label="Active tickets"
              value={
                dashboard.activeTickets
              }
              description="Tickets currently in an active operational state."
            />

            <MetricCard
              label="Response breached"
              value={
                dashboard.responseBreached
              }
              description="Active tickets still waiting for a first response after the deadline."
            />

            <MetricCard
              label="Resolution breached"
              value={
                dashboard.resolutionBreached
              }
              description="Active unresolved tickets already past their resolution deadline."
            />

            <MetricCard
              label="Response compliance"
              value={formatPercent(
                dashboard
                  .responseCompliancePercent,
              )}
              description="Known first responses completed within their SLA."
            />

            <MetricCard
              label="Resolution compliance"
              value={formatPercent(
                dashboard
                  .resolutionCompliancePercent,
              )}
              description="Known resolutions completed within their SLA."
            />
          </section>

          <section className="user-card">
            <div className="account-card-heading">
              <div>
                <p className="dashboard-card-label">
                  Current exposure
                </p>

                <h2>
                  Breached tickets by priority
                </h2>
              </div>
            </div>

            <div className="account-details-grid">
              {PRIORITIES.map(
                (priority) => (
                  <div key={priority}>
                    <span>
                      {formatLabel(
                        priority,
                      )}
                    </span>

                    <strong>
                      {
                        dashboard
                          .breachedByPriority[
                          priority
                        ]
                      }
                    </strong>
                  </div>
                ),
              )}
            </div>
          </section>

          <section className="user-card">
            <div className="account-card-heading">
              <div>
                <p className="dashboard-card-label">
                  Audit history
                </p>

                <h2>
                  Recent SLA breaches
                </h2>
              </div>
            </div>

            {dashboard
              .recentBreaches
              .length === 0 ? (
              <p>
                No SLA breach events have been
                recorded yet.
              </p>
            ) : (
              <div className="dashboard-workspace-grid">
                {dashboard
                  .recentBreaches
                  .map(
                    (breach) => (
                      <article
                        key={`${breach.ticketNumber}-${breach.eventType}`}
                        className="dashboard-workspace-card"
                      >
                        <p className="dashboard-card-label">
                          {
                            breach.ticketNumber
                          }
                        </p>

                        <h2>
                          {formatLabel(
                            breach.eventType,
                          )}
                        </h2>

                        <p>
                          Priority:{' '}
                          <strong>
                            {
                              breach.priority
                            }
                          </strong>
                        </p>

                        <p>
                          Status:{' '}
                          <strong>
                            {formatLabel(
                              breach.status,
                            )}
                          </strong>
                        </p>

                        <p>
                          Breached:{' '}
                          <strong>
                            {formatDate(
                              breach
                                .occurredAt,
                            )}
                          </strong>
                        </p>
                      </article>
                    ),
                  )}
              </div>
            )}
          </section>
        </>
      )}
    </main>
  )
}

interface MetricCardProps {
  label: string
  value: number | string
  description: string
}

function MetricCard({
  label,
  value,
  description,
}: MetricCardProps) {
  return (
    <article className="dashboard-workspace-card">
      <p className="dashboard-card-label">
        {label}
      </p>

      <h2>
        {value}
      </h2>

      <p>
        {description}
      </p>
    </article>
  )
}

function formatPercent(
  value: number | null,
) {
  if (value === null) {
    return 'Not enough data'
  }

  return `${value.toFixed(1)}%`
}

function formatLabel(
  value: string,
) {
  return value.replaceAll(
    '_',
    ' ',
  )
}

function formatDate(
  value: string,
) {
  const date =
    new Date(
      value,
    )

  if (
    Number.isNaN(
      date.getTime(),
    )
  ) {
    return value
  }

  return date.toLocaleString()
}
