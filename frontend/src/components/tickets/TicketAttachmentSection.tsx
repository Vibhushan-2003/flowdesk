import {
  type ChangeEvent,
  type DragEvent,
  useEffect,
  useRef,
  useState,
} from 'react'

import {
  type AttachmentPersona,
  downloadAttachment,
  listAttachments,
  uploadAttachment,
} from '../../api/attachment'
import { useAuth } from '../../auth/useAuth'
import type { TicketAttachmentResponse } from '../../types/attachment'
import type { TicketStatus } from '../../types/ticket'
import { formatBytes, formatDate } from '../../utils/formatters'

export interface TicketAttachmentSectionProps {
  ticketNumber: string
  ticketStatus: TicketStatus
  persona: AttachmentPersona
}

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

const ALLOWED_MIME_TYPES = new Set([
  'application/pdf',
  'image/jpeg',
  'image/png',
  'image/webp',
  'text/plain',
  'text/csv',
])

function isFileSupported(file: File): boolean {
  const lowerName = file.name.toLowerCase()
  const hasValidExt = ALLOWED_EXTENSIONS.some((ext) => lowerName.endsWith(ext))
  if (!hasValidExt) {
    return false
  }

  // If browser identified MIME type, check it, else rely on extension match
  if (file.type && !ALLOWED_MIME_TYPES.has(file.type)) {
    // In some systems, .log or .csv might report as generic or empty
    if (!lowerName.endsWith('.log') && !lowerName.endsWith('.csv') && !lowerName.endsWith('.txt')) {
      return false
    }
  }

  return true
}

function getFileIcon(filename: string, contentType: string): string {
  const lower = filename.toLowerCase()
  if (lower.endsWith('.pdf') || contentType.includes('pdf')) {
    return '📄'
  }
  if (
    lower.endsWith('.png') ||
    lower.endsWith('.jpg') ||
    lower.endsWith('.jpeg') ||
    lower.endsWith('.webp') ||
    contentType.startsWith('image/')
  ) {
    return '🖼️'
  }
  if (lower.endsWith('.csv') || contentType.includes('csv')) {
    return '📊'
  }
  return '📝'
}

