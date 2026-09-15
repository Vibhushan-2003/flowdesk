import { API_BASE_URL } from './config'

import type {
  ClaimTicketResponse,
  CreateTicketRequest,
  PageResponse,
  TicketResponse,
  TicketSummary,
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
      throw new Error(
        'Please check the ticket information you entered',
      )
    }

    throw new Error(
      'Unable to create ticket. Please try again.',
    )
  }

  return response.json() as Promise<TicketResponse>
}

export async function getMyTickets(
  accessToken: string,
  page = 0,
  size = 10,
): Promise<PageResponse<TicketSummary>> {
  const response = await fetch(
    `${API_BASE_URL}/api/tickets/my?page=${page}&size=${size}`,
    {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Your session is no longer valid')
    }

    if (response.status === 400) {
      throw new Error('Invalid pagination request')
    }

    throw new Error(
      'Unable to load tickets. Please try again.',
    )
  }

  return response.json() as Promise<
    PageResponse<TicketSummary>
  >
}

export async function getTicket(
  accessToken: string,
  ticketNumber: string,
): Promise<TicketResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/tickets/${encodeURIComponent(ticketNumber)}`,
    {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Your session is no longer valid')
    }

    if (response.status === 404) {
      throw new Error('Ticket not found')
    }

    throw new Error(
      'Unable to load ticket. Please try again.',
    )
  }

  return response.json() as Promise<TicketResponse>
}

export async function getSupportQueue(
  accessToken: string,
  page = 0,
  size = 10,
): Promise<PageResponse<TicketSummary>> {
  const response = await fetch(
    `${API_BASE_URL}/api/tickets/queue?page=${page}&size=${size}`,
    {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Your session is no longer valid')
    }

    if (response.status === 403) {
      throw new Error(
        'You do not have permission to access the support queue',
      )
    }

    if (response.status === 400) {
      throw new Error('Invalid pagination request')
    }

    throw new Error(
      'Unable to load the support queue. Please try again.',
    )
  }

  return response.json() as Promise<
    PageResponse<TicketSummary>
  >
}

export async function claimTicket(
  accessToken: string,
  ticketNumber: string,
): Promise<ClaimTicketResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/tickets/${encodeURIComponent(ticketNumber)}/claim`,
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error('Your session is no longer valid')
    }

    if (response.status === 403) {
      throw new Error(
        'You do not have permission to claim tickets',
      )
    }

    if (response.status === 404) {
      throw new Error('Ticket not found')
    }

    if (response.status === 409) {
      throw new Error(
        'This ticket has already been claimed by another engineer',
      )
    }

    throw new Error(
      'Unable to claim ticket. Please try again.',
    )
  }

  return response.json() as Promise<ClaimTicketResponse>
}