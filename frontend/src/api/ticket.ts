
import { API_BASE_URL } from './config'

import type {
  ClaimTicketResponse,
  CreateTicketCommentRequest,
  CreateTicketRequest,
  PageResponse,
  ResolveTicketRequest,
  SupportTicketResponse,
  SupportTicketSummary,
  TicketCommentResponse,
  TicketPriority,
  TicketResponse,
  TicketStatus,
  TicketSummary,
  UpdateTicketStatusRequest,
} from '../types/ticket'

export interface TicketListFilters {
  search?: string
  status?: TicketStatus | ''
  priority?: TicketPriority | ''
  sort?: 'newest' | 'oldest'
}

function buildTicketListQuery(
  page: number,
  size: number,
  filters: TicketListFilters,
): string {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  })

  if (filters.search?.trim()) {
    params.set('search', filters.search.trim())
  }

  if (filters.status) {
    params.set('status', filters.status)
  }

  if (filters.priority) {
    params.set('priority', filters.priority)
  }

  if (filters.sort) {
    params.set('sort', filters.sort)
  }

  return params.toString()
}

export async function createTicket(
  request: CreateTicketRequest,
  accessToken: string,
): Promise<TicketResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/tickets`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify(request),
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error(
        'Your session is no longer valid',
      )
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
  filters: TicketListFilters = {},
): Promise<PageResponse<TicketSummary>> {
  const query = buildTicketListQuery(
    page,
    size,
    filters,
  )

  const response = await fetch(
    `${API_BASE_URL}/api/tickets/my?${query}`,
    {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error(
        'Your session is no longer valid',
      )
    }

    if (response.status === 400) {
      throw new Error(
        'Invalid ticket filters or pagination request',
      )
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
    `${API_BASE_URL}/api/tickets/${encodeURIComponent(
      ticketNumber,
    )}`,
    {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error(
        'Your session is no longer valid',
      )
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

export async function getTicketComments(
  accessToken: string,
  ticketNumber: string,
): Promise<TicketCommentResponse[]> {
  const response = await fetch(
    `${API_BASE_URL}/api/tickets/${encodeURIComponent(
      ticketNumber,
    )}/comments`,
    {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error(
        'Your session is no longer valid',
      )
    }

    if (response.status === 404) {
      throw new Error('Ticket not found')
    }

    throw new Error(
      'Unable to load the conversation. Please try again.',
    )
  }

  return response.json() as Promise<
    TicketCommentResponse[]
  >
}

export async function addTicketComment(
  accessToken: string,
  ticketNumber: string,
  request: CreateTicketCommentRequest,
): Promise<TicketCommentResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/tickets/${encodeURIComponent(
      ticketNumber,
    )}/comments`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify(request),
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error(
        'Your session is no longer valid',
      )
    }

    if (response.status === 404) {
      throw new Error('Ticket not found')
    }

    if (response.status === 400) {
      throw new Error(
        'Please enter a valid message',
      )
    }

    if (response.status === 409) {
      throw new Error(
        'New messages cannot be added to this finished ticket',
      )
    }

    throw new Error(
      'Unable to send your message. Please try again.',
    )
  }

  return response.json() as Promise<
    TicketCommentResponse
  >
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
      throw new Error(
        'Your session is no longer valid',
      )
    }

    if (response.status === 403) {
      throw new Error(
        'You do not have permission to access the support queue',
      )
    }

    if (response.status === 400) {
      throw new Error(
        'Invalid pagination request',
      )
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
    `${API_BASE_URL}/api/tickets/${encodeURIComponent(
      ticketNumber,
    )}/claim`,
    {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error(
        'Your session is no longer valid',
      )
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

  return response.json() as Promise<
    ClaimTicketResponse
  >
}

export async function getMyAssignedTickets(
  accessToken: string,
  page = 0,
  size = 10,
  filters: TicketListFilters = {},
): Promise<PageResponse<SupportTicketSummary>> {
  const query = buildTicketListQuery(
    page,
    size,
    filters,
  )

  const response = await fetch(
    `${API_BASE_URL}/api/support/tickets/my?${query}`,
    {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error(
        'Your session is no longer valid',
      )
    }

    if (response.status === 403) {
      throw new Error(
        'You do not have permission to access support tickets',
      )
    }

    if (response.status === 400) {
      throw new Error(
        'Invalid ticket filters or pagination request',
      )
    }

    throw new Error(
      'Unable to load your assigned tickets. Please try again.',
    )
  }

  return response.json() as Promise<
    PageResponse<SupportTicketSummary>
  >
}

export async function getSupportTicketHistory(
  accessToken: string,
  page = 0,
  size = 10,
  filters: TicketListFilters = {},
): Promise<PageResponse<SupportTicketSummary>> {
  const query = buildTicketListQuery(
    page,
    size,
    filters,
  )

  const response = await fetch(
    `${API_BASE_URL}/api/support/tickets/history?${query}`,
    {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error(
        'Your session is no longer valid',
      )
    }

    if (response.status === 403) {
      throw new Error(
        'You do not have permission to access support tickets',
      )
    }

    if (response.status === 400) {
      throw new Error(
        'Invalid ticket filters or pagination request',
      )
    }

    throw new Error(
      'Unable to load your ticket history. Please try again.',
    )
  }

  return response.json() as Promise<
    PageResponse<SupportTicketSummary>
  >
}

export async function getSupportTicket(
  accessToken: string,
  ticketNumber: string,
): Promise<SupportTicketResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/support/tickets/${encodeURIComponent(
      ticketNumber,
    )}`,
    {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error(
        'Your session is no longer valid',
      )
    }

    if (response.status === 403) {
      throw new Error(
        'You do not have permission to access support tickets',
      )
    }

    if (response.status === 404) {
      throw new Error(
        'Ticket not found or it is no longer assigned to you',
      )
    }

    throw new Error(
      'Unable to load the support ticket. Please try again.',
    )
  }

  return response.json() as Promise<
    SupportTicketResponse
  >
}

export async function getSupportTicketComments(
  accessToken: string,
  ticketNumber: string,
): Promise<TicketCommentResponse[]> {
  const response = await fetch(
    `${API_BASE_URL}/api/support/tickets/${encodeURIComponent(
      ticketNumber,
    )}/comments`,
    {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error(
        'Your session is no longer valid',
      )
    }

    if (response.status === 403) {
      throw new Error(
        'You do not have permission to access this conversation',
      )
    }

    if (response.status === 404) {
      throw new Error(
        'Ticket not found or it is no longer assigned to you',
      )
    }

    throw new Error(
      'Unable to load the conversation. Please try again.',
    )
  }

  return response.json() as Promise<
    TicketCommentResponse[]
  >
}

export async function addSupportTicketComment(
  accessToken: string,
  ticketNumber: string,
  request: CreateTicketCommentRequest,
): Promise<TicketCommentResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/support/tickets/${encodeURIComponent(
      ticketNumber,
    )}/comments`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify(request),
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error(
        'Your session is no longer valid',
      )
    }

    if (response.status === 403) {
      throw new Error(
        'You do not have permission to send messages on this ticket',
      )
    }

    if (response.status === 404) {
      throw new Error(
        'Ticket not found or it is no longer assigned to you',
      )
    }

    if (response.status === 400) {
      throw new Error(
        'Please enter a valid message',
      )
    }

    if (response.status === 409) {
      throw new Error(
        'New messages cannot be added to this finished ticket',
      )
    }

    throw new Error(
      'Unable to send your message. Please try again.',
    )
  }

  return response.json() as Promise<
    TicketCommentResponse
  >
}

