import {
  Navigate,
  Outlet,
} from 'react-router-dom'

import {
  useAuth,
} from '../auth/useAuth'

interface RoleProtectedRouteProps {
  allowedRoles: string[]
}

export function RoleProtectedRoute({
  allowedRoles,
}: RoleProtectedRouteProps) {
  const {
    isAuthenticated,
    isAuthLoading,
    user,
  } =
    useAuth()

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
      />
    )
  }

  const hasAllowedRole =
    user?.roles.some(
      (role) =>
        allowedRoles.includes(
          role,
        ),
    ) ?? false

  if (!hasAllowedRole) {
    return (
      <Navigate
        to="/dashboard"
        replace
      />
    )
  }

  return <Outlet />
}