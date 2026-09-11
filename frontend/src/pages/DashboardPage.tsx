import { useNavigate } from 'react-router-dom'

import { useAuth } from '../auth/useAuth'

export function DashboardPage() {
  const {
    user,
    signOut,
  } = useAuth()

  const navigate = useNavigate()

  function handleLogout() {
    signOut()

    navigate('/login', {
      replace: true,
    })
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
          onClick={handleLogout}
        >
          Sign out
        </button>
      </header>

      <section className="user-card">
        <h2>Signed-in user</h2>

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