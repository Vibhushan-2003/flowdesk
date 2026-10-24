import {
  API_BASE_URL,
} from './config'

import type {
  AuditAction,
  AuditEventsPage,
} from '../types/audit'

export interface AuditFilters {
  search?: string
  action?: AuditAction | ''
}

export async function getAuditEvents(
  accessToken: string,
  page = 0,
  size = 20,
  filters?: AuditFilters,
): Promise<AuditEventsPage> {
  const searchParams =
    new URLSearchParams({
      page: String(page),
      size: String(size),
    })

  if (filters?.search && filters.search.trim()) {
    searchParams.set('search', filters.search.trim())
  }

  if (filters?.action) {
    searchParams.set('action', filters.action)
  }

  const response =
    await fetch(
      `${API_BASE_URL}/api/audit/events?${searchParams.toString()}`,
      {
        method: 'GET',
        headers: {
          Authorization:
            `Bearer ${accessToken}`,
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
        'You do not have permission to view the audit trail',
      )
    }

    if (response.status === 400) {
      throw new Error(
        'Invalid audit history request',
      )
    }

    throw new Error(
      'Unable to load audit history. Please try again.',
    )
  }

  return response.json() as Promise<
    AuditEventsPage
  >
}