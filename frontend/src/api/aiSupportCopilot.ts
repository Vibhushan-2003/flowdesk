
import { API_BASE_URL } from './config'
import type { AiSupportCopilotResponse } from '../types/aiSupportCopilot'

export class AiSupportCopilotApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'AiSupportCopilotApiError'
    this.status = status
  }
}

export async function askAiSupportCopilot(
  accessToken: string,
  ticketNumber: string,
  question: string,
): Promise<AiSupportCopilotResponse> {
  const normalizedQuestion = question.trim()

  if (!normalizedQuestion || normalizedQuestion.length > 1000) {
    throw new AiSupportCopilotApiError(
      'Enter a question of no more than 1000 characters.',
      400,
    )
  }

  const response = await fetch(
    `${API_BASE_URL}/api/support/tickets/${encodeURIComponent(ticketNumber)}/ai-copilot`,
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ question: normalizedQuestion }),
    },
  )

  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Enter a valid question of no more than 1000 characters.',
      401: 'Your session is no longer valid.',
      403: 'You do not have permission to use Support Copilot.',
      404: 'Ticket not found or it is no longer assigned to you.',
      503: 'Support Copilot is temporarily unavailable. You can still work on this ticket normally.',
    }

    throw new AiSupportCopilotApiError(
      messages[response.status] ?? 'Unable to get a Copilot answer. Please try again.',
      response.status,
    )
  }

  return response.json() as Promise<AiSupportCopilotResponse>
}
