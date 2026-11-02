import { lazy, Suspense } from 'react'
import {
  Navigate,
  Route,
  Routes,
} from 'react-router-dom'

import './App.css'

import { ProtectedRoute } from './components/ProtectedRoute'
import { RoleProtectedRoute } from './components/RoleProtectedRoute'
import { AppShell } from './components/layout/AppShell'

const ApprovalInboxPage = lazy(() =>
  import('./pages/ApprovalInboxPage').then((m) => ({ default: m.ApprovalInboxPage }))
)
const AuditTrailPage = lazy(() =>
  import('./pages/AuditTrailPage').then((m) => ({ default: m.AuditTrailPage }))
)
const CreateTicketPage = lazy(() =>
  import('./pages/CreateTicketPage').then((m) => ({ default: m.CreateTicketPage }))
)
const DashboardPage = lazy(() =>
  import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage }))
)
const ForgotPasswordPage = lazy(() =>
  import('./pages/ForgotPasswordPage').then((m) => ({ default: m.ForgotPasswordPage }))
)
const LoginPage = lazy(() =>
  import('./pages/LoginPage').then((m) => ({ default: m.LoginPage }))
)
const MyAssignedTicketsPage = lazy(() =>
  import('./pages/MyAssignedTicketsPage').then((m) => ({ default: m.MyAssignedTicketsPage }))
)
const MyTicketsPage = lazy(() =>
  import('./pages/MyTicketsPage').then((m) => ({ default: m.MyTicketsPage }))
)
const AcceptInvitationPage = lazy(() =>
  import('./pages/AcceptInvitationPage').then((m) => ({ default: m.AcceptInvitationPage }))
)
const GoogleCallbackPage = lazy(() =>
  import('./pages/GoogleCallbackPage').then((m) => ({ default: m.GoogleCallbackPage }))
)
const ResetPasswordPage = lazy(() =>
  import('./pages/ResetPasswordPage').then((m) => ({ default: m.ResetPasswordPage }))
)
const SlaDashboardPage = lazy(() =>
  import('./pages/SlaDashboardPage').then((m) => ({ default: m.SlaDashboardPage }))
)
const SupportQueuePage = lazy(() =>
  import('./pages/SupportQueuePage').then((m) => ({ default: m.SupportQueuePage }))
)
const SupportTicketDetailsPage = lazy(() =>
  import('./pages/SupportTicketDetailsPage').then((m) => ({ default: m.SupportTicketDetailsPage }))
)
const TicketDetailsPage = lazy(() =>
  import('./pages/TicketDetailsPage').then((m) => ({ default: m.TicketDetailsPage }))
)
const AttachmentSecurityPage = lazy(() =>
  import('./pages/AttachmentSecurityPage').then((m) => ({ default: m.AttachmentSecurityPage }))
)
const UserManagementPage = lazy(() =>
  import('./pages/UserManagementPage').then((m) => ({ default: m.UserManagementPage }))
)
const KnowledgeBasePage = lazy(() =>
  import('./pages/KnowledgeBasePage').then((m) => ({ default: m.KnowledgeBasePage }))
)
const ArticleViewPage = lazy(() =>
  import('./pages/ArticleViewPage').then((m) => ({ default: m.ArticleViewPage }))
)
const ArticleEditorPage = lazy(() =>
  import('./pages/ArticleEditorPage').then((m) => ({ default: m.ArticleEditorPage }))
)
const ReportsPage = lazy(() =>
  import('./pages/ReportsPage').then((m) => ({ default: m.ReportsPage }))
)
const ProfilePage = lazy(() =>
  import('./pages/ProfilePage').then((m) => ({ default: m.ProfilePage }))
)
const AccountSettingsPage = lazy(() =>
  import('./pages/AccountSettingsPage').then((m) => ({ default: m.AccountSettingsPage }))
)

function PageLoading() {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '50vh',
        color: '#6b7280',
        fontSize: '1rem',
      }}
      role="status"
      aria-label="Loading page content"
    >
      Loading...
    </div>
  )
}

function App() {
  return (
    <Suspense fallback={<PageLoading />}>
      <Routes>
        <Route
          path="/login"
          element={<LoginPage />}
        />

        <Route
          path="/auth/google/callback"
          element={<GoogleCallbackPage />}
        />

        <Route
          path="/forgot-password"
          element={<ForgotPasswordPage />}
        />

        <Route
          path="/reset-password"
          element={<ResetPasswordPage />}
        />

        <Route
          path="/accept-invitation"
          element={<AcceptInvitationPage />}
        />

        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
          <Route
            path="/dashboard"
            element={<DashboardPage />}
          />

          <Route
            path="/tickets"
            element={<MyTicketsPage />}
          />

          <Route
            path="/tickets/new"
            element={<CreateTicketPage />}
          />

          <Route
            path="/tickets/:ticketNumber"
            element={<TicketDetailsPage />}
          />

          <Route
            path="/profile"
            element={<ProfilePage />}
          />

          <Route
            path="/account"
            element={<AccountSettingsPage />}
          />

          <Route
            element={
              <RoleProtectedRoute
                allowedRoles={['SUPPORT_ENGINEER']}
              />
            }
          >
            <Route
              path="/support/queue"
              element={<SupportQueuePage />}
            />
          </Route>

          <Route
            element={
              <RoleProtectedRoute
                allowedRoles={[
                  'SUPPORT_ENGINEER',
                  'TEAM_LEAD',
                  'ADMIN',
                ]}
              />
            }
          >
            <Route
              path="/support/tickets"
              element={<MyAssignedTicketsPage />}
            />
            <Route
              path="/support/tickets/:ticketNumber"
              element={<SupportTicketDetailsPage />}
            />
          </Route>

          <Route
            path="/kb"
            element={<KnowledgeBasePage />}
          />

          <Route
            path="/kb/articles/:slug"
            element={<ArticleViewPage />}
          />

          <Route
            element={
              <RoleProtectedRoute
                allowedRoles={[
                  'SUPPORT_ENGINEER',
                  'TEAM_LEAD',
                  'ADMIN',
                ]}
              />
            }
          >
            <Route
              path="/support/kb/articles/new"
              element={<ArticleEditorPage />}
            />
            <Route
              path="/support/kb/articles/:id/edit"
              element={<ArticleEditorPage />}
            />
          </Route>

          <Route
            element={
              <RoleProtectedRoute
                allowedRoles={[
                  'TEAM_LEAD',
                  'ADMIN',
                ]}
              />
            }
          >
            <Route
              path="/approvals"
              element={<ApprovalInboxPage />}
            />

            <Route
              path="/sla/dashboard"
              element={<SlaDashboardPage />}
            />

            <Route
              path="/reports"
              element={<ReportsPage />}
            />
          </Route>

          <Route
            element={
              <RoleProtectedRoute
                allowedRoles={[
                  'ADMIN',
                ]}
              />
            }
          >
            <Route
              path="/audit"
              element={<AuditTrailPage />}
            />

            <Route
              path="/admin/users"
              element={<UserManagementPage />}
            />
            <Route
              path="/admin/attachments"
              element={<AttachmentSecurityPage />}
            />
          </Route>
          </Route>
        </Route>

        <Route
          path="/"
          element={
            <Navigate
              to="/dashboard"
              replace
            />
          }
        />

        <Route
          path="*"
          element={
            <Navigate
              to="/dashboard"
              replace
            />
          }
        />
      </Routes>
    </Suspense>
  )
}

export default App
