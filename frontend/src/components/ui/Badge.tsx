import { type HTMLAttributes, type ReactNode } from 'react'

export type BadgeVariant =
  | 'default'
  | 'primary'
  | 'success'
  | 'warning'
  | 'danger'
  | 'neutral'
  | 'purple'
  | 'amber'
  | 'internal'

export type BadgeSize = 'sm' | 'md'

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant
  size?: BadgeSize
  dot?: boolean
  icon?: ReactNode
}

export function Badge({
  variant = 'default',
  size = 'md',
  dot = false,
  icon,
  className = '',
  children,
  ...props
}: BadgeProps) {
  return (
    <span
      className={`horizon-badge horizon-badge--${variant} horizon-badge-${variant} horizon-badge--${size} horizon-badge-${size} ${className}`}
      {...props}
    >
      {dot && <span className="horizon-badge__dot horizon-badge-dot" aria-hidden="true" />}
      {icon && <span className="horizon-badge__icon horizon-badge-icon" aria-hidden="true">{icon}</span>}
      <span className="horizon-badge__label horizon-badge-label">{children}</span>
    </span>
  )
}
