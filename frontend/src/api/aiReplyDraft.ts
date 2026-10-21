import { API_BASE_URL } from './config'

import type {
  AiReplyDraftResponse,
} from '../types/aiReplyDraft'

export class AiReplyDraftApiError extends Error {
  readonly status: number

  constructor(
    message: string,
    status: number,
  ) {
    super(message)

    this.name = 'AiReplyDraftApiError'
    this.status = status
  }
}

export async function generateAiReplyDraft(
  accessToken: string,
  ticketNumber: string,
): Promise<AiReplyDraftResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/support/tickets/${encodeURIComponent(
      ticketNumber,
    )}/ai-reply-draft`,
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new AiReplyDraftApiError(
        'Your session is no longer valid',
        response.status,
      )
    }

    if (response.status === 403) {
      throw new AiReplyDraftApiError(
        'You do not have permission to generate AI reply drafts',
        response.status,
      )
    }

    if (response.status === 404) {
      throw new AiReplyDraftApiError(
        'Ticket not found or it is no longer assigned to you',
        response.status,
      )
    }

    if (response.status === 409) {
      throw new AiReplyDraftApiError(
        'AI reply drafts cannot be generated for this finished ticket',
        response.status,
      )
    }

    if (response.status === 503) {
      throw new AiReplyDraftApiError(
        'AI reply drafting is temporarily unavailable. No message was sent.',
        response.status,
      )
    }

    throw new AiReplyDraftApiError(
      'Unable to generate an AI reply draft. Please try again.',
      response.status,
    )
  }

  return response.json() as Promise<
    AiReplyDraftResponse
  >
}