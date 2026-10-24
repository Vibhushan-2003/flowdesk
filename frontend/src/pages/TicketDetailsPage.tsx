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

export function TicketDetailsPage() {
  const { accessToken } = useAuth()
  const { ticketNumber } = useParams()
  const navigate = useNavigate()

  const [ticket, setTicket] =
    useState<TicketResponse | null>(null)

  const [comments, setComments] =
    useState<TicketCommentResponse[]>([])

  const [commentBody, setCommentBody] =
    useState('')

  const [isLoading, setIsLoading] =
    useState(true)

  const [isSendingComment, setIsSendingComment] =
    useState(false)

  const [error, setError] =
    useState<string | null>(null)

  const [commentError, setCommentError] =
    useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadTicket() {
      if (!accessToken) {
        if (!cancelled) {
          setError(
            'Your session is no longer valid',
          )

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
        const [
          ticketResponse,
          commentsResponse,
        ] = await Promise.all([
          getTicket(
            accessToken,
            ticketNumber,
          ),
          getTicketComments(
            accessToken,
            ticketNumber,
          ),
        ])

        if (!cancelled) {
          setTicket(ticketResponse)
          setComments(commentsResponse)
        }
      } catch (caughtError) {
        if (cancelled) {
          return
        }

        if (caughtError instanceof Error) {
          setError(caughtError.message)
        } else {
          setError(
            'Unable to load ticket',
          )
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

  async function handleSendComment(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    if (!accessToken || !ticketNumber) {
      setCommentError(
        'Your session is no longer valid',
      )

      return
    }

    const normalizedBody =
      commentBody.trim()

    if (!normalizedBody) {
      setCommentError(
        'Please enter a message',
      )

      return
    }

    if (normalizedBody.length > 4000) {
      setCommentError(
        'Message must not exceed 4000 characters',
      )

      return
    }

    setIsSendingComment(true)
    setCommentError(null)

    try {
      const response =
        await addTicketComment(
          accessToken,
          ticketNumber,
          {
            body: normalizedBody,
          },
        )

      setComments((currentComments) => [
        ...currentComments,
        response,
      ])

      setCommentBody('')
    } catch (caughtError) {
      if (caughtError instanceof Error) {
        setCommentError(
          caughtError.message,
        )
      } else {
        setCommentError(
          'Unable to send message',
        )
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
    <main className="ticket-details-page">
      <header className="ticket-details-header">
        <div>
          <p className="eyebrow">FlowDesk</p>

          <h1>Ticket details</h1>
        </div>

        <button
          type="button"
          onClick={() =>
            navigate('/tickets')
          }
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
            onClick={() =>
              navigate('/tickets')
            }
          >
            Back to my tickets
          </button>
        </section>
      )}

      {!isLoading &&
        !error &&
        ticket && (
          <>
            <article className="ticket-details-card">
              <header>
                <p className="ticket-number">
                  {ticket.ticketNumber}
                </p>

                <h2>{ticket.title}</h2>

                <div className="ticket-success-tags" style={{ marginTop: '8px' }}>
                  <span className={`support-status support-status-${ticket.status.toLowerCase().replaceAll('_', '-')}`}>
                    {formatLabel(ticket.status)}
                  </span>
                  <span className={`priority-badge priority-badge-${ticket.priority.toLowerCase()}`}>
                    {formatLabel(ticket.priority)}
                  </span>
                  <span className="priority-badge priority-badge-low">
                    {formatLabel(ticket.type)}
                  </span>
                </div>
              </header>

              <section className="ticket-description">
                <h3>Description</h3>

                <p>
                  {ticket.description}
                </p>
              </section>

              <section className="ticket-details-grid">
                <div>
                  <strong>Ticket type</strong>

                  <p>
                    {formatLabel(
                      ticket.type,
                    )}
                  </p>
                </div>

                <div>
                  <strong>Priority</strong>

                  <p>
                    <span className={`priority-badge priority-badge-${ticket.priority.toLowerCase()}`}>
                      {formatLabel(ticket.priority)}
                    </span>
                  </p>
                </div>

                <div>
                  <strong>Status</strong>

                  <p>
                    <span className={`support-status support-status-${ticket.status.toLowerCase().replaceAll('_', '-')}`}>
                      {formatLabel(ticket.status)}
                    </span>
                  </p>
                </div>

                <div>
                  <strong>Created by</strong>

                  <p>
                    {ticket.createdByEmail}
                  </p>
                </div>

                <div>
                  <strong>Created</strong>

                  <p>
                    {formatDate(
                      ticket.createdAt,
                    )}
                  </p>
                </div>

                <div>
                  <strong>
                    Last updated
                  </strong>

                  <p>
                    {formatDate(
                      ticket.updatedAt,
                    )}
                  </p>
                </div>
              </section>

              <section className="ticket-description">
                <h3>SLA tracking</h3>

                <p>
                  Service targets for the first support
                  response and final resolution.
                </p>

                <div className="ticket-details-grid">
                  <div>
                    <strong>Response SLA</strong>

                    <p>
                      {formatSlaStatus(
                        ticket.responseSlaStatus,
                      )}
                    </p>
                  </div>

                  <div>
                    <strong>Response deadline</strong>

                    <p>
                      {formatDate(
                        ticket.responseDueAt,
                      )}
                    </p>
                  </div>

                  <div>
                    <strong>First response</strong>

                    <p>
                      {formatOptionalDate(
                        ticket.firstRespondedAt,
                      )}
                    </p>
                  </div>

                  <div>
                    <strong>Resolution SLA</strong>

                    <p>
                      {formatSlaStatus(
                        ticket.resolutionSlaStatus,
                      )}
                    </p>
                  </div>

                  <div>
                    <strong>Resolution deadline</strong>

                    <p>
                      {formatDate(
                        ticket.resolutionDueAt,
                      )}
                    </p>
                  </div>

                  <div>
                    <strong>Resolved</strong>

                    <p>
                      {formatOptionalDate(
                        ticket.resolvedAt,
                      )}
                    </p>
                  </div>
                </div>
              </section>

              {ticket.resolutionSummary?.trim() && (
                <section
                  className="ticket-description"
                  aria-label="Resolution summary"
                >
                  <h3>Resolution summary</h3>

                  <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                    {ticket.resolutionSummary}
                  </p>
                </section>
              )}

              {ticket.status === 'CANCELLED' && (
                <section
                  className="ticket-rejection-card"
                  aria-label="Rejection details"
                >
                  <div className="ticket-rejection-header">
                    <span className="support-status support-status-cancelled">
                      Cancelled
                    </span>
                    <h3>Manager Rejection Reason</h3>
                  </div>

                  <p className="ticket-rejection-text">
                    {ticket.rejectionReason?.trim()
                      ? ticket.rejectionReason
                      : ticket.type === 'SERVICE_REQUEST'
                        ? 'No specific rejection reason was recorded.'
                        : 'This ticket was cancelled.'}
                  </p>
                </section>
              )}
            </article>

            <section className="ticket-conversation-card">
              <header className="ticket-conversation-header">
                <div>
                  <p className="eyebrow">
                    Conversation
                  </p>

                  <h2>
                    Ticket messages
                  </h2>
                </div>

                <span>
                  {comments.length}{' '}
                  {comments.length === 1
                    ? 'message'
                    : 'messages'}
                </span>
              </header>

              <div className="ticket-comment-list">
                {comments.length === 0 && (
                  <div className="ticket-conversation-empty">
                    <p>
                      No messages yet.
                    </p>

                    <span>
                      Messages between you and the
                      support engineer will appear
                      here.
                    </span>
                  </div>
                )}

                {comments.map((comment) => (
                  <article
                    key={comment.commentId}
                    className="ticket-comment"
                  >
                    <header className="ticket-comment-header">
                      <strong>
                        {comment.authorName}
                      </strong>

                      <time
                        dateTime={
                          comment.createdAt
                        }
                      >
                        {formatDate(
                          comment.createdAt,
                        )}
                      </time>
                    </header>

                    <p>{comment.body}</p>
                  </article>
                ))}
              </div>

              {isConversationReadOnly ? (
                <div className="ticket-conversation-readonly">
                  <strong>
                    Conversation closed
                  </strong>

                  <p>
                    This ticket is finished. You can
                    still read the conversation, but
                    new messages cannot be added.
                  </p>
                </div>
              ) : (
                <form
                  className="ticket-comment-form"
                  onSubmit={
                    handleSendComment
                  }
                >
                  <label htmlFor="ticket-comment-body">
                    Reply
                  </label>

                  <textarea
                    id="ticket-comment-body"
                    value={commentBody}
                    maxLength={4000}
                    rows={4}
                    placeholder="Write a message to the support engineer..."
                    disabled={
                      isSendingComment
                    }
                    onChange={(event) => {
                      setCommentBody(
                        event.target.value,
                      )

                      if (commentError) {
                        setCommentError(
                          null,
                        )
                      }
                    }}
                  />

                  <div className="ticket-comment-form-footer">
                    <span>
                      {commentBody.length}
                      /4000
                    </span>

                    <button
                      type="submit"
                      disabled={
                        isSendingComment ||
                        !commentBody.trim()
                      }
                    >
                      {isSendingComment
                        ? 'Sending...'
                        : 'Send message'}
                    </button>
                  </div>

                  {commentError && (
                    <p
                      className="form-error"
                      role="alert"
                    >
                      {commentError}
                    </p>
                  )}
                </form>
              )}
            </section>
          </>
        )}
    </main>
  )
}