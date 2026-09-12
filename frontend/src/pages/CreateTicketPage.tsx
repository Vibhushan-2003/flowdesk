import { useState } from 'react'
import type { FormEvent } from 'react'

import { createTicket } from '../api/ticket'
import { useAuth } from '../auth/useAuth'
import type {
  TicketResponse,
  TicketType,
} from '../types/ticket'

export function CreateTicketPage() {
  const { accessToken } = useAuth()

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
            className="ticket-success"
            aria-live="polite"
          >
            <h2>Ticket created successfully</h2>

            <p>
              Your ticket number is{' '}
              <strong>
                {createdTicket.ticketNumber}
              </strong>
            </p>

            <p>
              {createdTicket.type}
              {' · '}
              {createdTicket.priority}
              {' · '}
              {createdTicket.status}
            </p>
          </section>
        )}
      </section>
    </main>
  )
}