import { Badge, type BadgeSize } from './Badge'
import type { SlaStatus } from '../../types/ticket'
import { formatSlaStatus, getSlaBadgeVariant } from '../../utils/formatters'

export interface SlaBadgeProps {
  status: SlaStatus | null | undefined
  size?: BadgeSize
  dot?: boolean
  className?: string
  prefix?: string
}

export function SlaBadge({
  status,
  size = 'sm',
  dot = true,
  className = '',
  prefix,
}: SlaBadgeProps) {
  if (!status) {
    return <span style={{ color: 'var(--color-text-muted)' }}>&mdash;</span>
  }

  const variant = getSlaBadgeVariant(status)
  const label = formatSlaStatus(status)
  const displayText = prefix ? `${prefix}: ${label}` : label
  const normalizedClass = status ? status.toLowerCase() : 'unknown'

  return (
    <Badge
      variant={variant}
      size={size}
      dot={dot}
      className={`sla-badge sla-badge-${normalizedClass} ${className}`}
    >
      {displayText}
    </Badge>
  )
}

