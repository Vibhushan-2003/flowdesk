import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, DragEvent, FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import {
  ArrowLeft,
  CheckCircle2,
  AlertCircle,
  AlertTriangle,
  HelpCircle,
  Paperclip,
  UploadCloud,
  Trash2,
  RefreshCw,
} from 'lucide-react'

import { createTicket } from '../api/ticket'
import { uploadAttachment } from '../api/attachment'
import { useAuth } from '../auth/useAuth'
import type {
  TicketPriority,
  TicketResponse,
  TicketType,
} from '../types/ticket'
import { formatBytes, formatLabel } from '../utils/formatters'
import { TicketDeflectionCard } from '../components/tickets/TicketDeflectionCard'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { StatusBadge } from '../components/ui/StatusBadge'
import { PriorityBadge } from '../components/ui/PriorityBadge'
import '../styles/kb.css'

const TICKET_DRAFT_KEY = 'flowdesk_ticket_draft'
const MAX_ATTACHMENTS = 10
const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024 // 10 MB
const ALLOWED_EXTENSIONS = [
  '.pdf',
  '.jpg',
  '.jpeg',
  '.png',
  '.webp',
  '.txt',
  '.log',
  '.csv',
]

interface TicketDraftState {
  userId?: string
  type: TicketType
  title: string
  description: string
}

