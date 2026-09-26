import {
  Navigate,
  Route,
  Routes,
} from 'react-router-dom'

import './App.css'

import { ProtectedRoute } from './components/ProtectedRoute'
import { RoleProtectedRoute } from './components/RoleProtectedRoute'

import { AuditTrailPage } from './pages/AuditTrailPage'
import { CreateTicketPage } from './pages/CreateTicketPage'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { MyAssignedTicketsPage } from './pages/MyAssignedTicketsPage'
import { MyTicketsPage } from './pages/MyTicketsPage'
import { SlaDashboardPage } from './pages/SlaDashboardPage'
import { SupportQueuePage } from './pages/SupportQueuePage'
import { SupportTicketDetailsPage } from './pages/SupportTicketDetailsPage'
import { TicketDetailsPage } from './pages/TicketDetailsPage'

function App() {
  return (
    <Routes>
      <Route
        path="/login"
        element={<LoginPage />}
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