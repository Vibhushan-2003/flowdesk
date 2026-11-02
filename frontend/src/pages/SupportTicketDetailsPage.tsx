import {
  type FormEvent,
  useEffect,
  useState,
} from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeft,
  CheckCircle2,
  AlertCircle,
  Play,
  Pause,
  Lock,
  Globe,
  Sparkles,
  MessageSquare,
  FileCheck2,
} from 'lucide-react'

import { generateAiReplyDraft } from '../api/aiReplyDraft'
import { generateAiResolutionSummary } from '../api/aiResolutionSummary'
import {
  addSupportTicketComment,
  getSupportTicket,
  getSupportTicketComments,
  resolveSupportTicket,
  updateSupportTicketStatus,
} from '../api/ticket'
import { useAuth } from '../auth/useAuth'
import { TicketAttachmentSection } from '../components/tickets/TicketAttachmentSection'
import { AiWorkbench } from '../components/AiWorkbench'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { StatusBadge } from '../components/ui/StatusBadge'
import { PriorityBadge } from '../components/ui/PriorityBadge'
import { UserAvatar } from '../components/ui/UserAvatar'
import { Timeline, TimelineItem } from '../components/ui/Timeline'
import { Modal } from '../components/ui/Modal'

import type {
  SupportTicketResponse,
  TicketCommentResponse,
  TicketStatus,
} from '../types/ticket'
import {
  formatDate,
  formatLabel,
  formatOptionalDate,
  formatSlaStatus,
} from '../utils/formatters'

function getActionMessage(status: TicketStatus) {
  switch (status) {
    case 'IN_PROGRESS':
      return 'Ticket is now in progress.'
    case 'WAITING_FOR_USER':
      return 'Ticket is now waiting for user response.'
    default:
      return 'Ticket status updated successfully.'
  }
}

