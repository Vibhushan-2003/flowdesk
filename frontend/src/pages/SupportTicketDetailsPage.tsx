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
  generateAiReplyDraft,
} from '../api/aiReplyDraft'

import {
  generateAiResolutionSummary,
} from '../api/aiResolutionSummary'

import {
  addSupportTicketComment,
  getSupportTicket,
  getSupportTicketComments,
  resolveSupportTicket,
  updateSupportTicketStatus,
} from '../api/ticket'

import { useAuth } from '../auth/useAuth'

import { AiTriageCard } from '../components/AiTriageCard'

import type {
  SlaStatus,
  SupportTicketResponse,
  TicketCommentResponse,
  TicketStatus,
} from '../types/ticket'

function formatLabel(value: string) {
  return value.replaceAll('_', ' ')
}

function formatDate(value: string) {
  return new Date(value).toLocaleString()
}

function formatOptionalDate(
  value: string | null,
) {
  return value
    ? formatDate(value)
    : 'Not yet'
}

function formatSlaStatus(
  status: SlaStatus,
) {
  switch (status) {
    case 'MET':
      return 'Met'

    case 'BREACHED':
      return 'Breached'

    case 'PENDING':
      return 'Pending'

    case 'UNKNOWN':
      return 'Unknown'
  }
}

function getStatusClass(status: string) {
  return `support-status support-status-${status
    .toLowerCase()
    .replaceAll('_', '-')}`
}

function getActionMessage(
  status: TicketStatus,
) {
  switch (status) {
    case 'IN_PROGRESS':
      return 'Ticket is now in progress.'

    case 'WAITING_FOR_USER':
      return 'Ticket is now waiting for the requester.'

    default:
      return 'Ticket status updated successfully.'
  }
}

