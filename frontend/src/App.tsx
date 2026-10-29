import {
  Navigate,
  Route,
  Routes,
} from 'react-router-dom'

import './App.css'

import { ProtectedRoute } from './components/ProtectedRoute'
import { RoleProtectedRoute } from './components/RoleProtectedRoute'

import { ApprovalInboxPage } from './pages/ApprovalInboxPage'
import { AuditTrailPage } from './pages/AuditTrailPage'
import { CreateTicketPage } from './pages/CreateTicketPage'
import { DashboardPage } from './pages/DashboardPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { LoginPage } from './pages/LoginPage'
import { MyAssignedTicketsPage } from './pages/MyAssignedTicketsPage'
import { MyTicketsPage } from './pages/MyTicketsPage'
import { AcceptInvitationPage } from './pages/AcceptInvitationPage'
import { GoogleCallbackPage } from './pages/GoogleCallbackPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { SlaDashboardPage } from './pages/SlaDashboardPage'
import { SupportQueuePage } from './pages/SupportQueuePage'
import { SupportTicketDetailsPage } from './pages/SupportTicketDetailsPage'
import { TicketDetailsPage } from './pages/TicketDetailsPage'
import { UserManagementPage } from './pages/UserManagementPage'
import { KnowledgeBasePage } from './pages/KnowledgeBasePage'
import { ArticleViewPage } from './pages/ArticleViewPage'
import { ArticleEditorPage } from './pages/ArticleEditorPage'

function App() {
  return (
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
          path="/support/queue"
          element={<SupportQueuePage />}
        />

        <Route
          path="/support/tickets"
          element={<MyAssignedTicketsPage />}
        />

        <Route
          path="/support/tickets/:ticketNumber"
          element={
            <SupportTicketDetailsPage />
          }
        />

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
            element={
              <ApprovalInboxPage />
            }
          />

          <Route
            path="/sla/dashboard"
            element={
              <SlaDashboardPage />
            }
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
            element={
              <AuditTrailPage />
            }
          />

          <Route
            path="/admin/users"
            element={
              <UserManagementPage />
            }
          />
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
  )
}

export default App
