import { API_BASE_URL } from './config'
import type { TicketAttachmentResponse } from '../types/attachment'

export type AttachmentPersona = 'employee' | 'support'

function getBasePath(persona: AttachmentPersona): string {
  return persona === 'support' ? '/api/support/tickets' : '/api/tickets'
}

export async function uploadAttachment(
  ticketNumber: string,
  file: File,
  persona: AttachmentPersona,
  accessToken: string,
): Promise<TicketAttachmentResponse> {
  const basePath = getBasePath(persona)
  const formData = new FormData()
  formData.append('file', file)

  let response: Response
  try {
    response = await fetch(
      `${API_BASE_URL}${basePath}/${encodeURIComponent(ticketNumber)}/attachments`,
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${accessToken}`,
          // Note: Content-Type boundary is set automatically by the browser with FormData
        },
        body: formData,
      },
    )
  } catch {
    throw new Error('Network connection error while uploading file. Please check your internet connection.')
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null)
    const backendMessage = errorBody?.message

    if (response.status === 401) {
      throw new Error('Your session is no longer valid. Please sign in again.')
    }
    if (response.status === 403) {
      throw new Error('You do not have permission to upload attachments to this ticket.')
    }
    if (response.status === 404) {
      throw new Error('Ticket not found or no longer accessible.')
    }
    if (response.status === 409) {
      throw new Error(backendMessage || 'Attachments cannot be added: ticket is finished or quota has been reached.')
    }
    if (response.status === 413) {
      throw new Error('Uploaded file exceeds maximum permitted size limit (10 MB).')
    }
    if (response.status === 400) {
      throw new Error(backendMessage || 'Invalid file format or rejected content.')
    }
    throw new Error(backendMessage || 'Failed to upload attachment. Please try again.')
  }

  return response.json() as Promise<TicketAttachmentResponse>
}

export async function listAttachments(
  ticketNumber: string,
  persona: AttachmentPersona,
  accessToken: string,
): Promise<TicketAttachmentResponse[]> {
  const basePath = getBasePath(persona)

  let response: Response
  try {
    response = await fetch(
      `${API_BASE_URL}${basePath}/${encodeURIComponent(ticketNumber)}/attachments`,
      {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      },
    )
  } catch {
    throw new Error('Network connection error while loading attachments.')
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null)
    const backendMessage = errorBody?.message

    if (response.status === 401) {
      throw new Error('Your session is no longer valid. Please sign in again.')
    }
    if (response.status === 403) {
      throw new Error('You do not have permission to view attachments for this ticket.')
    }
    if (response.status === 404) {
      throw new Error(backendMessage || 'Ticket not found or no longer accessible.')
    }
    throw new Error(backendMessage || 'Unable to load attachments. Please try again.')
  }

  return response.json() as Promise<TicketAttachmentResponse[]>
}

export async function downloadAttachment(
  ticketNumber: string,
  attachmentId: string,
  filename: string,
  persona: AttachmentPersona,
  accessToken: string,
): Promise<void> {
  const basePath = getBasePath(persona)

  let response: Response
  try {
    response = await fetch(
      `${API_BASE_URL}${basePath}/${encodeURIComponent(ticketNumber)}/attachments/${encodeURIComponent(attachmentId)}/download`,
      {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      },
    )
  } catch {
    throw new Error('Network error during file download. Please check your connection.')
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null)
    const backendMessage = errorBody?.message

    if (response.status === 401) {
      throw new Error('Your session is no longer valid. Please sign in again.')
    }
    if (response.status === 403) {
      throw new Error('You do not have permission to download this attachment.')
    }
    if (response.status === 404) {
      throw new Error(backendMessage || 'Attachment file is unavailable or not found.')
    }
    throw new Error(backendMessage || 'Unable to download attachment. Please try again.')
  }

  const blob = await response.blob()
  const blobUrl = window.URL.createObjectURL(blob)

  try {
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = filename || 'download'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  } finally {
    // Revoke object URL after browser initiates the save dialog
    setTimeout(() => {
      window.URL.revokeObjectURL(blobUrl)
    }, 1000)
  }
}
