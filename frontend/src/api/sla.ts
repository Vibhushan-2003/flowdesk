import { API_BASE_URL } from './config'

import type {
  SlaDashboardResponse,
} from '../types/sla'

export async function getSlaDashboard(
  accessToken: string,
): Promise<SlaDashboardResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/sla/dashboard`,
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
        'You do not have permission to view SLA operations',
      )
    }

    throw new Error(
      'Unable to load SLA operations. Please try again.',
    )
  }

  return response.json() as Promise<
    SlaDashboardResponse
  >
}
