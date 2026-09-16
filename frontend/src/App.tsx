import {
  Navigate,
  Route,
  Routes,
} from 'react-router-dom'

import './App.css'

import { ProtectedRoute } from './components/ProtectedRoute'

import { CreateTicketPage } from './pages/CreateTicketPage'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { MyAssignedTicketsPage } from './pages/MyAssignedTicketsPage'
import { MyTicketsPage } from './pages/MyTicketsPage'
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
          element={<SupportTicketDetailsPage />}
        />
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