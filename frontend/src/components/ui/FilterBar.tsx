import { type FormEvent, type ReactNode } from 'react'
import { Filter, RotateCcw } from 'lucide-react'

export interface FilterBarProps {
  children: ReactNode
  onSubmit?: (e: FormEvent) => void
  onReset?: () => void
  hasActiveFilters?: boolean
  submitLabel?: string
  resetLabel?: string
  ariaLabel?: string
  className?: string
}

export function FilterBar({
  children,
  onSubmit,
  onReset,
  hasActiveFilters = false,
  submitLabel = 'Apply filters',
  resetLabel = 'Clear',
  ariaLabel = 'Filters',
  className = '',
}: FilterBarProps) {
  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    onSubmit?.(e)
  }

  return (
    <form
      className={`horizon-filter-bar ${className}`}
      onSubmit={handleSubmit}
      aria-label={ariaLabel}
    >
      <div className="horizon-filter-fields">{children}</div>

      <div className="horizon-filter-actions">
        {onSubmit && (
          <button
            type="submit"
            className="horizon-btn horizon-btn--primary horizon-btn--sm"
          >
            <Filter size={14} aria-hidden="true" />
            <span>{submitLabel}</span>
          </button>
        )}

        {onReset && (
          <button
            type="button"
            className="horizon-btn horizon-btn--ghost horizon-btn--sm"
            onClick={onReset}
            disabled={!hasActiveFilters}
          >
            <RotateCcw size={14} aria-hidden="true" />
            <span>{resetLabel}</span>
          </button>
        )}
      </div>
    </form>
  )
}

