import { Badge } from './Badge'
import type { TicketStatus } from '../../types/ticket'
import { formatLabel } from '../../utils/formatters'

interface StatusBadgeProps {
  status: TicketStatus | string
  size?: 'sm' | 'md'
  className?: string
}

function normalizeStatus(status: string): string {
  const upper = status.toUpperCase()
  if (upper === 'NEW') return 'OPEN'
  if (upper === 'PENDING_CUSTOMER') return 'WAITING_FOR_USER'
  return upper
}

function getStatusVariant(status: string): 'primary' | 'warning' | 'purple' | 'success' | 'neutral' | 'danger' {
  switch (normalizeStatus(status)) {
    case 'OPEN':
      return 'primary'
    case 'ASSIGNED':
    case 'IN_PROGRESS':
    case 'PENDING_APPROVAL':
      return 'warning'
    case 'WAITING_FOR_USER':
      return 'purple'
    case 'RESOLVED':
    case 'CLOSED':
      return 'success'
    case 'CANCELLED':
      return 'neutral'
    default:
      return 'neutral'
  }
}

export function StatusBadge({ status, size = 'md', className = '' }: StatusBadgeProps) {
  const normalized = normalizeStatus(status)
  const variant = getStatusVariant(normalized)

  return (
    <Badge
      variant={variant}
      size={size}
      dot
      className={`status-badge status-badge-${normalized.toLowerCase().replaceAll('_', '-')} ${className}`}
    >
      {formatLabel(normalized)}
    </Badge>
  )
}
