import {
  type FormEvent,
  useEffect,
  useState,
} from 'react'
import {
  useNavigate,
  useParams,
} from 'react-router-dom'
import {
  ArrowLeft,
  Clock,
  AlertCircle,
  MessageSquare,
  CheckCircle2,
  XCircle,
} from 'lucide-react'

import {
  addTicketComment,
  getTicket,
  getTicketComments,
} from '../api/ticket'
import { useAuth } from '../auth/useAuth'
import type {
  TicketCommentResponse,
  TicketResponse,
} from '../types/ticket'
import {
  formatDate,
  formatLabel,
  formatOptionalDate,
  formatSlaStatus,
} from '../utils/formatters'
import { TicketAttachmentSection } from '../components/tickets/TicketAttachmentSection'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { StatusBadge } from '../components/ui/StatusBadge'
import { PriorityBadge } from '../components/ui/PriorityBadge'
import { UserAvatar } from '../components/ui/UserAvatar'
import { Timeline, TimelineItem } from '../components/ui/Timeline'

export function TicketDetailsPage() {
  const { accessToken } = useAuth()
  const { ticketNumber } = useParams()
  const navigate = useNavigate()

  const [ticket, setTicket] = useState<TicketResponse | null>(null)
  const [comments, setComments] = useState<TicketCommentResponse[]>([])
  const [commentBody, setCommentBody] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isSendingComment, setIsSendingComment] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [commentError, setCommentError] = useState<string | null>(null)

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
        const [ticketResponse, commentsResponse] = await Promise.all([
          getTicket(accessToken, ticketNumber),
          getTicketComments(accessToken, ticketNumber),
        ])

        if (!cancelled) {
          setTicket(ticketResponse)
          setComments(commentsResponse)
        }
      } catch (caughtError) {
        if (cancelled) return
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

  async function handleSendComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!accessToken || !ticketNumber) {
      setCommentError('Your session is no longer valid')
      return
    }

    const normalizedBody = commentBody.trim()

    if (!normalizedBody) {
      setCommentError('Please enter a message')
      return
    }

    if (normalizedBody.length > 4000) {
      setCommentError('Message must not exceed 4000 characters')
      return
    }

    setIsSendingComment(true)
    setCommentError(null)

    try {
      const response = await addTicketComment(accessToken, ticketNumber, {
        body: normalizedBody,
      })

      setComments((currentComments) => [...currentComments, response])
      setCommentBody('')
    } catch (caughtError) {
      if (caughtError instanceof Error) {
        setCommentError(caughtError.message)
      } else {
        setCommentError('Unable to send message')
      }
    } finally {
      setIsSendingComment(false)
    }
  }

  const isConversationReadOnly =
    ticket?.status === 'RESOLVED' ||
    ticket?.status === 'CLOSED' ||
    ticket?.status === 'CANCELLED'

  return (
    <div className="ticket-details-page horizon-ticket-details-workspace">
      {/* Header */}
      <PageHeader
        title="Ticket Details"
        subtitle={
          ticket ? (
            <span style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ fontFamily: 'var(--font-family-mono)', fontWeight: 'var(--font-weight-bold)', color: 'var(--color-primary)' }}>
                {ticket.ticketNumber}
              </span>
              <span>&bull;</span>
              <span>Created {formatDate(ticket.createdAt)}</span>
            </span>
          ) : (
            'View ticket details and conversation thread.'
          )
        }
        actions={
          <Button
            variant="secondary"
            size="sm"
            onClick={() => navigate('/tickets')}
            leadingIcon={<ArrowLeft size={14} />}
          >
            Back to my tickets
          </Button>
        }
      />

      {/* Loading feedback */}
      {isLoading && (
        <div style={{ textAlign: 'center', padding: '3rem 1rem', color: 'var(--color-text-secondary)' }}>
          <p>Loading ticket...</p>
        </div>
      )}

      {/* Error state */}
      {error && (
        <section className="ticket-error">
          <div className="form-error horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ margin: '1.5rem 0' }}>
            <AlertCircle size={18} className="horizon-auth-alert-icon" />
            <span>{error}</span>
          </div>

          <Button
            variant="secondary"
            size="md"
            onClick={() => navigate('/tickets')}
            leadingIcon={<ArrowLeft size={16} />}
          >
            Back to my tickets
          </Button>
        </section>
      )}

      {!isLoading && !error && ticket && (
        <div className="horizon-workbench-grid">
          {/* Left Column: Incident Description & Messages */}
          <div className="horizon-workbench-main">
            {/* Incident Description Card */}
            <article className="ticket-details-card horizon-card" style={{ padding: '1.5rem' }}>
              <header style={{ marginBottom: '1.25rem', paddingBottom: '1rem', borderBottom: '1px solid var(--color-divider)' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '1rem', flexWrap: 'wrap' }}>
                  <p className="ticket-number" style={{ margin: 0, fontFamily: 'var(--font-family-mono)', fontWeight: 'var(--font-weight-bold)', color: 'var(--color-primary)', fontSize: 'var(--font-size-lg)' }}>
                    {ticket.ticketNumber}
                  </p>

                  <div className="ticket-success-tags" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flexWrap: 'wrap' }}>
                    <StatusBadge status={ticket.status} size="md" />
                    <PriorityBadge priority={ticket.priority} size="md" />
                    <Badge variant="neutral" size="md">
                      {formatLabel(ticket.type)}
                    </Badge>
                  </div>
                </div>

                <h2 style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 'var(--font-weight-bold)', margin: '0.75rem 0 0', color: 'var(--color-text-primary)' }}>
                  {ticket.title}
                </h2>
              </header>

              {/* Description */}
              <section className="ticket-description">
                <h3 style={{ fontSize: 'var(--font-size-xs)', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', margin: '0 0 0.5rem' }}>
                  Description
                </h3>
                <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', lineHeight: 'var(--line-height-relaxed)', color: 'var(--color-text-primary)', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                  {ticket.description}
                </p>
              </section>

              {/* Resolution Summary (when resolved) */}
              {ticket.resolutionSummary?.trim() && (
                <section className="ticket-description" aria-label="Resolution summary" style={{ marginTop: '1.5rem', padding: '1rem 1.25rem', background: 'var(--color-success-subtle)', border: '1px solid var(--color-success-border)', borderRadius: 'var(--radius-md)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '6px' }}>
                    <CheckCircle2 size={16} style={{ color: 'var(--color-success)' }} />
                    <h3 style={{ margin: 0, fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-success)' }}>
                      Resolution summary
                    </h3>
                  </div>
                  <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', color: 'var(--color-text-primary)', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                    {ticket.resolutionSummary}
                  </p>
                </section>
              )}

              {/* Cancellation Reason (when cancelled) */}
              {ticket.status === 'CANCELLED' && (
                <section className="ticket-rejection-card" aria-label="Rejection details" style={{ marginTop: '1.5rem', padding: '1rem 1.25rem', background: 'var(--color-danger-subtle)', border: '1px solid var(--color-danger-border)', borderRadius: 'var(--radius-md)' }}>
                  <div className="ticket-rejection-header" style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
                    <XCircle size={16} style={{ color: 'var(--color-danger)' }} />
                    <h3 style={{ margin: 0, fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-danger)' }}>
                      Rejection / Cancellation Reason
                    </h3>
                  </div>
                  <p className="ticket-rejection-text" style={{ margin: 0, fontSize: 'var(--font-size-sm)', color: 'var(--color-text-primary)' }}>
                    {ticket.rejectionReason?.trim()
                      ? ticket.rejectionReason
                      : ticket.type === 'SERVICE_REQUEST'
                      ? 'No specific rejection reason was recorded.'
                      : 'This ticket was cancelled.'}
                  </p>
                </section>
              )}
            </article>

            {/* Conversation Thread */}
            <section className="ticket-conversation-card horizon-card" style={{ padding: '1.5rem' }}>
              <header className="ticket-conversation-header" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem', paddingBottom: '1rem', borderBottom: '1px solid var(--color-divider)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <MessageSquare size={18} style={{ color: 'var(--color-primary)' }} />
                  <h2 style={{ margin: 0, fontSize: 'var(--font-size-lg)', fontWeight: 'var(--font-weight-bold)' }}>
                    Ticket messages
                  </h2>
                </div>

                <Badge variant="neutral" size="sm">
                  {comments.length} {comments.length === 1 ? 'message' : 'messages'}
                </Badge>
              </header>

              <div className="ticket-comment-list" style={{ marginBottom: '1.5rem' }}>
                {comments.length === 0 ? (
                  <div className="ticket-conversation-empty" style={{ textAlign: 'center', padding: '2.5rem 1rem', color: 'var(--color-text-secondary)', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', border: '1px dashed var(--color-border)' }}>
                    <p style={{ margin: '0 0 4px', fontWeight: 'var(--font-weight-medium)' }}>No messages yet.</p>
                    <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
                      Messages between you and the support engineer will appear here.
                    </span>
                  </div>
                ) : (
                  <Timeline>
                    {comments.map((comment) => (
                      <TimelineItem
                        key={comment.commentId}
                        authorName={comment.authorName}
                        timestamp={formatDate(comment.createdAt)}
                        avatar={<UserAvatar email={comment.authorName} size="sm" />}
                      >
                        {comment.body}
                      </TimelineItem>
                    ))}
                  </Timeline>
                )}
              </div>

              {/* Conversation Status: Read-Only or Active Composer */}
              {isConversationReadOnly ? (
                <div className="ticket-conversation-readonly" style={{ padding: '1rem', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)', textAlign: 'center' }}>
                  <strong style={{ display: 'block', color: 'var(--color-text-primary)', marginBottom: '4px' }}>
                    Conversation closed
                  </strong>
                  <p style={{ margin: 0, fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
                    This ticket has concluded. You can still read the discussion, but additional messages cannot be posted.
                  </p>
                </div>
              ) : (
                <form className="ticket-comment-form horizon-auth-form" onSubmit={handleSendComment}>
                  <div className="horizon-form-field">
                    <label htmlFor="ticket-comment-body" className="horizon-label">
                      Reply to support
                    </label>
                    <div className="horizon-input-wrapper">
                      <textarea
                        id="ticket-comment-body"
                        className="horizon-textarea"
                        value={commentBody}
                        maxLength={4000}
                        rows={4}
                        placeholder="Write a message or provide additional troubleshooting info..."
                        disabled={isSendingComment}
                        onChange={(event) => {
                          setCommentBody(event.target.value)
                          if (commentError) setCommentError(null)
                        }}
                      />
                    </div>
                  </div>

                  <div className="ticket-comment-form-footer" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '1rem', marginTop: '0.5rem' }}>
                    <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
                      {commentBody.length}/4000
                    </span>

                    <button
                      type="submit"
                      className="horizon-btn horizon-btn--primary horizon-btn--md"
                      disabled={isSendingComment || !commentBody.trim()}
                    >
                      {isSendingComment ? 'Sending...' : 'Send message'}
                    </button>
                  </div>

                  {commentError && (
                    <div className="form-error horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ marginTop: '0.75rem' }}>
                      <AlertCircle size={16} className="horizon-auth-alert-icon" />
                      <span>{commentError}</span>
                    </div>
                  )}
                </form>
              )}
            </section>
          </div>

          {/* Right Column: Metadata, SLA, Attachments */}
          <div className="horizon-workbench-sidebar">
            {/* Ticket Metadata Card */}
            <div className="horizon-context-card">
              <div className="horizon-context-header">
                <h4 className="horizon-context-title">Ticket Information</h4>
                <Badge variant="neutral" size="sm">
                  {ticket.ticketNumber}
                </Badge>
              </div>

              <div className="horizon-context-row">
                <span className="horizon-context-row-label">Ticket Type</span>
                <span className="horizon-context-row-value">{formatLabel(ticket.type)}</span>
              </div>

              <div className="horizon-context-row">
                <span className="horizon-context-row-label">Priority</span>
                <span className="horizon-context-row-value">
                  <PriorityBadge priority={ticket.priority} size="sm" />
                </span>
              </div>

              <div className="horizon-context-row">
                <span className="horizon-context-row-label">Status</span>
                <span className="horizon-context-row-value">
                  <StatusBadge status={ticket.status} size="sm" />
                </span>
              </div>

              <div className="horizon-context-row">
                <span className="horizon-context-row-label">Created By</span>
                <span className="horizon-context-row-value">{ticket.createdByEmail}</span>
              </div>

              <div className="horizon-context-row">
                <span className="horizon-context-row-label">Created</span>
                <span className="horizon-context-row-value">{formatDate(ticket.createdAt)}</span>
              </div>

              <div className="horizon-context-row">
                <span className="horizon-context-row-label">Last Updated</span>
                <span className="horizon-context-row-value">{formatDate(ticket.updatedAt)}</span>
              </div>
            </div>

            {/* SLA Tracking Card */}
            <div className="horizon-context-card">
              <div className="horizon-context-header">
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <Clock size={15} style={{ color: 'var(--color-primary)' }} />
                  <h4 className="horizon-context-title">SLA Tracking</h4>
                </div>
              </div>

              <div className="horizon-context-row">
                <span className="horizon-context-row-label">Response SLA</span>
                <span className="horizon-context-row-value">
                  <Badge variant={ticket.responseSlaStatus === 'BREACHED' ? 'danger' : 'success'} size="sm" dot>
                    {formatSlaStatus(ticket.responseSlaStatus)}
                  </Badge>
                </span>
              </div>

              <div className="horizon-context-row">
                <span className="horizon-context-row-label">Response Deadline</span>
                <span className="horizon-context-row-value">{formatDate(ticket.responseDueAt)}</span>
              </div>

              <div className="horizon-context-row">
                <span className="horizon-context-row-label">First Response</span>
                <span className="horizon-context-row-value">{formatOptionalDate(ticket.firstRespondedAt)}</span>
              </div>

              <div className="horizon-context-row">
                <span className="horizon-context-row-label">Resolution SLA</span>
                <span className="horizon-context-row-value">
                  <Badge variant={ticket.resolutionSlaStatus === 'BREACHED' ? 'danger' : 'success'} size="sm" dot>
                    {formatSlaStatus(ticket.resolutionSlaStatus)}
                  </Badge>
                </span>
              </div>

              <div className="horizon-context-row">
                <span className="horizon-context-row-label">Resolution Deadline</span>
                <span className="horizon-context-row-value">{formatDate(ticket.resolutionDueAt)}</span>
              </div>

              <div className="horizon-context-row">
                <span className="horizon-context-row-label">Resolved</span>
                <span className="horizon-context-row-value">{formatOptionalDate(ticket.resolvedAt)}</span>
              </div>
            </div>

            {/* Attachments Section */}
            <div className="horizon-context-card" style={{ padding: '1rem' }}>
              <TicketAttachmentSection
                ticketNumber={ticket.ticketNumber}
                ticketStatus={ticket.status}
                persona="employee"
              />
            </div>
          </div>
        </div>
      )}
    </div>
  )
}