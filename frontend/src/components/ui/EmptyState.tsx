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
      <div className="horizon-empty-icon" aria-hidden="true">
        {icon}
      </div>
      <h4 className="horizon-empty-title">{title}</h4>
      {description && <p className="horizon-empty-desc">{description}</p>}
      {action && <div className="horizon-empty-action">{action}</div>}
    </div>
  )
}

