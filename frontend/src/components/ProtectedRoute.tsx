import {
  Navigate,
  Outlet,
  useLocation,
} from 'react-router-dom'

import {
  useAuth,
} from '../auth/useAuth'

export function ProtectedRoute() {
  const {
    isAuthenticated,
    isAuthLoading,
  } =
    useAuth()

  const location =
    useLocation()

  if (isAuthLoading) {
    return (
      <main className="support-loading-state">
        <div className="support-loading-dot" />

        <p>
          Restoring your session...
        </p>
      </main>
    )
  }

  if (!isAuthenticated) {
    return (
      <Navigate
        to="/login"
        replace
        state={{
          from: location,
        }}
      />
    )
  }

  return <Outlet />
}