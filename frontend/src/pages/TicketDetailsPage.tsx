import {
  useEffect,
  useState,
} from 'react'
import {
  useNavigate,
  useParams,
} from 'react-router-dom'

import { getTicket } from '../api/ticket'
import { useAuth } from '../auth/useAuth'
import type { TicketResponse } from '../types/ticket'

function formatLabel(value: string) {
  return value.replaceAll('_', ' ')
}

function formatDate(value: string) {
  return new Date(value).toLocaleString()
}

export function TicketDetailsPage() {
  const { accessToken } = useAuth()
  const { ticketNumber } = useParams()
  const navigate = useNavigate()

  const [ticket, setTicket] =
    useState<TicketResponse | null>(null)

  const [isLoading, setIsLoading] =
    useState(true)

  const [error, setError] =
    useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadTicket() {
      if (!accessToken) {
        if (!cancelled) {
          setError('Your session is no longer valid')
          setIsLoading(false)
        }

        return
      }

      if (!ticketNumber) {
        if (!cancelled) {
          setError('Ticket number is missing')
          setIsLoading(false)
        }

        return
      }

      setIsLoading(true)
      setError(null)

      try {
        const response = await getTicket(
          accessToken,
          ticketNumber,
        )

        if (!cancelled) {
          setTicket(response)
        }
      } catch (caughtError) {
        if (cancelled) {
          return
        }

        if (caughtError instanceof Error) {
          setError(caughtError.message)
        } else {
          setError('Unable to load ticket')
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    void loadTicket()

    return () => {
      cancelled = true
    }
  }, [accessToken, ticketNumber])

  return (
    <main className="ticket-details-page">
      <header className="ticket-details-header">
        <div>
          <p className="eyebrow">FlowDesk</p>
          <h1>Ticket details</h1>
        </div>

        <button
          type="button"
          onClick={() => navigate('/tickets')}
        >
          Back to my tickets
        </button>
      </header>

      {isLoading && (
        <p>Loading ticket...</p>
      )}

      {error && (
        <section className="ticket-error">
          <p
            className="form-error"
            role="alert"
          >
            {error}
          </p>

          <button
            type="button"
            onClick={() => navigate('/tickets')}
          >
            Back to my tickets
          </button>
        </section>
      )}

      {!isLoading &&
        !error &&
        ticket && (
          <article className="ticket-details-card">
            <header>
              <p className="ticket-number">
                {ticket.ticketNumber}
              </p>

              <h2>{ticket.title}</h2>

              <p>
                {formatLabel(ticket.type)}
                {' · '}
                {ticket.priority}
                {' · '}
                {formatLabel(ticket.status)}
              </p>
            </header>

            <section className="ticket-description">
              <h3>Description</h3>

              <p>{ticket.description}</p>
            </section>

            <section className="ticket-details-grid">
              <div>
                <strong>Ticket type</strong>
                <p>{formatLabel(ticket.type)}</p>
              </div>

              <div>
                <strong>Priority</strong>
                <p>{ticket.priority}</p>
              </div>

              <div>
                <strong>Status</strong>
                <p>{formatLabel(ticket.status)}</p>
              </div>

              <div>
                <strong>Created by</strong>
                <p>{ticket.createdByEmail}</p>
              </div>

              <div>
                <strong>Created</strong>
                <p>{formatDate(ticket.createdAt)}</p>
              </div>

              <div>
                <strong>Last updated</strong>
                <p>{formatDate(ticket.updatedAt)}</p>
              </div>
            </section>
          </article>
        )}
    </main>
  )
}