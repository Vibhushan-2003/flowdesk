import { API_BASE_URL } from './config'

import type {
  AiTriageApplicationResponse,
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

export async function applyAiTriageRecommendation(
  accessToken: string,
  ticketNumber: string,
): Promise<AiTriageApplicationResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/support/tickets/${encodeURIComponent(
      ticketNumber,
    )}/ai-triage/apply`,
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
        'You do not have permission to apply AI triage',
        response.status,
      )
    }

    if (response.status === 404) {
      throw new AiTriageApiError(
        'The ticket or AI recommendation is no longer available',
        response.status,
      )
    }

    if (response.status === 409) {
      throw new AiTriageApiError(
        'The AI recommendation cannot be applied from the ticket current state',
        response.status,
      )
    }

    throw new AiTriageApiError(
      'Unable to apply the AI recommendation. Please try again.',
      response.status,
    )
  }

  return response.json() as Promise<
    AiTriageApplicationResponse
  >
}