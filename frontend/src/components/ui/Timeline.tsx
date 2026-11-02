import { type ReactNode } from 'react'

export interface TimelineProps {
  children: ReactNode
  className?: string
}

export function Timeline({ children, className = '' }: TimelineProps) {
  return <div className={`horizon-timeline ${className}`}>{children}</div>
}

export interface TimelineItemProps {
  avatar?: ReactNode
  authorName: string
  timestamp: string | ReactNode
  badge?: ReactNode
  children: ReactNode
  isInternal?: boolean
  className?: string
}

export function TimelineItem({
  avatar,
  authorName,
  timestamp,
  badge,
  children,
  isInternal = false,
  className = '',
}: TimelineItemProps) {
  return (
    <article
      className={`horizon-timeline-item ticket-comment ${
        isInternal ? 'horizon-timeline-item--internal' : ''
      } ${className}`}
    >
      <div className="horizon-timeline-item__rail">
        <div className="horizon-timeline-item__avatar">
          {avatar}
        </div>
        <div className="horizon-timeline-item__line" />
      </div>

      <div className="horizon-timeline-item__card">
        <header className="horizon-timeline-item__header ticket-comment-header">
          <div className="horizon-timeline-item__meta">
            <strong className="horizon-timeline-item__author">{authorName}</strong>
            {badge && <span className="horizon-timeline-item__badge">{badge}</span>}
          </div>
          <span className="horizon-timeline-item__time">{timestamp}</span>
        </header>

        <div className="horizon-timeline-item__content">
          {children}
        </div>
      </div>
    </article>
  )
}

