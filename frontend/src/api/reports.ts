import { notifySessionExpired } from '../auth/sessionEvents'
import { API_BASE_URL } from './config'
import type { OperationalReport } from '../types/reports'

async function checkStatus(response: Response): Promise<void> {
  if (!response.ok) {
    if (response.status === 401) {
      notifySessionExpired()
      throw new Error('Your session is no longer valid')
    }
    if (response.status === 403) {
      throw new Error('You do not have permission to access operational reports')
    }

    let errorMessage = `Request failed (${response.status})`
    try {
      const errorJson = (await response.json()) as { message?: string; error?: string }
      if (errorJson.message) {
        errorMessage = errorJson.message
      } else if (errorJson.error) {
        errorMessage = errorJson.error
      }
    } catch {
      // Use fallback
    }
    throw new Error(errorMessage)
  }
}

export async function getOperationalReport(
  token: string,
  startDate?: string,
  endDate?: string,
): Promise<OperationalReport> {
  const params = new URLSearchParams()
  if (startDate) params.set('startDate', startDate)
  if (endDate) params.set('endDate', endDate)

  const url = `${API_BASE_URL}/api/reports/operational${params.toString() ? `?${params.toString()}` : ''}`
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })

  await checkStatus(response)
  return response.json() as Promise<OperationalReport>
}

export async function exportOperationalReportCsv(
  token: string,
  startDate?: string,
  endDate?: string,
): Promise<Blob> {
  const params = new URLSearchParams()
  if (startDate) params.set('startDate', startDate)
  if (endDate) params.set('endDate', endDate)

  const url = `${API_BASE_URL}/api/reports/operational/csv${params.toString() ? `?${params.toString()}` : ''}`
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })

  await checkStatus(response)
  return response.blob()
}

export async function exportTicketsCsv(
  token: string,
  startDate?: string,
  endDate?: string,
  limit = 1000,
): Promise<Blob> {
  const params = new URLSearchParams({
    limit: String(limit),
  })
  if (startDate) params.set('startDate', startDate)
  if (endDate) params.set('endDate', endDate)

  const url = `${API_BASE_URL}/api/reports/tickets/csv?${params.toString()}`
  const response = await fetch(url, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })

  await checkStatus(response)
  return response.blob()
}

export function downloadBlob(blob: Blob, filename: string): void {
  const url = window.URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  window.URL.revokeObjectURL(url)
}

