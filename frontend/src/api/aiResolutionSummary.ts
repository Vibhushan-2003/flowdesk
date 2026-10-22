import { API_BASE_URL } from './config'

import type {
  AiResolutionSummaryResponse,
} from '../types/aiResolutionSummary'

export class AiResolutionSummaryApiError
  extends Error {
  readonly status: number

  constructor(
    message: string,
    status: number,
  ) {
    super(message)

    this.name =
      'AiResolutionSummaryApiError'

    this.status = status
  }
}

export async function
generateAiResolutionSummary(
  accessToken: string,
  ticketNumber: string,
): Promise<AiResolutionSummaryResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/support/tickets/${encodeURIComponent(
      ticketNumber,
    )}/ai-resolution-summary`,
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new AiResolutionSummaryApiError(
        'Your session is no longer valid',
        response.status,
      )
    }

    if (response.status === 403) {
      throw new AiResolutionSummaryApiError(
        'You do not have permission to generate AI resolution summaries',
        response.status,
      )
    }

    if (response.status === 404) {
      throw new AiResolutionSummaryApiError(
        'Ticket not found or it is no longer assigned to you',
        response.status,
      )
    }

    if (response.status === 409) {
      throw new AiResolutionSummaryApiError(
        'AI resolution summaries can only be generated while the ticket is in progress',
        response.status,
      )
    }

    if (response.status === 503) {
      throw new AiResolutionSummaryApiError(
        'AI resolution summary drafting is temporarily unavailable. You can still write the summary manually.',
        response.status,
      )
    }

    throw new AiResolutionSummaryApiError(
      'Unable to generate an AI resolution summary. Please try again.',
      response.status,
    )
  }

  return response.json() as Promise<
    AiResolutionSummaryResponse
  >
}