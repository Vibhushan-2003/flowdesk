import {
  API_BASE_URL,
} from './config'

import type {
  AuditEventsPage,
} from '../types/audit'

export async function getAuditEvents(
  accessToken: string,
  page = 0,
  size = 20,
): Promise<AuditEventsPage> {
  const searchParams =
    new URLSearchParams({
      page: String(page),
      size: String(size),
    })

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