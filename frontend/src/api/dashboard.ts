import { API_BASE_URL } from './config'
import type { DashboardMetricsResponse } from '../types/dashboard'
import { notifySessionExpired } from '../auth/sessionEvents'

export async function getDashboardStats(
  accessToken: string,
): Promise<DashboardMetricsResponse> {
  const response = await fetch(`${API_BASE_URL}/api/dashboard/stats`, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  })

  if (!response.ok) {
    if (response.status === 401) {
      notifySessionExpired()
      throw new Error('Your session is no longer valid')
    }
    throw new Error('Unable to load operational dashboard statistics')
  }

  return response.json() as Promise<DashboardMetricsResponse>
}