interface StagedAttachment {
  id: string
  file: File
  status: 'staged' | 'uploading' | 'uploaded' | 'failed'
  errorMessage?: string
  scanStatus?: 'PENDING_SCAN' | 'CLEAN' | 'INFECTED' | 'SCAN_FAILED'
  scanDetails?: string | null
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

function getFileIcon(filename?: string, contentType?: string): string {
  const lower = (filename || '').toLowerCase()
  const cType = (contentType || '').toLowerCase()
  if (lower.endsWith('.pdf') || cType.includes('pdf')) return '📄'
  if (
    lower.endsWith('.png') ||
    lower.endsWith('.jpg') ||
    lower.endsWith('.jpeg') ||
    lower.endsWith('.webp') ||
    cType.startsWith('image/')
  ) {
    return '🖼️'
  }
  if (lower.endsWith('.csv') || cType.includes('csv')) return '📊'
  return '📝'
}

export function CreateTicketPage() {
  const { accessToken, user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const isPrivilegedStaff = Boolean(
    user?.roles?.some(
      (role) =>
        role === 'SUPPORT_ENGINEER' ||
        role === 'TEAM_LEAD' ||
        role === 'ADMIN',
    ),
  )

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

  // Staged attachments state
  const [stagedFiles, setStagedFiles] = useState<StagedAttachment[]>([])
  const [isDragOver, setIsDragOver] = useState(false)
  const [fileValidationError, setFileValidationError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [isSubmitting, setIsSubmitting] = useState(false)
  const [uploadProgressText, setUploadProgressText] = useState<string | null>(null)
  const [retryingFileIds, setRetryingFileIds] = useState<string[]>([])
  const isSubmittingRef = useRef(false)
  const idempotencyKeyRef = useRef<string | null>(null)

  const [isSolved, setIsSolved] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [partialUploadError, setPartialUploadError] = useState<string | null>(null)
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
    setStagedFiles([])
    setRetryingFileIds([])
    setFileValidationError(null)
    setError(null)
    setPartialUploadError(null)
    setUploadProgressText(null)
    sessionStorage.removeItem(TICKET_DRAFT_KEY)
    idempotencyKeyRef.current = null
  }

  function validateAndAddFiles(filesToAdd: File[]) {
    setFileValidationError(null)

    if (stagedFiles.length + filesToAdd.length > MAX_ATTACHMENTS) {
      setFileValidationError(`Maximum ${MAX_ATTACHMENTS} attachments allowed per ticket.`)
      return
    }

    const newStaged: StagedAttachment[] = []

    for (const file of filesToAdd) {
      if (file.size === 0) {
        setFileValidationError(`File "${file.name}" is empty (0 bytes).`)
        return
      }

      const lower = file.name.toLowerCase()
      const isAllowed = ALLOWED_EXTENSIONS.some((ext) => lower.endsWith(ext))
      if (!isAllowed) {
        setFileValidationError(`File "${file.name}" has an unsupported format. Supported: ${ALLOWED_EXTENSIONS.join(', ')}`)
        return
      }

      if (file.size > MAX_FILE_SIZE_BYTES) {
        setFileValidationError(`File "${file.name}" exceeds the maximum limit of 10 MB.`)
        return
      }

      // Check duplicate by name + size
      const isDuplicate =
        stagedFiles.some((s) => s.file.name === file.name && s.file.size === file.size) ||
        newStaged.some((s) => s.file.name === file.name && s.file.size === file.size)
      if (!isDuplicate) {
        newStaged.push({
          id: typeof crypto !== 'undefined' && crypto.randomUUID
            ? crypto.randomUUID()
            : `stage-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
          file,
          status: 'staged',
        })
      }
    }

    if (newStaged.length > 0) {
      setStagedFiles((prev) => [...prev, ...newStaged])
    }
  }

  function handleFileInputChange(event: ChangeEvent<HTMLInputElement>) {
    const files = event.target.files
    if (files && files.length > 0) {
      validateAndAddFiles(Array.from(files))
    }
    // Reset file input so user can re-select if needed
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  function handleDragOver(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setIsDragOver(true)
  }

  function handleDragLeave(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setIsDragOver(false)
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setIsDragOver(false)
    const files = event.dataTransfer.files
    if (files && files.length > 0) {
      validateAndAddFiles(Array.from(files))
    }
  }

  function handleRemoveStagedFile(id: string) {
    setStagedFiles((prev) => prev.filter((item) => item.id !== id))
    setFileValidationError(null)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    // Prevent submission while solved or already submitting (synchronous ref check prevents rapid double-clicks)
    if (isSolved || isSubmitting || isSubmittingRef.current) {
      return
    }

    setError(null)
    setPartialUploadError(null)
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
      const ticketResponse = await createTicket(
        {
          type,
          title: trimmedTitle,
          description: trimmedDescription,
          priority,
        },
        accessToken,
        idempotencyKeyRef.current,
      )

      // On successful creation, reset idempotency key so next ticket gets a fresh key
      idempotencyKeyRef.current = null
      sessionStorage.removeItem(TICKET_DRAFT_KEY)

      // Upload staged attachments sequentially if any
      if (stagedFiles.length > 0) {
        let hasFailures = false
        const workingList = [...stagedFiles]

        for (let i = 0; i < workingList.length; i++) {
          const targetId = workingList[i].id
          const currentFile = workingList[i].file

          setStagedFiles((prev) =>
            prev.map((f) => (f.id === targetId ? { ...f, status: 'uploading', errorMessage: undefined } : f)),
          )
          setUploadProgressText(`Uploading attachment ${i + 1} of ${workingList.length}: ${currentFile.name}...`)

          try {
            const uploadRes = await uploadAttachment(ticketResponse.ticketNumber, currentFile, 'employee', accessToken)
            setStagedFiles((prev) =>
              prev.map((f) =>
                f.id === targetId
                  ? {
                      ...f,
                      status: 'uploaded',
                      scanStatus: uploadRes.scanStatus,
                      scanDetails: uploadRes.scanDetails,
                    }
                  : f,
              ),
            )
          } catch (uploadErr) {
            hasFailures = true
            const errMsg = uploadErr instanceof Error ? uploadErr.message : 'Upload failed'
            setStagedFiles((prev) =>
              prev.map((f) =>
                f.id === targetId
                  ? {
                      ...f,
                      status: 'failed',
                      errorMessage: errMsg,
                    }
                  : f,
              ),
            )
          }
        }

        setUploadProgressText(null)

        if (hasFailures) {
          // Keep created ticket reference; display partial upload status with retry actions
          setCreatedTicket(ticketResponse)
          setPartialUploadError(
            `Ticket ${ticketResponse.ticketNumber} was created successfully, but one or more attachments failed to upload. You can retry failed files below or proceed to your ticket.`,
          )
          return
        }
      }

      setCreatedTicket(ticketResponse)
      setType('INCIDENT')
      setTitle('')
      setDescription('')
      setPriority('MEDIUM')
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

  async function handleRetryFailedUpload(targetId: string) {
    if (!createdTicket || !accessToken || retryingFileIds.includes(targetId)) return

    const item = stagedFiles.find((f) => f.id === targetId)
    if (!item) return

    setRetryingFileIds((prev) => [...prev, targetId])
    setStagedFiles((prev) =>
      prev.map((f) => (f.id === targetId ? { ...f, status: 'uploading', errorMessage: undefined } : f)),
    )

    try {
      const res = await uploadAttachment(createdTicket.ticketNumber, item.file, 'employee', accessToken)
      setStagedFiles((prev) => {
        const next = prev.map((f) =>
          f.id === targetId
            ? {
                ...f,
                status: 'uploaded' as const,
                scanStatus: res.scanStatus,
                scanDetails: res.scanDetails,
              }
            : f,
        )
        const remainingFailed = next.filter((f) => f.status === 'failed')
        if (remainingFailed.length === 0) {
          setPartialUploadError(null)
        }
        return next
      })
    } catch (retryErr) {
      const errMsg = retryErr instanceof Error ? retryErr.message : 'Retry failed'
      setStagedFiles((prev) =>
        prev.map((f) => (f.id === targetId ? { ...f, status: 'failed', errorMessage: errMsg } : f)),
      )
    } finally {
      setRetryingFileIds((prev) => prev.filter((id) => id !== targetId))
    }
  }

  return (
    <div className="horizon-create-ticket-page">
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
        /* Confirmation View 2: Ticket Created Successfully (with attachment status) */
        <section
          className="ticket-success-panel horizon-card"
          aria-live="polite"
          style={{ padding: '3rem 2rem', textAlign: 'center', maxWidth: '680px', margin: '2rem auto' }}
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

          <div className="ticket-success-tags" style={{ display: 'flex', gap: '0.5rem', justifyContent: 'center', marginBottom: '1.5rem' }}>
            <Badge variant="neutral" size="md">
              {formatLabel(createdTicket.type)}
            </Badge>
            <PriorityBadge priority={createdTicket.priority} size="md" />
            <StatusBadge status={createdTicket.status} size="md" />
          </div>

          {/* Attached Files & Truthful ClamAV Scan Status */}
          {stagedFiles.length > 0 && (
            <div
              className="ticket-success-attachments"
              style={{
                textAlign: 'left',
                margin: '1.5rem 0',
                padding: '1.25rem',
                background: 'var(--color-surface-subtle)',
                border: '1px solid var(--color-border)',
                borderRadius: 'var(--radius-md)',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '12px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Paperclip size={16} style={{ color: 'var(--color-primary)' }} />
                  <strong style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-primary)' }}>
                    Attached Files ({stagedFiles.filter((f) => f.status === 'uploaded').length} of {stagedFiles.length} uploaded)
                  </strong>
                </div>
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                {stagedFiles.map((item) => (
                  <div
                    key={item.id}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      padding: '8px 12px',
                      background: 'var(--color-surface)',
                      border: '1px solid var(--color-border)',
                      borderRadius: 'var(--radius-sm)',
                      gap: '12px',
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: 0, flex: 1 }}>
                      <span style={{ fontSize: '1.1rem' }}>{getFileIcon(item.file.name, item.file.type)}</span>
                      <div style={{ minWidth: 0, flex: 1 }}>
                        <p style={{ margin: 0, fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-medium)', color: 'var(--color-text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {item.file.name}
                        </p>
                        <span style={{ fontSize: '0.6875rem', color: 'var(--color-text-muted)' }}>
                          {formatBytes(item.file.size)}
                        </span>
                      </div>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexShrink: 0 }}>
                      {item.status === 'uploaded' && (
                        item.scanStatus === 'INFECTED' ? (
                          <Badge variant="danger" size="sm" dot>
                            Quarantined (Malware Detected)
                          </Badge>
                        ) : item.scanStatus === 'SCAN_FAILED' ? (
                          <Badge variant="warning" size="sm" dot>
                            Quarantined (Scan Failed)
                          </Badge>
                        ) : item.scanStatus === 'PENDING_SCAN' ? (
                          <Badge variant="neutral" size="sm" dot>
                            Scan Pending
                          </Badge>
                        ) : (
                          <Badge variant="success" size="sm" dot>
                            Clean (Verified)
                          </Badge>
                        )
                      )}

                      {item.status === 'uploading' && (
                        <Badge variant="primary" size="sm">
                          Uploading...
                        </Badge>
                      )}

                      {item.status === 'failed' && (
                        <>
                          <Badge variant="danger" size="sm" dot>
                            Upload Failed
                          </Badge>
                          <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => handleRetryFailedUpload(item.id)}
                            disabled={retryingFileIds.includes(item.id)}
                            leadingIcon={<RefreshCw size={12} className={retryingFileIds.includes(item.id) ? 'animate-spin' : ''} />}
                          >
                            {retryingFileIds.includes(item.id) ? 'Retrying...' : 'Retry'}
                          </Button>
                        </>
                      )}
                    </div>
                  </div>
                ))}
              </div>

              {/* Truthful ClamAV Security Notice if any file was quarantined */}
              {stagedFiles.some((f) => f.scanStatus === 'INFECTED' || f.scanStatus === 'SCAN_FAILED') && (
                <div style={{ marginTop: '12px', padding: '8px 12px', background: 'var(--color-danger-subtle)', border: '1px solid var(--color-danger-border)', borderRadius: 'var(--radius-sm)', display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--font-size-xs)', color: 'var(--color-danger)' }}>
                  <AlertCircle size={14} style={{ flexShrink: 0 }} />
                  <span>
                    One or more attachments were flagged by automated ClamAV malware scanning and quarantined fail-closed. Quarantined files cannot be downloaded.
                  </span>
                </div>
              )}
            </div>
          )}

          {/* Partial Attachment Failure Notice */}
          {partialUploadError && (
            <div style={{ textAlign: 'left', marginBottom: '1.5rem', padding: '1rem', background: 'var(--color-warning-subtle)', border: '1px solid var(--color-warning-border)', borderRadius: 'var(--radius-md)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px', color: 'var(--color-warning)' }}>
                <AlertTriangle size={18} />
                <strong style={{ fontSize: 'var(--font-size-sm)' }}>Attachment Notice</strong>
              </div>
              <p style={{ margin: 0, fontSize: 'var(--font-size-xs)', color: 'var(--color-text-primary)' }}>
                {partialUploadError}
              </p>
            </div>
          )}

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
        /* Two-Area Form Layout: Main Form + Contextual Help */
        <div className="horizon-create-grid">
          {/* Main Incident / Request Form */}
          <Card padded className="ticket-form-card">
            <form className="ticket-form horizon-auth-form" onSubmit={handleSubmit}>
              {/* Type & Priority Row */}
              <div className="horizon-form-row horizon-form-row--2col">
                {/* Type Select */}
                <div className="horizon-form-field">
                  <label htmlFor="ticket-type-select" className="horizon-label">
                    Ticket type <span className="horizon-label__required">*</span>
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
                    Urgency / Priority <span className="horizon-label__required">*</span>
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
                      <option value="LOW">Low (minor inconvenience or cosmetic)</option>
                      <option value="MEDIUM">Medium (standard business impact)</option>
                      <option value="HIGH">High (significant impact on daily work)</option>
                      {isPrivilegedStaff && (
                        <option value="CRITICAL">Critical (complete work stoppage / emergency)</option>
                      )}
                    </select>
                  </div>
                  <span className="horizon-field-helper">
                    {isPrivilegedStaff
                      ? 'Privileged staff: You may submit tickets directly at Critical priority.'
                      : 'Employees can submit Low, Medium, or High urgency. Critical priority requires support escalation.'}
                  </span>
                </div>
              </div>

              {/* Title Input */}
              <div className="horizon-form-field">
                <label htmlFor="ticket-title-input" className="horizon-label">
                  Title <span className="horizon-label__required">*</span>
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
                    placeholder="Briefly describe the issue (e.g. VPN connection failure after OS update)"
                    disabled={isSubmitting}
                    required
                  />
                </div>
              </div>

              {/* Description Textarea */}
              <div className="horizon-form-field">
                <label htmlFor="ticket-description-input" className="horizon-label">
                  Description <span className="horizon-label__required">*</span>
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
                    rows={6}
                    disabled={isSubmitting}
                    required
                  />
                </div>
                <div className="horizon-field-bottom">
                  <span className="horizon-field-helper">Markdown formatting supported in comments.</span>
                  <span className="horizon-char-counter">{description.length} / 5000</span>
                </div>
              </div>

              {/* File Attachments Dropzone & Staged File Selection */}
              <div className="horizon-form-field" style={{ marginTop: '0.5rem' }}>
                <label className="horizon-label" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                    <Paperclip size={15} style={{ color: 'var(--color-primary)' }} />
                    Attachments & Diagnostics (Optional)
                  </span>
                  <span style={{ fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-regular)', color: 'var(--color-text-secondary)' }}>
                    {stagedFiles.length} / {MAX_ATTACHMENTS} selected
                  </span>
                </label>

                {/* Hidden File Input */}
                <input
                  ref={fileInputRef}
                  type="file"
                  multiple
                  accept={ALLOWED_EXTENSIONS.join(',')}
                  onChange={handleFileInputChange}
                  className="attachment-hidden-input"
                  disabled={isSubmitting}
                  id="ticket-create-file-input"
                />

                {/* Interactive Drag & Drop Area */}
                <div
                  className={`attachment-dropzone ${isDragOver ? 'attachment-dropzone-active' : ''} ${
                    isSubmitting ? 'attachment-dropzone-uploading' : ''
                  }`}
                  onDragOver={handleDragOver}
                  onDragLeave={handleDragLeave}
                  onDrop={handleDrop}
                  onClick={() => !isSubmitting && fileInputRef.current?.click()}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if ((e.key === 'Enter' || e.key === ' ') && !isSubmitting) {
                      e.preventDefault()
                      fileInputRef.current?.click()
                    }
                  }}
                  aria-label="Upload attachments dropzone"
                >
                  <div className="attachment-dropzone-content">
                    <span className="attachment-dropzone-icon" aria-hidden="true">
                      <UploadCloud size={28} style={{ color: 'var(--color-primary)' }} />
                    </span>
                    <p className="attachment-dropzone-text">
                      <strong>Choose files</strong> or drag and drop here
                    </p>
                    <p className="attachment-dropzone-hint">
                      PDF, PNG, JPG, WEBP, TXT, LOG, CSV up to 10 MB each. Scanned automatically with ClamAV.
                    </p>
                  </div>
                </div>

                {/* Validation Error Alert */}
                {fileValidationError && (
                  <div className="form-error horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ margin: '0.5rem 0' }}>
                    <AlertCircle size={15} className="horizon-auth-alert-icon" />
                    <span>{fileValidationError}</span>
                  </div>
                )}

                {/* Staged File List Preview */}
                {stagedFiles.length > 0 && (
                  <div className="staged-attachments-list" style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '0.75rem' }}>
                    {stagedFiles.map((item) => (
                      <div
                        key={item.id}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          padding: '8px 12px',
                          background: 'var(--color-surface)',
                          border: '1px solid var(--color-border)',
                          borderRadius: 'var(--radius-md)',
                          gap: '10px',
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: 0, flex: 1 }}>
                          <span style={{ fontSize: '1.1rem' }}>{getFileIcon(item.file.name, item.file.type)}</span>
                          <div style={{ minWidth: 0, flex: 1 }}>
                            <p style={{ margin: 0, fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-medium)', color: 'var(--color-text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                              {item.file.name}
                            </p>
                            <span style={{ fontSize: '0.6875rem', color: 'var(--color-text-muted)' }}>
                              {formatBytes(item.file.size)}
                            </span>
                          </div>
                        </div>

                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          {item.status === 'staged' && (
                            <Badge variant="neutral" size="sm">Ready</Badge>
                          )}
                          {item.status === 'uploading' && (
                            <Badge variant="primary" size="sm">Uploading...</Badge>
                          )}
                          {item.status === 'uploaded' && (
                            <Badge variant="success" size="sm">Uploaded</Badge>
                          )}
                          {item.status === 'failed' && (
                            <Badge variant="danger" size="sm">Failed</Badge>
                          )}

                          {!isSubmitting && (
                            <button
                              type="button"
                              className="horizon-btn horizon-btn--ghost horizon-btn--sm"
                              onClick={() => handleRemoveStagedFile(item.id)}
                              aria-label={`Remove ${item.file.name}`}
                              style={{ padding: '2px', height: '24px', width: '24px' }}
                            >
                              <Trash2 size={13} style={{ color: 'var(--color-danger)' }} />
                            </button>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Upload Progress Status Banner */}
              {uploadProgressText && (
                <div style={{ margin: '1rem 0', padding: '0.75rem 1rem', background: 'var(--color-primary-subtle)', border: '1px solid var(--color-primary-border)', borderRadius: 'var(--radius-md)', display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--font-size-sm)', color: 'var(--color-primary)' }}>
                  <RefreshCw size={16} className="animate-spin" />
                  <span>{uploadProgressText}</span>
                </div>
              )}

              {/* Error Alert */}
              {error && (
                <div className="form-error horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ marginTop: '1rem' }}>
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
                style={{ marginTop: '1.25rem' }}
              >
                {isSubmitting
                  ? uploadProgressText
                    ? 'Uploading attachments...'
                    : 'Creating ticket...'
                  : stagedFiles.length > 0
                  ? `Create ticket with ${stagedFiles.length} ${stagedFiles.length === 1 ? 'attachment' : 'attachments'}`
                  : 'Create ticket'}
              </button>
            </form>
          </Card>

          {/* Contextual Side Panel: Knowledge Base Suggestions & Best Practices */}
          <div style={{ position: 'sticky', top: '88px', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
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
                setStagedFiles([])
              }}
              onDeflected={() => {
                sessionStorage.removeItem(TICKET_DRAFT_KEY)
                navigate('/dashboard')
              }}
            />

            {/* Helper Guidance Card */}
            <Card padded>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '10px' }}>
                <HelpCircle size={18} style={{ color: 'var(--color-primary)' }} />
                <h3 style={{ margin: 0, fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)' }}>
                  Submission Guidelines
                </h3>
              </div>
              <ul style={{ margin: 0, paddingLeft: '1.25rem', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', lineHeight: 1.6 }}>
                <li>Include exact error codes or stack traces where available.</li>
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
