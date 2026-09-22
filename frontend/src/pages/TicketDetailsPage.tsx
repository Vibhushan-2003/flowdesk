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

                <p>
                  {formatLabel(
                    ticket.type,
                  )}
                  {' · '}
                  {ticket.priority}
                  {' · '}
                  {formatLabel(
                    ticket.status,
                  )}
                </p>
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

                  <p>{ticket.priority}</p>
                </div>

                <div>
                  <strong>Status</strong>

                  <p>
                    {formatLabel(
                      ticket.status,
                    )}
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