export async function updateSupportTicketStatus(
  accessToken: string,
  ticketNumber: string,
  status: TicketStatus,
): Promise<SupportTicketResponse> {
  const request: UpdateTicketStatusRequest = {
    status,
  }

  const response = await fetch(
    `${API_BASE_URL}/api/support/tickets/${encodeURIComponent(
      ticketNumber,
    )}/status`,
    {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify(request),
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error(
        'Your session is no longer valid',
      )
    }

    if (response.status === 403) {
      throw new Error(
        'You do not have permission to update support tickets',
      )
    }

    if (response.status === 404) {
      throw new Error(
        'Ticket not found or it is no longer assigned to you',
      )
    }

    if (response.status === 400) {
      throw new Error(
        'Invalid ticket status request',
      )
    }

    if (response.status === 409) {
      throw new Error(
        'That status change is not allowed from the ticket current state',
      )
    }

    throw new Error(
      'Unable to update ticket status. Please try again.',
    )
  }

  return response.json() as Promise<
    SupportTicketResponse
  >
}

export async function resolveSupportTicket(
  accessToken: string,
  ticketNumber: string,
  resolutionSummary: string,
): Promise<SupportTicketResponse> {
  const request: ResolveTicketRequest = {
    resolutionSummary,
  }

  const response = await fetch(
    `${API_BASE_URL}/api/support/tickets/${encodeURIComponent(
      ticketNumber,
    )}/resolve`,
    {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
      body: JSON.stringify(request),
    },
  )

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error(
        'Your session is no longer valid',
      )
    }

    if (response.status === 403) {
      throw new Error(
        'You do not have permission to resolve support tickets',
      )
    }

    if (response.status === 404) {
      throw new Error(
        'Ticket not found or it is no longer assigned to you',
      )
    }

    if (response.status === 400) {
      throw new Error(
        'Enter a valid resolution summary before resolving the ticket',
      )
    }

    if (response.status === 409) {
      throw new Error(
        'This ticket can no longer be resolved from its current state',
      )
    }

    throw new Error(
      'Unable to resolve ticket. Please try again.',
    )
  }

  return response.json() as Promise<
    SupportTicketResponse
  >
}
