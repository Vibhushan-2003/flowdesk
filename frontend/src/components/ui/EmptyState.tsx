import { type ReactNode } from 'react'
import { Inbox } from 'lucide-react'

export interface EmptyStateProps {
  icon?: ReactNode
  title: string
  description?: string
  action?: ReactNode
  className?: string
}

export function EmptyState({
  icon = <Inbox size={32} />,
  title,
  description,
  action,
  className = '',
}: EmptyStateProps) {
  return (
    <div className={`horizon-empty-state ${className}`}>
      <div className="horizon-empty-icon horizon-empty-state__icon-wrapper" aria-hidden="true">
        {icon}
      </div>
      <h4 className="horizon-empty-title horizon-empty-state__title">{title}</h4>
      {description && (
        <p className="horizon-empty-desc horizon-empty-state__description">{description}</p>
      )}
      {action && <div className="horizon-empty-action">{action}</div>}
    </div>
  )
}
