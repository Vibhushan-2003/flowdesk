import type { SlaStatus } from '../types/ticket'

export function formatLabel(value: string | null | undefined): string {
  if (!value) return ''
  return value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/\b\w/g, (char) => char.toUpperCase())
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return 'Not started'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString()
}

export function formatOptionalDate(value: string | null | undefined): string {
  if (!value) return 'Not yet'
  return formatDate(value)
}

export function formatSlaStatus(status: SlaStatus): string {
  switch (status) {
    case 'MET':
      return 'Met'
    case 'BREACHED':
      return 'Breached'
    case 'PENDING':
      return 'Pending'
    case 'UNKNOWN':
      return 'Unknown'
  }
}

export function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return 'Not enough data'
  }
  return `${value.toFixed(1)}%`
}
