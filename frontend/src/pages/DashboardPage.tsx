import { useNavigate } from 'react-router-dom'

import { useAuth } from '../auth/useAuth'

export function DashboardPage() {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()

  function handleSignOut() {
    signOut()
    navigate('/login')
  }

  return (
    <main className="dashboard-page">
      <header className="dashboard-header">
        <div>
          <p className="eyebrow">FlowDesk</p>
          <h1>Dashboard</h1>
        </div>

        <button
          type="button"
          onClick={handleSignOut}
        >
          Sign out
        </button>
      </header>

      <section className="dashboard-actions">
        <h2>IT Support</h2>

        <p>
          Report an incident or submit a service request.
        </p>

        <button
          type="button"
          onClick={() => navigate('/tickets/new')}
        >
          Create ticket
        </button>
      </section>

      <section className="user-card">
        <h2>Your account</h2>

        <p>
          <strong>Email:</strong>{' '}
          {user?.email}
        </p>

        <p>
          <strong>User ID:</strong>{' '}
          {user?.userId}
        </p>

        <p>
          <strong>Roles:</strong>{' '}
          {user?.roles.join(', ')}
        </p>
      </section>
    </main>
  )
}