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

export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`
}