export function SupportTicketDetailsPage() {
  const { accessToken, user } = useAuth()
  const { ticketNumber } = useParams()
  const navigate = useNavigate()

  const [ticket, setTicket] = useState<SupportTicketResponse | null>(null)
  const [comments, setComments] = useState<TicketCommentResponse[]>([])
  const [commentBody, setCommentBody] = useState('')
  const [isInternalComment, setIsInternalComment] = useState(false)
  const [resolutionSummary, setResolutionSummary] = useState('')

  const [isLoading, setIsLoading] = useState(true)
  const [isUpdating, setIsUpdating] = useState(false)
  const [isResolving, setIsResolving] = useState(false)
  const [isSendingComment, setIsSendingComment] = useState(false)

  // AI draft states
  const [isGeneratingReplyDraft, setIsGeneratingReplyDraft] = useState(false)
  const [isGeneratingResolutionSummary, setIsGeneratingResolutionSummary] = useState(false)
  const [aiReplyDraftError, setAiReplyDraftError] = useState<string | null>(null)
  const [aiReplyDraftNotice, setAiReplyDraftNotice] = useState<string | null>(null)
  const [aiResolutionSummaryError, setAiResolutionSummaryError] = useState<string | null>(null)
  const [aiResolutionSummaryNotice, setAiResolutionSummaryNotice] = useState<string | null>(null)

  // Feedback alerts
  const [error, setError] = useState<string | null>(null)
  const [commentError, setCommentError] = useState<string | null>(null)
  const [resolutionError, setResolutionError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  // Modal dialog states
  const [isResolveModalOpen, setIsResolveModalOpen] = useState(false)

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
          getSupportTicket(accessToken, ticketNumber),
          getSupportTicketComments(accessToken, ticketNumber),
        ])

        if (!cancelled) {
          setTicket(ticketResponse)
          setComments(commentsResponse)
          setResolutionSummary(ticketResponse.resolutionSummary ?? '')
        }
      } catch (caughtError) {
        if (cancelled) return
        setError(
          caughtError instanceof Error
            ? caughtError.message
            : 'Unable to load support ticket. Please verify your permissions.',
        )
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

  async function handleAiTriageApplied() {
    if (!accessToken || !ticketNumber) {
      throw new Error('Your session is no longer valid')
    }

    const refreshedTicket = await getSupportTicket(accessToken, ticketNumber)
    setTicket(refreshedTicket)
    setSuccessMessage(`AI recommendation applied. Ticket priority is now ${refreshedTicket.priority}.`)
  }

  async function handleStatusChange(nextStatus: TicketStatus) {
    if (!accessToken || !ticketNumber) {
      setError('Your session is no longer valid')
      return
    }

    setIsUpdating(true)
    setError(null)
    setSuccessMessage(null)

    try {
      const response = await updateSupportTicketStatus(accessToken, ticketNumber, nextStatus)
      setTicket(response)
      setSuccessMessage(getActionMessage(response.status))
    } catch (caughtError) {
      setError(
        caughtError instanceof Error ? caughtError.message : 'Unable to update ticket status',
      )
    } finally {
      setIsUpdating(false)
    }
  }

  async function handleGenerateAiReplyDraft() {
    if (!accessToken || !ticketNumber) {
      setAiReplyDraftError('Your session is no longer valid')
      return
    }

    if (commentBody.trim()) {
      setAiReplyDraftError('Clear current message text before generating a new AI draft')
      return
    }

    setIsGeneratingReplyDraft(true)
    setAiReplyDraftError(null)
    setAiReplyDraftNotice(null)
    setCommentError(null)

    try {
      const response = await generateAiReplyDraft(accessToken, ticketNumber)
      setCommentBody(response.draft)
      setAiReplyDraftNotice(`AI draft generated with ${response.model}. Review and edit before sending.`)
    } catch (caughtError) {
      setAiReplyDraftError(
        caughtError instanceof Error ? caughtError.message : 'Unable to generate AI reply draft',
      )
    } finally {
      setIsGeneratingReplyDraft(false)
    }
  }

  async function handleGenerateAiResolutionSummary() {
    if (!accessToken || !ticketNumber) {
      setAiResolutionSummaryError('Your session is no longer valid')
      return
    }

    if (ticket?.status !== 'IN_PROGRESS') {
      setAiResolutionSummaryError('AI resolution summaries can only be generated while in progress')
      return
    }

    if (resolutionSummary.trim()) {
      setAiResolutionSummaryError('Clear the current summary before generating a new draft')
      return
    }

    setIsGeneratingResolutionSummary(true)
    setAiResolutionSummaryError(null)
    setAiResolutionSummaryNotice(null)
    setResolutionError(null)

    try {
      const response = await generateAiResolutionSummary(accessToken, ticketNumber)
      setResolutionSummary(response.draft)
      setAiResolutionSummaryNotice(
        `AI draft generated with ${response.model}. Review and edit before resolving.`,
      )
    } catch (caughtError) {
      setAiResolutionSummaryError(
        caughtError instanceof Error ? caughtError.message : 'Unable to generate AI resolution summary',
      )
    } finally {
      setIsGeneratingResolutionSummary(false)
    }
  }

  async function handleResolveTicket() {
    if (!accessToken || !ticketNumber) {
      setResolutionError('Your session is no longer valid')
      return
    }

    if (ticket?.status !== 'IN_PROGRESS') {
      setResolutionError('This ticket can only be resolved while it is in progress')
      return
    }

    const normalizedSummary = resolutionSummary.trim()
    if (!normalizedSummary) {
      setResolutionError('Enter a resolution summary before resolving the ticket')
      return
    }

    if (normalizedSummary.length > 4000) {
      setResolutionError('Resolution summary must not exceed 4000 characters')
      return
    }

    setIsResolving(true)
    setResolutionError(null)
    setAiResolutionSummaryError(null)
    setSuccessMessage(null)

    try {
      const response = await resolveSupportTicket(accessToken, ticketNumber, normalizedSummary)
      setTicket(response)
      setResolutionSummary(response.resolutionSummary ?? normalizedSummary)
      setAiResolutionSummaryNotice(null)
      setIsResolveModalOpen(false)
      setSuccessMessage('Ticket has been resolved successfully.')
    } catch (caughtError) {
      setResolutionError(
        caughtError instanceof Error ? caughtError.message : 'Unable to resolve ticket',
      )
    } finally {
      setIsResolving(false)
    }
  }

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
      const response = await addSupportTicketComment(accessToken, ticketNumber, {
        body: normalizedBody,
        visibility: isInternalComment ? 'INTERNAL' : 'PUBLIC',
      })

      setComments((currentComments) => [...currentComments, response])
      setCommentBody('')
      setIsInternalComment(false)
      setAiReplyDraftNotice(null)
      setAiReplyDraftError(null)
    } catch (caughtError) {
      setCommentError(
        caughtError instanceof Error ? caughtError.message : 'Unable to send message',
      )
    } finally {
      setIsSendingComment(false)
    }
  }

  const isConversationReadOnly =
    ticket?.status === 'RESOLVED' || ticket?.status === 'CLOSED' || ticket?.status === 'CANCELLED'

  return (
    <div className="support-ticket-workbench horizon-page-container">
      {/* Workbench Header */}
      <PageHeader
        title="Support Ticket Details"
        subtitle={
          ticket ? (
            <span style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
              <span
                style={{
                  fontFamily: 'var(--font-family-mono)',
                  fontWeight: 'var(--font-weight-bold)',
                  color: 'var(--color-primary)',
                }}
              >
                {ticket.ticketNumber}
              </span>
              <span>&bull;</span>
              <span>Created {formatDate(ticket.createdAt)}</span>
              <span>&bull;</span>
              <span>Requester: {ticket.createdByEmail}</span>
            </span>
          ) : (
            'Manage, troubleshoot and resolve support ticket.'
          )
        }
        actions={
          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => navigate('/support/tickets')}
              leadingIcon={<ArrowLeft size={14} />}
            >
              My Assignments
            </Button>
          </div>
        }
      />

      {/* Global Alerts */}
      {error && (
        <div className="horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ marginBottom: '1.5rem' }}>
          <AlertCircle size={18} className="horizon-auth-alert-icon" />
          <span>{error}</span>
        </div>
      )}

      {successMessage && (
        <div className="horizon-auth-alert horizon-auth-alert--success" role="status" style={{ marginBottom: '1.5rem' }}>
          <CheckCircle2 size={18} className="horizon-auth-alert-icon" />
          <span>{successMessage}</span>
        </div>
      )}

      {/* Loading state */}
      {isLoading && (
        <div style={{ textAlign: 'center', padding: '4rem 1rem', color: 'var(--color-text-secondary)' }}>
          <p>Loading support workbench...</p>
        </div>
      )}

      {!isLoading && ticket && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
          {/* Status Workflow Ribbon */}
          <div className="horizon-workflow-ribbon">
            <div className="horizon-workflow-status-meta">
              <span style={{ fontSize: 'var(--font-size-xs)', textTransform: 'uppercase', color: 'var(--color-text-secondary)', fontWeight: 'var(--font-weight-semibold)' }}>
                Status:
              </span>
              <StatusBadge status={ticket.status} size="md" />
              <PriorityBadge priority={ticket.priority} size="md" />
              <Badge variant="neutral" size="md">
                {formatLabel(ticket.type)}
              </Badge>
            </div>

            <div className="horizon-workflow-actions">
              {/* Step Transitions */}
              {ticket.status === 'ASSIGNED' && (
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => void handleStatusChange('IN_PROGRESS')}
                  isLoading={isUpdating}
                  leadingIcon={<Play size={14} />}
                >
                  Start Working
                </Button>
              )}

              {ticket.status === 'IN_PROGRESS' && (
                <>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => void handleStatusChange('WAITING_FOR_USER')}
                    isLoading={isUpdating}
                    leadingIcon={<Pause size={14} />}
                  >
                    Wait for User
                  </Button>
                  <Button
                    variant="primary"
                    size="sm"
                    onClick={() => setIsResolveModalOpen(true)}
                    leadingIcon={<FileCheck2 size={14} />}
                  >
                    Resolve Ticket
                  </Button>
                </>
              )}

              {ticket.status === 'WAITING_FOR_USER' && (
                <>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => void handleStatusChange('IN_PROGRESS')}
                    isLoading={isUpdating}
                    leadingIcon={<Play size={14} />}
                  >
                    Resume Progress
                  </Button>
                  <Button
                    variant="primary"
                    size="sm"
                    onClick={() => setIsResolveModalOpen(true)}
                    leadingIcon={<FileCheck2 size={14} />}
                  >
                    Resolve Ticket
                  </Button>
                </>
              )}

              {isConversationReadOnly && (
                <Badge variant="success" size="md">
                  Ticket Closed
                </Badge>
              )}
            </div>
          </div>

          {/* 2-Column Responsive Flagship Grid */}
          <div className="horizon-workbench-grid">
            {/* Left Column: Main Workflow & Conversation */}
            <div className="horizon-workbench-main">
              {/* Ticket Description Card */}
              <Card padded>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1rem', borderBottom: '1px solid var(--color-divider)', paddingBottom: '0.75rem' }}>
                  <h2 style={{ margin: 0, fontSize: 'var(--font-size-xl)', fontWeight: 'var(--font-weight-bold)', color: 'var(--color-text-primary)' }}>
                    {ticket.title}
                  </h2>
                </div>

                <div style={{ marginBottom: '1.25rem' }}>
                  <h3 style={{ fontSize: 'var(--font-size-xs)', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', margin: '0 0 0.5rem' }}>
                    Incident Description
                  </h3>
                  <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', lineHeight: 'var(--line-height-relaxed)', color: 'var(--color-text-primary)', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                    {ticket.description}
                  </p>
                </div>

                {/* Resolution Summary Banner if resolved */}
                {ticket.resolutionSummary && (
                  <div style={{ marginTop: '1rem', padding: '1rem', background: 'var(--color-success-subtle)', borderRadius: 'var(--radius-md)', border: '1px solid rgba(16, 185, 129, 0.2)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px', color: 'var(--color-success)', fontWeight: 'var(--font-weight-semibold)', fontSize: 'var(--font-size-sm)' }}>
                      <CheckCircle2 size={16} />
                      <span>Resolution Summary</span>
                    </div>
                    <p style={{ margin: 0, fontSize: 'var(--font-size-xs)', color: 'var(--color-text-primary)', whiteSpace: 'pre-wrap' }}>
                      {ticket.resolutionSummary}
                    </p>
                  </div>
                )}
              </Card>

              {/* Attachments Section */}
              <Card padded>
                <TicketAttachmentSection
                  ticketNumber={ticket.ticketNumber}
                  ticketStatus={ticket.status}
                  persona="support"
                />
              </Card>

              {/* Conversation Timeline */}
              <Card padded>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem', paddingBottom: '0.75rem', borderBottom: '1px solid var(--color-divider)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <MessageSquare size={18} style={{ color: 'var(--color-primary)' }} />
                    <h3 style={{ margin: 0, fontSize: 'var(--font-size-base)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)' }}>
                      Conversation & Staff Notes
                    </h3>
                  </div>
                  <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
                    {comments.length} {comments.length === 1 ? 'message' : 'messages'}
                  </span>
                </div>

                {comments.length === 0 ? (
                  <p style={{ color: 'var(--color-text-muted)', fontSize: 'var(--font-size-sm)', fontStyle: 'italic', margin: '1rem 0' }}>
                    No messages or notes posted yet on this ticket.
                  </p>
                ) : (
                  <Timeline>
                    {comments.map((comment) => {
                      const isInternal = comment.visibility === 'INTERNAL'
                      const authorDisplayName = comment.authorName || 'Support Staff'

                      return (
                        <TimelineItem
                          key={comment.commentId}
                          avatar={<UserAvatar name={authorDisplayName} size="sm" />}
                          authorName={authorDisplayName}
                          badge={
                            <Badge variant={isInternal ? 'warning' : 'neutral'} size="sm">
                              {isInternal ? 'Staff Only' : 'Public'}
                            </Badge>
                          }
                          timestamp={formatDate(comment.createdAt)}
                          isInternal={isInternal}
                        >
                          {isInternal && (
                            <div style={{ marginBottom: '6px' }}>
                              <span data-testid="internal-note-badge" className="horizon-internal-badge">
                                <Lock size={10} />
                                <span>Internal Note</span>
                              </span>
                            </div>
                          )}
                          <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', whiteSpace: 'pre-wrap', wordBreak: 'break-word', color: 'var(--color-text-primary)' }}>
                            {comment.body}
                          </p>
                        </TimelineItem>
                      )
                    })}
                  </Timeline>
                )}

                {/* Reply & Internal Note Composer */}
                {!isConversationReadOnly ? (
                  <div className="horizon-composer-container" style={{ marginTop: '1.5rem' }}>
                    <form onSubmit={handleSendComment}>
                      {/* Public vs Internal Selection Tabs */}
                      <div className="horizon-composer-tabs" role="tablist">
                        <button
                          type="button"
                          role="tab"
                          aria-selected={!isInternalComment}
                          className={`horizon-composer-tab horizon-composer-tab--public ${!isInternalComment ? 'is-active' : ''}`}
                          onClick={() => setIsInternalComment(false)}
                        >
                          <Globe size={14} />
                          <span>Public Reply</span>
                        </button>
                        <button
                          type="button"
                          role="tab"
                          aria-selected={isInternalComment}
                          className={`horizon-composer-tab horizon-composer-tab--internal ${isInternalComment ? 'is-active' : ''}`}
                          onClick={() => setIsInternalComment(true)}
                        >
                          <Lock size={14} />
                          <span>Internal Staff Note</span>
                        </button>
                      </div>

                      {/* Checkbox for internal note (accessible and regression-safe) */}
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', margin: '0.75rem 0' }}>
                        <input
                          type="checkbox"
                          id="comment-is-internal"
                          checked={isInternalComment}
                          onChange={(e) => setIsInternalComment(e.target.checked)}
                          className="horizon-checkbox"
                          style={{ width: '16px', height: '16px', cursor: 'pointer', accentColor: 'var(--color-primary)' }}
                        />
                        <label
                          htmlFor="comment-is-internal"
                          style={{
                            fontSize: 'var(--font-size-sm)',
                            fontWeight: 'var(--font-weight-medium)',
                            color: 'var(--color-text-secondary)',
                            cursor: 'pointer',
                            userSelect: 'none',
                          }}
                        >
                          Mark as internal note (staff only)
                        </label>
                      </div>

                      {/* Visibility indicator notice */}
                      <div
                        className={`horizon-composer-visibility-indicator ${
                          isInternalComment
                            ? 'horizon-composer-visibility-indicator--internal'
                            : 'horizon-composer-visibility-indicator--public'
                        }`}
                      >
                        {isInternalComment ? (
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                            <Lock size={12} />
                            <strong>Private Staff Note:</strong> Hidden from the ticket requester. Only staff members with authorized access can view this note.
                          </span>
                        ) : (
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                            <Globe size={12} />
                            <strong>Public Response:</strong> Visible to the customer and sent as a notification.
                          </span>
                        )}
                      </div>

                      {/* Message Textarea */}
                      <div className="horizon-form-field" style={{ margin: 0 }}>
                        <div className="horizon-input-wrapper">
                          <textarea
                            id="support-comment-body"
                            className="horizon-textarea"
                            style={{ minHeight: '120px' }}
                            placeholder={
                              isInternalComment
                                ? 'Add confidential investigation notes, replica checks, or staff handoff details...'
                                : 'Type a clear, professional reply to the requester...'
                            }
                            value={commentBody}
                            onChange={(e) => setCommentBody(e.target.value)}
                            disabled={isSendingComment}
                            maxLength={4000}
                          />
                        </div>
                      </div>

                      {/* Composer Notices & Errors */}
                      {aiReplyDraftNotice && (
                        <div className="horizon-auth-alert horizon-auth-alert--success" style={{ margin: '0.75rem 0 0', padding: '0.5rem' }}>
                          <span style={{ fontSize: 'var(--font-size-xs)' }}>{aiReplyDraftNotice}</span>
                        </div>
                      )}

                      {commentError && (
                        <div className="horizon-auth-alert horizon-auth-alert--error" style={{ margin: '0.75rem 0 0', padding: '0.5rem' }}>
                          <span style={{ fontSize: 'var(--font-size-xs)' }}>{commentError}</span>
                        </div>
                      )}

                      {/* Composer Actions */}
                      <div className="horizon-composer-actions">
                        <Button
                          variant="secondary"
                          size="sm"
                          type="button"
                          onClick={handleGenerateAiReplyDraft}
                          isLoading={isGeneratingReplyDraft}
                          disabled={isSendingComment || Boolean(commentBody.trim())}
                          leadingIcon={<Sparkles size={14} />}
                        >
                          AI Reply Draft
                        </Button>

                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                          <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
                            {commentBody.length}/4000
                          </span>
                          <Button
                            variant={isInternalComment ? 'secondary' : 'primary'}
                            size="sm"
                            type="submit"
                            isLoading={isSendingComment}
                            disabled={!commentBody.trim()}
                            aria-label={isInternalComment ? 'Add internal note' : 'Send message'}
                          >
                            {isInternalComment ? 'Add Internal Note' : 'Send Public Reply'}
                          </Button>
                        </div>
                      </div>
                    </form>
                  </div>
                ) : (
                  <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-sm)', textAlign: 'center', margin: '1.5rem 0 0' }}>
                    This ticket is finalized ({ticket.status}). New messages or notes can no longer be posted.
                  </p>
                )}
              </Card>
            </div>

            {/* Right Column: Context Cards & Integrated AI Assistant */}
            <div className="horizon-workbench-sidebar">
              {/* Context Summary Card */}
              <div className="horizon-context-card">
                <div className="horizon-context-header">
                  <h4 className="horizon-context-title">Ticket Context</h4>
                  <Badge variant="neutral" size="sm">
                    {ticket.ticketNumber}
                  </Badge>
                </div>

                <div className="horizon-context-row">
                  <span className="horizon-context-row-label">Requester</span>
                  <span className="horizon-context-row-value">{ticket.createdByEmail}</span>
                </div>

                <div className="horizon-context-row">
                  <span className="horizon-context-row-label">Assigned Engineer</span>
                  <span className="horizon-context-row-value">
                    {user?.email || 'Assigned to Me'}
                  </span>
                </div>

                <div className="horizon-context-row">
                  <span className="horizon-context-row-label">Assigned At</span>
                  <span className="horizon-context-row-value">{formatOptionalDate(ticket.assignedAt)}</span>
                </div>

                <div className="horizon-context-row">
                  <span className="horizon-context-row-label">Last Updated</span>
                  <span className="horizon-context-row-value">{formatDate(ticket.updatedAt)}</span>
                </div>

                {/* SLA Tracking Details */}
                <div style={{ marginTop: '0.75rem', paddingTop: '0.75rem', borderTop: '1px solid var(--color-divider)' }}>
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
                    <span className="horizon-context-row-value">{formatOptionalDate(ticket.responseDueAt)}</span>
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
                    <span className="horizon-context-row-value">{formatOptionalDate(ticket.resolutionDueAt)}</span>
                  </div>
                </div>
              </div>

              {/* Integrated AI Assistant Workbench */}
              <AiWorkbench
                accessToken={accessToken}
                ticketNumber={ticket.ticketNumber}
                currentPriority={ticket.priority}
                ticketStatus={ticket.status}
                onTriageApplied={handleAiTriageApplied}
                onGenerateReplyDraft={handleGenerateAiReplyDraft}
                isGeneratingReplyDraft={isGeneratingReplyDraft}
                aiReplyDraftNotice={aiReplyDraftNotice}
                aiReplyDraftError={aiReplyDraftError}
                onGenerateResolutionSummary={handleGenerateAiResolutionSummary}
                isGeneratingResolutionSummary={isGeneratingResolutionSummary}
                aiResolutionSummaryNotice={aiResolutionSummaryNotice}
                aiResolutionSummaryError={aiResolutionSummaryError}
              />
            </div>
          </div>
        </div>
      )}

      {/* Resolution Modal Dialog */}
      <Modal
        isOpen={isResolveModalOpen}
        onClose={() => setIsResolveModalOpen(false)}
        title="Resolve Ticket"
        subtitle="Provide a concise technical resolution summary explaining how the issue was fixed. The customer will receive this explanation."
        footer={
          <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
            <Button variant="secondary" size="sm" onClick={() => setIsResolveModalOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              size="sm"
              onClick={() => void handleResolveTicket()}
              isLoading={isResolving}
              disabled={!resolutionSummary.trim()}
            >
              Confirm and Resolve Ticket
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', padding: '0.5rem 0' }}>
          {resolutionError && (
            <div className="horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ margin: 0, padding: '0.5rem' }}>
              <AlertCircle size={16} className="horizon-auth-alert-icon" />
              <span style={{ fontSize: 'var(--font-size-xs)' }}>{resolutionError}</span>
            </div>
          )}

          {aiResolutionSummaryNotice && (
            <div className="horizon-auth-alert horizon-auth-alert--success" style={{ margin: 0, padding: '0.5rem' }}>
              <span style={{ fontSize: 'var(--font-size-xs)' }}>{aiResolutionSummaryNotice}</span>
            </div>
          )}

          <div className="horizon-form-field" style={{ margin: 0 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
              <label htmlFor="support-resolution-summary" className="horizon-label" style={{ margin: 0 }}>
                Resolution Summary <span className="horizon-field-required">*</span>
              </label>
              <Button
                variant="ghost"
                size="sm"
                type="button"
                onClick={handleGenerateAiResolutionSummary}
                isLoading={isGeneratingResolutionSummary}
                disabled={Boolean(resolutionSummary.trim())}
                leadingIcon={<Sparkles size={12} />}
              >
                Auto-Draft with AI
              </Button>
            </div>
            <div className="horizon-input-wrapper">
              <textarea
                id="support-resolution-summary"
                className="horizon-textarea"
                style={{ minHeight: '160px' }}
                placeholder="Describe the root cause and remediation steps taken..."
                value={resolutionSummary}
                onChange={(e) => setResolutionSummary(e.target.value)}
                disabled={isResolving}
                maxLength={4000}
                required
              />
            </div>
            <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', marginTop: '4px', textAlign: 'right' }}>
              {resolutionSummary.length}/4000
            </span>
          </div>
        </div>
      </Modal>
    </div>
  )
}
