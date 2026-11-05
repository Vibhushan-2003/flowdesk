import { type ReactNode } from 'react'

export interface KPIStatCardProps {
  label: string
  value: number | string
  icon?: ReactNode
  description?: string
  alert?: boolean
  onClick?: () => void
  className?: string
}

export function KPIStatCard({
  label,
  value,
  icon,
  description,
  alert = false,
  onClick,
  className = '',
}: KPIStatCardProps) {
  const isClickable = Boolean(onClick)

  return (
    <div
      onClick={onClick}
      role={isClickable ? 'button' : undefined}
      tabIndex={isClickable ? 0 : undefined}
      onKeyDown={
        isClickable
          ? (e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault()
                onClick?.()
              }
            }
          : undefined
      }
      className={`horizon-kpi-card dashboard-kpi-chip ${
        alert ? 'dashboard-kpi-chip-alert is-alert horizon-kpi-card--alert' : ''
      } ${isClickable ? 'is-clickable horizon-kpi-card--clickable' : ''} ${className}`}
    >
      <div className="horizon-kpi-top horizon-kpi-card__top">
        <span className="horizon-kpi-label dashboard-kpi-label horizon-kpi-card__title">
          {label}
        </span>
        {icon && (
          <div
            className={`horizon-kpi-icon-wrap horizon-kpi-card__icon-wrapper ${
              alert ? 'horizon-kpi-card__icon-wrapper--danger' : 'horizon-kpi-card__icon-wrapper--primary'
            }`}
            aria-hidden="true"
          >
            {icon}
          </div>
        )}
      </div>

      <div className="horizon-kpi-value dashboard-kpi-value horizon-kpi-card__value">
        {value}
      </div>

      {description && (
        <div className="horizon-kpi-desc horizon-kpi-card__subtitle">
          {description}
        </div>
      )}
    </div>
  )
}
