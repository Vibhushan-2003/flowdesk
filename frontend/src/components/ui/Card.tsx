import { type HTMLAttributes, type ReactNode } from 'react'

export interface CardProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  title?: ReactNode
  subtitle?: ReactNode
  headerActions?: ReactNode
  footer?: ReactNode
  padded?: boolean
  hoverable?: boolean
}

export function Card({
  title,
  subtitle,
  headerActions,
  footer,
  padded = true,
  hoverable = false,
  className = '',
  children,
  ...props
}: CardProps) {
  const hasHeader = Boolean(title || subtitle || headerActions)

  return (
    <div
      className={`horizon-card ${
        hoverable ? 'horizon-card--interactive horizon-card-hoverable' : ''
      } ${className}`}
      {...props}
    >
      {hasHeader && (
        <div className="horizon-card-header horizon-card__header">
          <div className="horizon-card-titles">
            {title && (
              typeof title === 'string' ? (
                <h3 className="horizon-card-title horizon-card__title">{title}</h3>
              ) : (
                title
              )
            )}
            {subtitle && (
              typeof subtitle === 'string' ? (
                <p className="horizon-card-subtitle horizon-card__subtitle">{subtitle}</p>
              ) : (
                subtitle
              )
            )}
          </div>
          {headerActions && (
            <div className="horizon-card-actions horizon-card__actions">{headerActions}</div>
          )}
        </div>
      )}

      <div className={`horizon-card-body ${padded ? 'is-padded horizon-card--padding-md' : 'horizon-card--padding-none'}`}>
        {children}
      </div>

      {footer && (
        <div className="horizon-card-footer horizon-card__footer">{footer}</div>
      )}
    </div>
  )
}
