import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { createTicket } from '../api/ticket'
import { useAuth } from '../auth/useAuth'
import type {
  TicketResponse,
  TicketType,
} from '../types/ticket'
import { formatLabel } from '../utils/formatters'
import { TicketDeflectionCard } from '../components/tickets/TicketDeflectionCard'
import '../styles/kb.css'

const TICKET_DRAFT_KEY = 'flowdesk_ticket_draft'

interface TicketDraftState {
  type: TicketType
  title: string
  description: string
}

function getStoredTicketDraft(): TicketDraftState | null {
  try {
    const raw = sessionStorage.getItem(TICKET_DRAFT_KEY)
    if (!raw) return null
    return JSON.parse(raw) as TicketDraftState
  } catch {
    return null
  }
}

export function CreateTicketPage() {
  const { accessToken } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const locationDraft = (
    location.state as {
      ticketDraft?: TicketDraftState
    } | null
  )?.ticketDraft

  const initialDraft = locationDraft ?? getStoredTicketDraft()

  const [type, setType] = useState<TicketType>(initialDraft?.type ?? 'INCIDENT')
  const [title, setTitle] = useState(initialDraft?.title ?? '')
  const [description, setDescription] = useState(initialDraft?.description ?? '')

  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isSolved, setIsSolved] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [createdTicket, setCreatedTicket] =
    useState<TicketResponse | null>(null)

  // Persist draft in session storage so navigating away to read KB articles preserves form state
  useEffect(() => {
    if (!isSolved && (title.trim() || description.trim())) {
      sessionStorage.setItem(
        TICKET_DRAFT_KEY,
        JSON.stringify({ type, title, description }),
      )
    }
  }, [type, title, description, isSolved])

  function handleStartNewTicket() {
    setIsSolved(false)
    setCreatedTicket(null)
    setType('INCIDENT')
    setTitle('')
    setDescription('')
    setError(null)
    sessionStorage.removeItem(TICKET_DRAFT_KEY)
  }

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    // Prevent submission while solved or already submitting
    if (isSolved || isSubmitting) {
      return
    }

    setError(null)
    setCreatedTicket(null)

    if (!accessToken) {
      setError('Your session is no longer valid')
      return
    }

    const trimmedTitle = title.trim()
    const trimmedDescription = description.trim()

    if (!trimmedTitle || !trimmedDescription) {
      setError('Title and description are required')
      return
    }

    setIsSubmitting(true)

    try {
      const response = await createTicket(
        {
          type,
          title: trimmedTitle,
          description: trimmedDescription,
        },
        accessToken,
      )

      sessionStorage.removeItem(TICKET_DRAFT_KEY)
      setCreatedTicket(response)

      setType('INCIDENT')
      setTitle('')
      setDescription('')
    } catch (caughtError) {
      if (caughtError instanceof Error) {
        setError(caughtError.message)
      } else {
        setError('Unable to create ticket')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="ticket-page">
      <section className="ticket-form-card">
        <div className="ticket-form-heading">
          <p className="eyebrow">FlowDesk</p>
          <h1>Create a ticket</h1>
          <p>
            Report an IT incident or request a service.
          </p>
        </div>

        {isSolved ? (
          <section
            className="ticket-deflected-panel"
            aria-live="polite"
            role="region"
            aria-label="Issue resolved confirmation"
          >
            <div className="ticket-deflected-icon" aria-hidden="true">🎉</div>
            <h2>Issue resolved via Knowledge Base</h2>
            <p className="ticket-deflected-message">
              Great! We are glad a Knowledge Base guide solved your issue.
              No ticket was submitted to the IT support queue.
            </p>
            <div className="ticket-deflected-actions">
              <button
                type="button"
                className="primary-button"
                onClick={() => navigate('/dashboard')}
              >
                Return to Dashboard
              </button>
              <button
                type="button"
                className="secondary-button"
                onClick={handleStartNewTicket}
              >
                Create another ticket
              </button>
              <button
                type="button"
                className="secondary-button"
                onClick={() => navigate('/kb')}
              >
                Browse Knowledge Base
              </button>
            </div>
          </section>
        ) : createdTicket ? (
          <section
            className="ticket-success-panel"
            aria-live="polite"
          >
            <h2>Ticket created successfully</h2>

            <p className="ticket-success-number">
              Your ticket number is{' '}
              <strong>
                {createdTicket.ticketNumber}
              </strong>
            </p>

            <div className="ticket-success-tags">
              <span className="priority-badge priority-badge-low">
                {formatLabel(createdTicket.type)}
              </span>

              <span className={`priority-badge priority-badge-${createdTicket.priority.toLowerCase()}`}>
                {formatLabel(createdTicket.priority)}
              </span>

              <span className={`support-status support-status-${createdTicket.status.toLowerCase().replaceAll('_', '-')}`}>
                {formatLabel(createdTicket.status)}
              </span>
            </div>

            <div className="ticket-success-actions">
              <button
                type="button"
                className="primary-button"
                onClick={() =>
                  navigate(`/tickets/${createdTicket.ticketNumber}`)
                }
              >
                View ticket {createdTicket.ticketNumber}
              </button>

              <button
                type="button"
                className="secondary-button"
                onClick={handleStartNewTicket}
              >
                Create another ticket
              </button>

              <button
                type="button"
                className="secondary-button"
                onClick={() => navigate('/tickets')}
              >
                My Tickets
              </button>
            </div>
          </section>
        ) : (
          <form
            className="ticket-form"
            onSubmit={handleSubmit}
          >
            <label>
              Ticket type

              <select
                value={type}
                onChange={(event) =>
                  setType(event.target.value as TicketType)
                }
                disabled={isSubmitting}
              >
                <option value="INCIDENT">
                  Incident
                </option>

                <option value="SERVICE_REQUEST">
                  Service request
                </option>
              </select>
            </label>

            <label>
              Title

              <input
                type="text"
                value={title}
                onChange={(event) =>
                  setTitle(event.target.value)
                }
                maxLength={200}
                placeholder="Example: VPN is not working"
                disabled={isSubmitting}
                required
              />
            </label>

            <TicketDeflectionCard
              title={title}
              ticketType={type}
              description={description}
              onSolved={() => {
                sessionStorage.removeItem(TICKET_DRAFT_KEY)
                setIsSolved(true)
                setTitle('')
                setDescription('')
              }}
              onDeflected={() => {
                sessionStorage.removeItem(TICKET_DRAFT_KEY)
                navigate('/dashboard')
              }}
            />

            <label>
              Description

              <textarea
                value={description}
                onChange={(event) =>
                  setDescription(event.target.value)
                }
                maxLength={5000}
                placeholder="Describe the issue or request"
                rows={6}
                disabled={isSubmitting}
                required
              />
            </label>

            {error && (
              <p
                className="form-error"
                role="alert"
              >
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={isSubmitting}
            >
              {isSubmitting
                ? 'Creating ticket...'
                : 'Create ticket'}
            </button>
          </form>
        )}
      </section>
    </main>
  )
}