export function TicketAttachmentSection({
  ticketNumber,
  ticketStatus,
  persona,
}: TicketAttachmentSectionProps) {
  const { accessToken } = useAuth()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [attachments, setAttachments] = useState<TicketAttachmentResponse[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isUploading, setIsUploading] = useState(false)
  const [downloadingId, setDownloadingId] = useState<string | null>(null)

  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [isDragOver, setIsDragOver] = useState(false)

  const isTicketFinished =
    ticketStatus === 'RESOLVED' ||
    ticketStatus === 'CLOSED' ||
    ticketStatus === 'CANCELLED'

  const isQuotaReached = attachments.length >= MAX_ATTACHMENTS
  const canUpload = !isTicketFinished && !isQuotaReached && !isUploading

  // Load attachments on mount or when ticket changes
  useEffect(() => {
    let cancelled = false

    async function fetchAttachments() {
      if (!accessToken || !ticketNumber) {
        setIsLoading(false)
        return
      }

      setIsLoading(true)
      setError(null)

      try {
        const data = await listAttachments(ticketNumber, persona, accessToken)
        if (!cancelled) {
          setAttachments(data)
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error
              ? err.message
              : 'Failed to load attachments.',
          )
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    void fetchAttachments()

    return () => {
      cancelled = true
    }
  }, [ticketNumber, persona, accessToken])

  async function handleFileUpload(file: File) {
    if (!accessToken) {
      setError('Your session is no longer valid. Please sign in again.')
      return
    }

    if (file.size === 0) {
      setError('Cannot upload empty file (0 bytes).')
      setSuccessMessage(null)
      return
    }

    if (file.size > MAX_FILE_SIZE_BYTES) {
      setError(`File size (${formatBytes(file.size)}) exceeds the maximum permitted limit of 10 MB.`)
      setSuccessMessage(null)
      return
    }

    if (!isFileSupported(file)) {
      setError(
        `Unsupported file type. Permitted formats: PDF, JPEG, PNG, WebP, TXT, LOG, CSV.`,
      )
      setSuccessMessage(null)
      return
    }

    if (isQuotaReached) {
      setError(`Attachment quota reached (maximum ${MAX_ATTACHMENTS} attachments per ticket).`)
      setSuccessMessage(null)
      return
    }

    if (isTicketFinished) {
      setError('Attachments cannot be added to a finished ticket.')
      setSuccessMessage(null)
      return
    }

    setIsUploading(true)
    setError(null)
    setSuccessMessage(null)

    try {
      const uploaded = await uploadAttachment(ticketNumber, file, persona, accessToken)
      setAttachments((current) => [...current, uploaded])
      setSuccessMessage(`"${uploaded.filename}" uploaded successfully.`)
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Unable to upload attachment. Please try again.',
      )
    } finally {
      setIsUploading(false)
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    }
  }

  function onFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (file) {
      void handleFileUpload(file)
    }
  }

  function onDragOver(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    if (canUpload) {
      setIsDragOver(true)
    }
  }

  function onDragLeave(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setIsDragOver(false)
  }

  function onDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setIsDragOver(false)
    if (!canUpload) {
      return
    }
    const file = event.dataTransfer.files?.[0]
    if (file) {
      void handleFileUpload(file)
    }
  }

  async function handleDownload(attachment: TicketAttachmentResponse) {
    if (!accessToken) {
      setError('Your session is no longer valid. Please sign in again.')
      return
    }

    setDownloadingId(attachment.id)
    setError(null)

    try {
      await downloadAttachment(
        ticketNumber,
        attachment.id,
        attachment.filename,
        persona,
        accessToken,
      )
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Failed to download attachment. Please try again.',
      )
    } finally {
      setDownloadingId(null)
    }
  }

  return (
    <section className="ticket-attachments-card" aria-label="Ticket attachments">
      <header className="ticket-conversation-header">
        <div>
          <p className="eyebrow">Documentation</p>
          <h2>Attachments</h2>
        </div>

        <span
          className="attachment-quota-badge"
          aria-label={`Attachment count: ${attachments.length} of ${MAX_ATTACHMENTS}`}
        >
          {attachments.length} / {MAX_ATTACHMENTS} attachments
        </span>
      </header>

      {error && (
        <div className="attachment-alert attachment-alert-error" role="alert">
          {error}
        </div>
      )}

      {successMessage && (
        <div className="attachment-alert attachment-alert-success" role="status">
          {successMessage}
        </div>
      )}

      {/* Upload area or disabled state notice */}
      {isTicketFinished ? (
        <div className="attachment-notice-box" role="status">
          <p>
            This ticket is <strong>{ticketStatus}</strong>. New attachments cannot be uploaded to a
            completed ticket, but existing files remain available for download below.
          </p>
        </div>
      ) : isQuotaReached ? (
        <div className="attachment-notice-box attachment-notice-quota" role="status">
          <p>
            Attachment limit reached ({MAX_ATTACHMENTS} of {MAX_ATTACHMENTS}). No additional files can
            be uploaded to this ticket.
          </p>
        </div>
      ) : (
        <div
          className={`attachment-dropzone ${isDragOver ? 'attachment-dropzone-active' : ''} ${
            isUploading ? 'attachment-dropzone-uploading' : ''
          }`}
          onDragOver={onDragOver}
          onDragLeave={onDragLeave}
          onDrop={onDrop}
          onClick={() => {
            if (!isUploading) {
              fileInputRef.current?.click()
            }
          }}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => {
            if ((e.key === 'Enter' || e.key === ' ') && !isUploading) {
              e.preventDefault()
              fileInputRef.current?.click()
            }
          }}
          aria-label="Upload attachment dropzone"
        >
          <input
            ref={fileInputRef}
            type="file"
            className="attachment-hidden-input"
            accept={ALLOWED_EXTENSIONS.join(',')}
            onChange={onFileChange}
            disabled={isUploading}
            aria-hidden="true"
          />

          <div className="attachment-dropzone-content">
            <span className="attachment-dropzone-icon" aria-hidden="true">
              {isUploading ? '⏳' : '📁'}
            </span>
            <p className="attachment-dropzone-text">
              {isUploading ? (
                <strong>Uploading and validating file...</strong>
              ) : (
                <>
                  <strong>Click to upload</strong> or drag and drop files here
                </>
              )}
            </p>
            <p className="attachment-dropzone-hint">
              Supported formats: PDF, JPEG, PNG, WebP, TXT, LOG, CSV (Max 10 MB)
            </p>
          </div>
        </div>
      )}

      {/* Attachment List */}
      <div className="attachment-list-wrapper">
        {isLoading ? (
          <div className="attachment-loading-state" role="status">
            <span>Loading attachments...</span>
          </div>
        ) : attachments.length === 0 ? (
          <div className="attachment-empty-state">
            <p>No attachments yet.</p>
            <span>Upload diagnostic logs, screenshots, or documents to help resolve this ticket.</span>
          </div>
        ) : (
          <ul className="attachment-items" aria-label="Uploaded attachments">
            {attachments.map((item) => (
              <li key={item.id} className="attachment-item">
                <div className="attachment-item-leading" aria-hidden="true">
                  <span className="attachment-file-icon">
                    {getFileIcon(item.filename, item.contentType)}
                  </span>
                </div>

                <div className="attachment-item-details">
                  <strong className="attachment-item-name" title={item.filename}>
                    {item.filename}
                  </strong>
                  <div className="attachment-item-meta">
                    <span>{formatBytes(item.sizeBytes)}</span>
                    <span className="attachment-meta-dot">•</span>
                    <span>Uploaded by {item.uploadedByName}</span>
                    <span className="attachment-meta-dot">•</span>
                    <time dateTime={item.createdAt}>{formatDate(item.createdAt)}</time>
                  </div>
                </div>

                <div className="attachment-item-actions">
                  <button
                    type="button"
                    className="attachment-download-button"
                    onClick={() => void handleDownload(item)}
                    disabled={downloadingId === item.id}
                    aria-label={`Download ${item.filename}`}
                  >
                    {downloadingId === item.id ? (
                      <>
                        <span aria-hidden="true">⏳</span> Downloading...
                      </>
                    ) : (
                      <>
                        <span aria-hidden="true">⬇️</span> Download
                      </>
                    )}
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}