export function SupportTicketDetailsPage() {
  const { accessToken } = useAuth()

  const { ticketNumber } = useParams()

  const navigate = useNavigate()

  const [ticket, setTicket] =
    useState<SupportTicketResponse | null>(
      null,
    )

  const [comments, setComments] =
    useState<TicketCommentResponse[]>([])

  const [commentBody, setCommentBody] =
    useState('')

  const [
    resolutionSummary,
    setResolutionSummary,
  ] = useState('')

  const [isLoading, setIsLoading] =
    useState(true)

  const [isUpdating, setIsUpdating] =
    useState(false)

  const [
    isResolving,
    setIsResolving,
  ] = useState(false)

  const [isSendingComment, setIsSendingComment] =
    useState(false)

  const [
    isGeneratingReplyDraft,
    setIsGeneratingReplyDraft,
  ] =
    useState(false)

  const [
    isGeneratingResolutionSummary,
    setIsGeneratingResolutionSummary,
  ] =
    useState(false)

  const [error, setError] =
    useState<string | null>(null)

  const [commentError, setCommentError] =
    useState<string | null>(null)

  const [
    resolutionError,
    setResolutionError,
  ] =
    useState<string | null>(null)

  const [
    aiReplyDraftError,
    setAiReplyDraftError,
  ] =
    useState<string | null>(null)

  const [
    aiReplyDraftNotice,
    setAiReplyDraftNotice,
  ] =
    useState<string | null>(null)

  const [
    aiResolutionSummaryError,
    setAiResolutionSummaryError,
  ] =
    useState<string | null>(null)

  const [
    aiResolutionSummaryNotice,
    setAiResolutionSummaryNotice,
  ] =
    useState<string | null>(null)

  const [successMessage, setSuccessMessage] =
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
          setError(
            'Ticket number is missing',
          )

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
          getSupportTicket(
            accessToken,
            ticketNumber,
          ),
          getSupportTicketComments(
            accessToken,
            ticketNumber,
          ),
        ])

        if (!cancelled) {
          setTicket(ticketResponse)

          setComments(
            commentsResponse,
          )

          setResolutionSummary(
            ticketResponse.resolutionSummary ?? '',
          )
        }
      } catch (caughtError) {
        if (cancelled) {
          return
        }

        if (caughtError instanceof Error) {
          setError(
            caughtError.message,
          )
        } else {
          setError(
            'Unable to load support ticket',
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
  }, [
    accessToken,
    ticketNumber,
  ])

  async function handleAiTriageApplied() {
    if (!accessToken || !ticketNumber) {
      throw new Error(
        'Your session is no longer valid',
      )
    }

    const refreshedTicket =
      await getSupportTicket(
        accessToken,
        ticketNumber,
      )

    setTicket(
      refreshedTicket,
    )

    setSuccessMessage(
      `AI recommendation applied. Ticket priority is now ${refreshedTicket.priority}.`,
    )
  }

  async function handleStatusChange(
    nextStatus: TicketStatus,
  ) {
    if (!accessToken || !ticketNumber) {
      setError(
        'Your session is no longer valid',
      )

      return
    }

    setIsUpdating(true)
    setError(null)
    setSuccessMessage(null)

    try {
      const response =
        await updateSupportTicketStatus(
          accessToken,
          ticketNumber,
          nextStatus,
        )

      setTicket(response)

      setSuccessMessage(
        getActionMessage(
          response.status,
        ),
      )
    } catch (caughtError) {
      if (caughtError instanceof Error) {
        setError(
          caughtError.message,
        )
      } else {
        setError(
          'Unable to update ticket status',
        )
      }
    } finally {
      setIsUpdating(false)
    }
  }

  async function handleGenerateAiReplyDraft() {
    if (!accessToken || !ticketNumber) {
      setAiReplyDraftError(
        'Your session is no longer valid',
      )

      return
    }

    /*
     * Never overwrite something that the engineer
     * has already typed or edited.
     */
    if (commentBody.trim()) {
      setAiReplyDraftError(
        'Clear the current message before generating a new AI draft',
      )

      return
    }

    setIsGeneratingReplyDraft(true)
    setAiReplyDraftError(null)
    setAiReplyDraftNotice(null)
    setCommentError(null)

    try {
      const response =
        await generateAiReplyDraft(
          accessToken,
          ticketNumber,
        )

      /*
       * The AI result only fills the normal editable
       * message textarea.
       *
       * This does NOT send or persist a comment.
       */
      setCommentBody(
        response.draft,
      )

      setAiReplyDraftNotice(
        `AI draft generated with ${response.model}. Review and edit it before sending.`,
      )
    } catch (caughtError) {
      if (caughtError instanceof Error) {
        setAiReplyDraftError(
          caughtError.message,
        )
      } else {
        setAiReplyDraftError(
          'Unable to generate AI reply draft',
        )
      }
    } finally {
      setIsGeneratingReplyDraft(false)
    }
  }

  async function
  handleGenerateAiResolutionSummary() {
    if (!accessToken || !ticketNumber) {
      setAiResolutionSummaryError(
        'Your session is no longer valid',
      )

      return
    }

    if (ticket?.status !== 'IN_PROGRESS') {
      setAiResolutionSummaryError(
        'AI resolution summaries can only be generated while the ticket is in progress',
      )

      return
    }

    /*
     * Never overwrite a manual summary or an
     * AI draft that the engineer has edited.
     */
    if (resolutionSummary.trim()) {
      setAiResolutionSummaryError(
        'Clear the current resolution summary before generating a new AI draft',
      )

      return
    }

    setIsGeneratingResolutionSummary(true)
    setAiResolutionSummaryError(null)
    setAiResolutionSummaryNotice(null)
    setResolutionError(null)

    try {
      const response =
        await generateAiResolutionSummary(
          accessToken,
          ticketNumber,
        )

      /*
       * AI only fills this editable textarea.
       *
       * Nothing is persisted and the ticket
       * remains IN_PROGRESS.
       */
      setResolutionSummary(
        response.draft,
      )

      setAiResolutionSummaryNotice(
        `AI draft generated with ${response.model}. Review and edit it before resolving the ticket.`,
      )
    } catch (caughtError) {
      if (caughtError instanceof Error) {
        setAiResolutionSummaryError(
          caughtError.message,
        )
      } else {
        setAiResolutionSummaryError(
          'Unable to generate AI resolution summary',
        )
      }
    } finally {
      setIsGeneratingResolutionSummary(false)
    }
  }

  async function handleResolveTicket() {
    if (!accessToken || !ticketNumber) {
      setResolutionError(
        'Your session is no longer valid',
      )

      return
    }

    if (ticket?.status !== 'IN_PROGRESS') {
      setResolutionError(
        'This ticket can only be resolved while it is in progress',
      )

      return
    }

    const normalizedSummary =
      resolutionSummary.trim()

    if (!normalizedSummary) {
      setResolutionError(
        'Enter a resolution summary before resolving the ticket',
      )

      return
    }

    if (normalizedSummary.length > 4000) {
      setResolutionError(
        'Resolution summary must not exceed 4000 characters',
      )

      return
    }

    setIsResolving(true)
    setResolutionError(null)
    setAiResolutionSummaryError(null)
    setSuccessMessage(null)

    try {
      const response =
        await resolveSupportTicket(
          accessToken,
          ticketNumber,
          normalizedSummary,
        )

      setTicket(response)

      setResolutionSummary(
        response.resolutionSummary ??
          normalizedSummary,
      )

      setAiResolutionSummaryNotice(null)

      setSuccessMessage(
        'Ticket has been resolved successfully.',
      )
    } catch (caughtError) {
      if (caughtError instanceof Error) {
        setResolutionError(
          caughtError.message,
        )
      } else {
        setResolutionError(
          'Unable to resolve ticket',
        )
      }
    } finally {
      setIsResolving(false)
    }
  }

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
        await addSupportTicketComment(
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
      setAiReplyDraftNotice(null)
      setAiReplyDraftError(null)
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

  const hasDraftableMessage =
    Boolean(
      commentBody.trim(),
    )

  const hasResolutionSummary =
    Boolean(
      resolutionSummary.trim(),
    )

  return (
    <main className="support-ticket-details-page">
      <header className="support-page-header">
        <div>
          <p className="eyebrow">
            FlowDesk Operations
          </p>

          <h1>Support ticket</h1>

          <p className="support-page-description">
            Review the requester&apos;s issue and
            move the ticket through the support
            workflow.
          </p>
        </div>

        <div className="support-header-actions">
          <button
            type="button"
            className="secondary-button"
            onClick={() =>
              navigate('/support/tickets')
            }
          >
            My assignments
          </button>

          <button
            type="button"
            className="secondary-button"
            onClick={() =>
              navigate('/support/queue')
            }
          >
            Support queue
          </button>
        </div>
      </header>

      {error && (
        <div
          className="support-alert support-alert-error"
          role="alert"
        >
          {error}
        </div>
      )}

      {successMessage && (
        <div
          className="support-alert support-alert-success"
          aria-live="polite"
        >
          {successMessage}
        </div>
      )}

      {isLoading && (
        <section className="support-loading-state">
          <div className="support-loading-dot" />

          <p>
            Loading support ticket...
          </p>
        </section>
      )}

      {!isLoading &&
        !ticket &&
        error && (
          <section className="support-empty-state">
            <h2>
              Ticket unavailable
            </h2>

            <p>
              This ticket may no longer be assigned
              to you.
            </p>

            <button
              type="button"
              className="primary-button"
              onClick={() =>
                navigate('/support/tickets')
              }
            >
              Back to my assignments
            </button>
          </section>
        )}

      {!isLoading && ticket && (
        <>
          <div className="support-ticket-layout">
            <article className="support-ticket-detail-card">
              <header className="support-ticket-detail-heading">
                <div>
                  <p className="support-ticket-number">
                    {ticket.ticketNumber}
                  </p>

                  <h2>
                    {ticket.title}
                  </h2>
                </div>

                <span
                  className={getStatusClass(
                    ticket.status,
                  )}
                >
                  {formatLabel(
                    ticket.status,
                  )}
                </span>
              </header>

              <section className="support-description-section">
                <p className="support-section-label">
                  Issue description
                </p>

                <p className="support-ticket-description-text">
                  {ticket.description}
                </p>
              </section>

              {!isConversationReadOnly && (
                <AiTriageCard
                  accessToken={accessToken}
                  ticketNumber={
                    ticket.ticketNumber
                  }
                  currentPriority={
                    ticket.priority
                  }
                  ticketStatus={
                    ticket.status
                  }
                  onApplied={
                    handleAiTriageApplied
                  }
                />
              )}

              <section className="support-detail-grid">
                <div>
                  <span>Type</span>

                  <strong>
                    {formatLabel(
                      ticket.type,
                    )}
                  </strong>
                </div>

                <div>
                  <span>Priority</span>

                  <strong>
                    {ticket.priority}
                  </strong>
                </div>

                <div>
                  <span>Requester</span>

                  <strong>
                    {ticket.createdByEmail}
                  </strong>
                </div>

                <div>
                  <span>Created</span>

                  <strong>
                    {formatDate(
                      ticket.createdAt,
                    )}
                  </strong>
                </div>

                <div>
                  <span>Assigned</span>

                  <strong>
                    {formatDate(
                      ticket.assignedAt,
                    )}
                  </strong>
                </div>

                <div>
                  <span>
                    Last updated
                  </span>

                  <strong>
                    {formatDate(
                      ticket.updatedAt,
                    )}
                  </strong>
                </div>

                {ticket.resolvedAt && (
                  <div>
                    <span>Resolved</span>

                    <strong>
                      {formatDate(
                        ticket.resolvedAt,
                      )}
                    </strong>
                  </div>
                )}
              </section>

              <section className="support-description-section">
                <p className="support-section-label">
                  SLA tracking
                </p>

                <div className="support-detail-grid">
                  <div>
                    <span>Response SLA</span>

                    <strong>
                      {formatSlaStatus(
                        ticket.responseSlaStatus,
                      )}
                    </strong>
                  </div>

                  <div>
                    <span>Response deadline</span>

                    <strong>
                      {formatDate(
                        ticket.responseDueAt,
                      )}
                    </strong>
                  </div>

                  <div>
                    <span>First response</span>

                    <strong>
                      {formatOptionalDate(
                        ticket.firstRespondedAt,
                      )}
                    </strong>
                  </div>

                  <div>
                    <span>Resolution SLA</span>

                    <strong>
                      {formatSlaStatus(
                        ticket.resolutionSlaStatus,
                      )}
                    </strong>
                  </div>

                  <div>
                    <span>Resolution deadline</span>

                    <strong>
                      {formatDate(
                        ticket.resolutionDueAt,
                      )}
                    </strong>
                  </div>

                  <div>
                    <span>Resolved</span>

                    <strong>
                      {formatOptionalDate(
                        ticket.resolvedAt,
                      )}
                    </strong>
                  </div>
                </div>
              </section>

              {ticket.resolutionSummary && (
                <section className="support-description-section">
                  <p className="support-section-label">
                    Resolution
                  </p>

                  <p className="support-ticket-description-text">
                    {ticket.resolutionSummary}
                  </p>
                </section>
              )}
            </article>

            <aside className="support-workflow-panel">
              <p className="support-section-label">
                Workflow
              </p>

              <h2>Next action</h2>

              {ticket.status ===
                'ASSIGNED' && (
                <>
                  <p>
                    The ticket is assigned to you
                    but work has not started yet.
                  </p>

                  <button
                    type="button"
                    className="primary-button support-workflow-button"
                    disabled={
                      isUpdating
                    }
                    onClick={() =>
                      void handleStatusChange(
                        'IN_PROGRESS',
                      )
                    }
                  >
                    {isUpdating
                      ? 'Starting...'
                      : 'Start work'}
                  </button>
                </>
              )}

              {ticket.status ===
                'IN_PROGRESS' && (
                <>
                  <p>
                    You are actively working on
                    this ticket.
                  </p>

                  <button
                    type="button"
                    className="secondary-button support-workflow-button"
                    disabled={
                      isUpdating ||
                      isResolving ||
                      isGeneratingResolutionSummary
                    }
                    onClick={() =>
                      void handleStatusChange(
                        'WAITING_FOR_USER',
                      )
                    }
                  >
                    {isUpdating
                      ? 'Updating...'
                      : 'Wait for user'}
                  </button>

                  <div className="resolution-workflow">
                    <p className="support-section-label">
                      Resolution summary
                    </p>

                    <h3>
                      Resolve ticket
                    </h3>

                    <p className="resolution-workflow-description">
                      Record what was actually done and
                      the verified outcome. You can write
                      this manually or ask AI to prepare
                      an editable draft.
                    </p>

                    <button
                      type="button"
                      className="secondary-button support-workflow-button"
                      disabled={
                        isGeneratingResolutionSummary ||
                        isResolving ||
                        isUpdating ||
                        hasResolutionSummary
                      }
                      onClick={() =>
                        void handleGenerateAiResolutionSummary()
                      }
                    >
                      {isGeneratingResolutionSummary
                        ? 'Drafting...'
                        : 'Draft summary with AI'}
                    </button>

                    {aiResolutionSummaryNotice && (
                      <p
                        className="resolution-ai-notice"
                        aria-live="polite"
                      >
                        {aiResolutionSummaryNotice}
                      </p>
                    )}

                    {aiResolutionSummaryError && (
                      <p
                        className="form-error"
                        role="alert"
                      >
                        {aiResolutionSummaryError}
                      </p>
                    )}

                    <label
                      className="resolution-summary-label"
                      htmlFor="resolution-summary"
                    >
                      Final resolution summary
                    </label>

                    <textarea
                      id="resolution-summary"
                      value={resolutionSummary}
                      maxLength={4000}
                      rows={7}
                      placeholder="Describe the verified action taken and outcome..."
                      disabled={
                        isResolving ||
                        isGeneratingResolutionSummary ||
                        isUpdating
                      }
                      onChange={(event) => {
                        setResolutionSummary(
                          event.target.value,
                        )

                        if (resolutionError) {
                          setResolutionError(
                            null,
                          )
                        }

                        if (
                          aiResolutionSummaryError
                        ) {
                          setAiResolutionSummaryError(
                            null,
                          )
                        }
                      }}
                    />

                    <div className="resolution-summary-footer">
                      <span>
                        {resolutionSummary.length}
                        /4000
                      </span>
                    </div>

                    {resolutionError && (
                      <p
                        className="form-error"
                        role="alert"
                      >
                        {resolutionError}
                      </p>
                    )}

                    <button
                      type="button"
                      className="resolve-button support-workflow-button"
                      disabled={
                        isResolving ||
                        isUpdating ||
                        isGeneratingResolutionSummary ||
                        !hasResolutionSummary
                      }
                      onClick={() =>
                        void handleResolveTicket()
                      }
                    >
                      {isResolving
                        ? 'Resolving...'
                        : 'Resolve ticket'}
                    </button>
                  </div>
                </>
              )}

              {ticket.status ===
                'WAITING_FOR_USER' && (
                <>
                  <p>
                    Work is paused while waiting
                    for information from the
                    requester.
                  </p>

                  <button
                    type="button"
                    className="primary-button support-workflow-button"
                    disabled={
                      isUpdating
                    }
                    onClick={() =>
                      void handleStatusChange(
                        'IN_PROGRESS',
                      )
                    }
                  >
                    {isUpdating
                      ? 'Resuming...'
                      : 'Resume work'}
                  </button>
                </>
              )}

              {ticket.status ===
                'RESOLVED' && (
                <div className="resolved-workflow-state">
                  <div className="resolved-workflow-icon">
                    ✓
                  </div>

                  <h3>
                    Ticket resolved
                  </h3>

                  <p>
                    This ticket has been completed
                    and removed from your active
                    assignments.
                  </p>

                  {ticket.resolutionSummary && (
                    <div className="resolved-summary-box">
                      <strong>
                        Resolution summary
                      </strong>

                      <p>
                        {
                          ticket.resolutionSummary
                        }
                      </p>
                    </div>
                  )}

                  <button
                    type="button"
                    className="primary-button support-workflow-button"
                    onClick={() =>
                      navigate(
                        '/support/tickets',
                      )
                    }
                  >
                    Back to my assignments
                  </button>
                </div>
              )}

              <div className="workflow-rule">
                <span>
                  Current status
                </span>

                <strong>
                  {formatLabel(
                    ticket.status,
                  )}
                </strong>
              </div>
            </aside>
          </div>

          <section className="support-conversation-card">
            <header className="ticket-conversation-header">
              <div>
                <p className="support-section-label">
                  Conversation
                </p>

                <h2>
                  Requester messages
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
                    Ask the requester for more
                    information when needed.
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

                  <p>
                    {comment.body}
                  </p>
                </article>
              ))}
            </div>

            {isConversationReadOnly ? (
              <div className="ticket-conversation-readonly">
                <strong>
                  Conversation closed
                </strong>

                <p>
                  The ticket is resolved. Existing
                  messages remain visible, but new
                  messages cannot be added.
                </p>
              </div>
            ) : (
              <form
                className="ticket-comment-form"
                onSubmit={
                  handleSendComment
                }
              >
                <label htmlFor="support-comment-body">
                  Message requester
                </label>

                <div className="ticket-comment-form-footer">
                  <span>
                    AI can prepare an editable draft.
                    It is never sent automatically.
                  </span>

                  <button
                    type="button"
                    disabled={
                      isGeneratingReplyDraft ||
                      isSendingComment ||
                      hasDraftableMessage
                    }
                    onClick={() =>
                      void handleGenerateAiReplyDraft()
                    }
                  >
                    {isGeneratingReplyDraft
                      ? 'Drafting...'
                      : 'Draft reply with AI'}
                  </button>
                </div>

                {aiReplyDraftNotice && (
                  <p aria-live="polite">
                    {aiReplyDraftNotice}
                  </p>
                )}

                {aiReplyDraftError && (
                  <p
                    className="form-error"
                    role="alert"
                  >
                    {aiReplyDraftError}
                  </p>
                )}

                <textarea
                  id="support-comment-body"
                  value={commentBody}
                  maxLength={4000}
                  rows={4}
                  placeholder="Ask a question or send an update..."
                  disabled={
                    isSendingComment ||
                    isGeneratingReplyDraft
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

                    if (aiReplyDraftError) {
                      setAiReplyDraftError(
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
                    className="primary-button"
                    disabled={
                      isSendingComment ||
                      isGeneratingReplyDraft ||
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