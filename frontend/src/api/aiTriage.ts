import { API_BASE_URL } from './config'

import type {
  AiTriageRecommendationResponse,
} from '../types/aiTriage'

export class AiTriageApiError extends Error {
  readonly status: number

  constructor(
    message: string,
    status: number,
  ) {
    super(message)

    this.name = 'AiTriageApiError'
    this.status = status
  }
}

export async function getAiTriageRecommendation(
  accessToken: string,
  ticketNumber: string,
): Promise<AiTriageRecommendationResponse | null> {
  const response = await fetch(
    `${API_BASE_URL}/api/support/tickets/${encodeURIComponent(
      ticketNumber,
    )}/ai-triage`,
    {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  /*
   * Day 25 uses 404 both when there is no stored
   * recommendation and when the support engineer
   * no longer owns the ticket.
   *
   * The parent support page already verifies ticket
   * ownership before this component is displayed,
   * so an initial 404 here means there is no
   * recommendation to show yet.
   */
  if (response.status === 404) {
    return null
  }

  if (!response.ok) {
    if (response.status === 401) {
      throw new AiTriageApiError(
        'Your session is no longer valid',
        response.status,
      )
    }

    if (response.status === 403) {
      throw new AiTriageApiError(
        'You do not have permission to access AI triage',
        response.status,
      )
    }

    throw new AiTriageApiError(
      'Unable to load the AI triage recommendation.',
      response.status,
    )
  }

  return response.json() as Promise<
    AiTriageRecommendationResponse
  >
}

export async function generateAiTriageRecommendation(
  accessToken: string,
  ticketNumber: string,
): Promise<AiTriageRecommendationResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/support/tickets/${encodeURIComponent(
      ticketNumber,
    )}/ai-triage`,
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new AiTriageApiError(
        'Your session is no longer valid',
        response.status,
      )
    }

    if (response.status === 403) {
      throw new AiTriageApiError(
        'You do not have permission to generate AI triage',
        response.status,
      )
    }

    if (response.status === 404) {
      throw new AiTriageApiError(
        'This ticket is no longer assigned to you',
        response.status,
      )
    }

    if (response.status === 409) {
      throw new AiTriageApiError(
        'An AI recommendation already exists for this ticket',
        response.status,
      )
    }

    if (response.status === 503) {
      throw new AiTriageApiError(
        'AI triage is temporarily unavailable. Your ticket has not been changed.',
        response.status,
      )
    }

    throw new AiTriageApiError(
      'Unable to generate AI triage. Please try again.',
      response.status,
    )
  }

  return response.json() as Promise<
    AiTriageRecommendationResponse
  >
}