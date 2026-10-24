import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'

import { createTicket } from '../api/ticket'
import { useAuth } from '../auth/useAuth'
import type {
  TicketResponse,
  TicketType,
} from '../types/ticket'
import { formatLabel } from '../utils/formatters'

export function CreateTicketPage() {
  const { accessToken } = useAuth()
  const navigate = useNavigate()

  const [type, setType] = useState<TicketType>('INCIDENT')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')

  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [createdTicket, setCreatedTicket] =
    useState<TicketResponse | null>(null)

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

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

        {createdTicket && (
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
                onClick={() => setCreatedTicket(null)}
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
        )}
      </section>
    </main>
  )
}