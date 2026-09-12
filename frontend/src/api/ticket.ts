import { API_BASE_URL } from './config'
import type {
  CreateTicketRequest,
  TicketResponse,
} from '../types/ticket'

export async function createTicket(
  request: CreateTicketRequest,
  accessToken: string,
): Promise<TicketResponse> {
  const response = await fetch(`${API_BASE_URL}/api/tickets`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Your session is no longer valid')
    }

    if (response.status === 400) {
      throw new Error('Please check the ticket information you entered')
    }

    throw new Error('Unable to create ticket. Please try again.')
  }

  return response.json() as Promise<TicketResponse>
}