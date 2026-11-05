import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  ArrowLeft,
  CheckCircle2,
  AlertCircle,
  HelpCircle,
  Paperclip,
} from 'lucide-react'

import { createTicket } from '../api/ticket'
import { useAuth } from '../auth/useAuth'
import type {
  TicketPriority,
  TicketResponse,
  TicketType,
} from '../types/ticket'
import { formatLabel } from '../utils/formatters'
import { TicketDeflectionCard } from '../components/tickets/TicketDeflectionCard'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { StatusBadge } from '../components/ui/StatusBadge'
import { PriorityBadge } from '../components/ui/PriorityBadge'
import '../styles/kb.css'

const TICKET_DRAFT_KEY = 'flowdesk_ticket_draft'

interface TicketDraftState {
  userId?: string
  type: TicketType
  title: string
  description: string
}

function getStoredTicketDraft(currentUserId?: string): TicketDraftState | null {
  try {
    const raw = sessionStorage.getItem(TICKET_DRAFT_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as TicketDraftState
    if (parsed.userId && currentUserId && parsed.userId !== currentUserId) {
      sessionStorage.removeItem(TICKET_DRAFT_KEY)
      return null
    }
    return parsed
  } catch {
    return null
  }
}

export function CreateTicketPage() {
  const { accessToken, user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const locationDraft = (
    location.state as {
      ticketDraft?: TicketDraftState
    } | null
  )?.ticketDraft

  const initialDraft = locationDraft ?? getStoredTicketDraft(user?.userId)

  const [type, setType] = useState<TicketType>(initialDraft?.type ?? 'INCIDENT')
  const [title, setTitle] = useState(initialDraft?.title ?? '')
  const [description, setDescription] = useState(initialDraft?.description ?? '')
  const [priority, setPriority] = useState<TicketPriority>('MEDIUM')

  const [isSubmitting, setIsSubmitting] = useState(false)
  const isSubmittingRef = useRef(false)
  const idempotencyKeyRef = useRef<string | null>(null)

  const [isSolved, setIsSolved] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [createdTicket, setCreatedTicket] = useState<TicketResponse | null>(null)

  // Persist draft in session storage so navigating away to read KB articles preserves form state
  useEffect(() => {
    if (!isSolved && (title.trim() || description.trim())) {
      sessionStorage.setItem(
        TICKET_DRAFT_KEY,
        JSON.stringify({ userId: user?.userId, type, title, description }),
      )
    }
  }, [type, title, description, isSolved, user?.userId])

  function handleStartNewTicket() {
    setIsSolved(false)
    setCreatedTicket(null)
    setType('INCIDENT')
    setTitle('')
    setDescription('')
    setPriority('MEDIUM')
    setError(null)
    sessionStorage.removeItem(TICKET_DRAFT_KEY)
    idempotencyKeyRef.current = null
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    // Prevent submission while solved or already submitting (synchronous ref check prevents rapid double-clicks)
    if (isSolved || isSubmitting || isSubmittingRef.current) {
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

    isSubmittingRef.current = true
    setIsSubmitting(true)

    // Generate or reuse idempotency key for this submission attempt
    if (!idempotencyKeyRef.current) {
      idempotencyKeyRef.current =
        typeof crypto !== 'undefined' && crypto.randomUUID
          ? crypto.randomUUID()
          : `ticket-${Date.now().toString(36)}-${Math.random().toString(36).substring(2, 9)}`
    }

    try {
      const response = await createTicket(
        {
          type,
          title: trimmedTitle,
          description: trimmedDescription,
        },
        accessToken,
        idempotencyKeyRef.current,
      )

      // On successful creation, reset idempotency key so next ticket gets a fresh key
      idempotencyKeyRef.current = null
      sessionStorage.removeItem(TICKET_DRAFT_KEY)
      setCreatedTicket(response)

      setType('INCIDENT')
      setTitle('')
      setDescription('')
    } catch (caughtError) {
      // Retain the same idempotency key on failure so retrying sends the same key
      if (caughtError instanceof Error) {
        setError(caughtError.message)
      } else {
        setError('Unable to create ticket')
      }
    } finally {
      isSubmittingRef.current = false
      setIsSubmitting(false)
    }
  }

  return (
    <div className="ticket-page horizon-create-ticket-workspace">
      {/* Page Header */}
      <PageHeader
        title="Create a Ticket"
        subtitle="Report an IT incident or request assistance from IT support."
        actions={
          <Button
            variant="secondary"
            size="sm"
            onClick={() => navigate('/tickets')}
            leadingIcon={<ArrowLeft size={14} />}
          >
            My tickets
          </Button>
        }
      />

      {/* Confirmation View 1: Issue Solved via Deflection */}
      {isSolved ? (
        <section
          className="ticket-deflected-panel horizon-card"
          aria-live="polite"
          role="region"
          aria-label="Issue resolved confirmation"
          style={{ padding: '3rem 2rem', textAlign: 'center', maxWidth: '640px', margin: '2rem auto' }}
        >
          <div className="ticket-deflected-icon" aria-hidden="true" style={{ fontSize: '3rem', marginBottom: '1rem' }}>
            🎉
          </div>
          <h2 style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 'var(--font-weight-bold)', color: 'var(--color-text-primary)' }}>
            Issue resolved via Knowledge Base
          </h2>
          <p className="ticket-deflected-message" style={{ color: 'var(--color-text-secondary)', margin: '1rem 0 2rem' }}>
            Great! We are glad a Knowledge Base guide solved your issue.
            No ticket was submitted to the IT support queue.
          </p>
          <div className="ticket-deflected-actions" style={{ display: 'flex', gap: '1rem', justifyContent: 'center', flexWrap: 'wrap' }}>
            <button
              type="button"
              className="primary-button horizon-btn horizon-btn--primary horizon-btn--md"
              onClick={() => navigate('/dashboard')}
            >
              Return to Dashboard
            </button>
            <button
              type="button"
              className="secondary-button horizon-btn horizon-btn--secondary horizon-btn--md"
              onClick={handleStartNewTicket}
            >
              Create another ticket
            </button>
            <button
              type="button"
              className="secondary-button horizon-btn horizon-btn--secondary horizon-btn--md"
              onClick={() => navigate('/kb')}
            >
              Browse Knowledge Base
            </button>
          </div>
        </section>
      ) : createdTicket ? (
        /* Confirmation View 2: Ticket Created Successfully */
        <section
          className="ticket-success-panel horizon-card"
          aria-live="polite"
          style={{ padding: '3rem 2rem', textAlign: 'center', maxWidth: '640px', margin: '2rem auto' }}
        >
          <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '1rem' }}>
            <div style={{ width: '56px', height: '56px', borderRadius: '50%', background: 'var(--color-success-subtle)', color: 'var(--color-success)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <CheckCircle2 size={32} />
            </div>
          </div>

          <h2 style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 'var(--font-weight-bold)', color: 'var(--color-text-primary)' }}>
            Ticket created successfully
          </h2>

          <p className="ticket-success-number" style={{ fontSize: 'var(--font-size-lg)', margin: '1rem 0 1.5rem' }}>
            Your ticket number is{' '}
            <strong style={{ fontFamily: 'var(--font-family-mono)', color: 'var(--color-primary)' }}>
              {createdTicket.ticketNumber}
            </strong>
          </p>

          <div className="ticket-success-tags" style={{ display: 'flex', gap: '0.5rem', justifyContent: 'center', marginBottom: '2rem' }}>
            <Badge variant="neutral" size="md">
              {formatLabel(createdTicket.type)}
            </Badge>
            <PriorityBadge priority={createdTicket.priority} size="md" />
            <StatusBadge status={createdTicket.status} size="md" />
          </div>

          <div className="ticket-success-actions" style={{ display: 'flex', gap: '1rem', justifyContent: 'center', flexWrap: 'wrap' }}>
            <button
              type="button"
              className="primary-button horizon-btn horizon-btn--primary horizon-btn--md"
              onClick={() => navigate(`/tickets/${createdTicket.ticketNumber}`)}
            >
              View ticket {createdTicket.ticketNumber}
            </button>

            <button
              type="button"
              className="secondary-button horizon-btn horizon-btn--secondary horizon-btn--md"
              onClick={handleStartNewTicket}
            >
              Create another ticket
            </button>

            <button
              type="button"
              className="secondary-button horizon-btn horizon-btn--secondary horizon-btn--md"
              onClick={() => navigate('/tickets')}
            >
              My Tickets
            </button>
          </div>
        </section>
      ) : (
        /* Purposeful Two-Area Form Layout: Main Form + Contextual Help */
        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 340px', gap: '1.5rem', alignItems: 'start' }} className="horizon-create-grid">
          {/* Main Incident / Request Form */}
          <Card padded className="ticket-form-card">
            <form className="ticket-form horizon-auth-form" onSubmit={handleSubmit}>
              {/* Type Select */}
              <div className="horizon-form-field">
                <label htmlFor="ticket-type-select" className="horizon-label">
                  Ticket type
                </label>
                <div className="horizon-select-wrapper">
                  <select
                    id="ticket-type-select"
                    className="horizon-select"
                    value={type}
                    onChange={(event) => {
                      setType(event.target.value as TicketType)
                      idempotencyKeyRef.current = null
                    }}
                    disabled={isSubmitting}
                  >
                    <option value="INCIDENT">Incident (something is broken)</option>
                    <option value="SERVICE_REQUEST">Service request (request new access/hardware)</option>
                  </select>
                </div>
              </div>

              {/* Urgency / Initial Priority */}
              <div className="horizon-form-field">
                <label htmlFor="ticket-priority-select" className="horizon-label">
                  Urgency / Priority
                </label>
                <div className="horizon-select-wrapper">
                  <select
                    id="ticket-priority-select"
                    className="horizon-select"
                    value={priority}
                    onChange={(event) => {
                      setPriority(event.target.value as TicketPriority)
                      idempotencyKeyRef.current = null
                    }}
                    disabled={isSubmitting}
                  >
                    <option value="LOW">Low (minor inconvenience or cosmetic issue)</option>
                    <option value="MEDIUM">Medium (standard business impact)</option>
                    <option value="HIGH">High (significant impact on daily work)</option>
                  </select>
                </div>
                <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', marginTop: '0.25rem', display: 'block' }}>
                  Critical priorities are reserved for major system outages triaged directly by support staff.
                </span>
              </div>

              {/* Title Input */}
              <div className="horizon-form-field">
                <label htmlFor="ticket-title-input" className="horizon-label">
                  Title
                </label>
                <div className="horizon-input-wrapper">
                  <input
                    id="ticket-title-input"
                    type="text"
                    className="horizon-input"
                    value={title}
                    onChange={(event) => {
                      setTitle(event.target.value)
                      idempotencyKeyRef.current = null
                    }}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && (isSubmitting || isSubmittingRef.current)) {
                        e.preventDefault()
                      }
                    }}
                    maxLength={200}
                    placeholder="Briefly summarize the issue (e.g. VPN not connecting after update)"
                    disabled={isSubmitting}
                    required
                  />
                </div>
              </div>

              {/* Description Textarea */}
              <div className="horizon-form-field">
                <label htmlFor="ticket-description-input" className="horizon-label">
                  Description
                </label>
                <div className="horizon-input-wrapper">
                  <textarea
                    id="ticket-description-input"
                    className="horizon-textarea"
                    value={description}
                    onChange={(event) => {
                      setDescription(event.target.value)
                      idempotencyKeyRef.current = null
                    }}
                    maxLength={5000}
                    placeholder="Provide details: what happened, steps to reproduce, error messages, and any troubleshooting already attempted."
                    rows={7}
                    disabled={isSubmitting}
                    required
                  />
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', marginTop: '4px' }}>
                  <span>Markdown formatting supported in comments.</span>
                  <span>{description.length}/5000</span>
                </div>
              </div>

              {/* Notice regarding file attachments */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '10px 14px', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', marginBottom: '1rem' }}>
                <Paperclip size={16} style={{ color: 'var(--color-text-muted)', flexShrink: 0 }} />
                <span>
                  <strong>Attachments:</strong> You can upload screenshots and logs (up to 10 MB, malware scanned) immediately once your ticket is created.
                </span>
              </div>

              {/* Error Alert */}
              {error && (
                <div className="form-error horizon-auth-alert horizon-auth-alert--error" role="alert">
                  <AlertCircle size={18} className="horizon-auth-alert-icon" />
                  <span>{error}</span>
                </div>
              )}

              {/* Submit Button */}
              <button
                type="submit"
                className="horizon-btn horizon-btn--primary horizon-btn--lg horizon-btn--full-width"
                disabled={isSubmitting}
                aria-busy={isSubmitting}
              >
                {isSubmitting ? 'Creating ticket...' : 'Create ticket'}
              </button>
            </form>
          </Card>

          {/* Contextual Side Panel: Knowledge Base Suggestions & Best Practices */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {/* Deflection Card: dynamic live search as user types */}
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

            {/* Helper Guidance Card */}
            <Card padded>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                <HelpCircle size={18} style={{ color: 'var(--color-primary)' }} />
                <h3 style={{ margin: 0, fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-semibold)' }}>
                  Submission Tips
                </h3>
              </div>
              <ul style={{ margin: 0, paddingLeft: '1.2rem', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', lineHeight: 1.6 }}>
                <li>Include exact error messages where available.</li>
                <li>State whether the issue affects only you or your entire team.</li>
                <li>Tickets are routed to support engineers based on priority.</li>
                <li>All uploaded attachments are automatically scanned for malware with ClamAV.</li>
              </ul>
            </Card>
          </div>
        </div>
      )}
    </div>
  )
}
