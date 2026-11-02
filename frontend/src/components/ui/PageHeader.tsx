import { type ReactNode } from 'react'

export interface PageHeaderProps {
  title: string
  subtitle?: ReactNode
  badge?: ReactNode
  actions?: ReactNode
  breadcrumbs?: Array<{ label: string; href?: string }>
  className?: string
}

export function PageHeader({
  title,
  subtitle,
  badge,
  actions,
  breadcrumbs,
  className = '',
}: PageHeaderProps) {
  return (
    <header className={`horizon-page-header ${className}`}>
      {breadcrumbs && breadcrumbs.length > 0 && (
        <nav className="horizon-breadcrumbs" aria-label="Breadcrumb">
          <ol>
            {breadcrumbs.map((crumb, idx) => {
              const isLast = idx === breadcrumbs.length - 1
              return (
                <li key={crumb.label}>
                  {isLast || !crumb.href ? (
                    <span aria-current={isLast ? 'page' : undefined}>
                      {crumb.label}
                    </span>
                  ) : (
                    <a href={crumb.href}>{crumb.label}</a>
                  )}
                  {!isLast && <span className="horizon-breadcrumb-sep">/</span>}
                </li>
              )
            })}
          </ol>
        </nav>
      )}

      <div className="horizon-page-header-row">
        <div className="horizon-page-title-wrap">
          <div className="horizon-page-title-line">
            <h1 className="horizon-page-title">{title}</h1>
            {badge && <div className="horizon-page-title-badge">{badge}</div>}
          </div>
          {subtitle && (
            <p className="horizon-page-subtitle">{subtitle}</p>
          )}
        </div>

        {actions && (
          <div className="horizon-page-actions">{actions}</div>
        )}
      </div>
    </header>
  )
}

