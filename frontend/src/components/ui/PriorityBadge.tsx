import { Badge } from './Badge'
import type { TicketPriority } from '../../types/ticket'
import { formatLabel } from '../../utils/formatters'

interface PriorityBadgeProps {
  priority: TicketPriority | string
  size?: 'sm' | 'md'
  className?: string
}

function getPriorityVariant(priority: string): 'neutral' | 'primary' | 'warning' | 'danger' {
  switch (priority.toUpperCase()) {
    case 'LOW':
      return 'neutral'
    case 'NORMAL':
    case 'MEDIUM':
      return 'primary'
    case 'HIGH':
      return 'warning'
    case 'CRITICAL':
    case 'URGENT':
      return 'danger'
    default:
      return 'neutral'
  }
}

export function PriorityBadge({ priority, size = 'md', className = '' }: PriorityBadgeProps) {
  const normalized = priority.toUpperCase()
  const variant = getPriorityVariant(normalized)

  return (
    <Badge
      variant={variant}
      size={size}
      dot
      className={`priority-badge priority-badge-${normalized.toLowerCase()} ${className}`}
    >
      {formatLabel(priority)}
    </Badge>
  )
}
