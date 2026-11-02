import { ChevronLeft, ChevronRight } from 'lucide-react'

export interface PaginationProps {
  page: number // 0-based index
  totalPages: number
  totalElements?: number
  isFirst?: boolean
  isLast?: boolean
  onPageChange: (newPage: number) => void
  label?: string
  entityName?: string
  className?: string
}

export function Pagination({
  page = 0,
  totalPages,
  totalElements,
  isFirst,
  isLast,
  onPageChange,
  label = 'Pagination',
  entityName = 'items',
  className = '',
}: PaginationProps) {
  const safePage = typeof page === 'number' && !Number.isNaN(page) ? page : 0
  const first = isFirst ?? safePage <= 0
  const last = isLast ?? safePage >= totalPages - 1

  if (totalPages <= 0) return null

  return (
    <div className={`horizon-pagination-wrapper ${className}`}>
      {totalElements !== undefined && (
        <div className="horizon-pagination-info">
          Total {entityName}: <strong>{totalElements}</strong>
        </div>
      )}

      <nav className="pagination horizon-pagination" aria-label={label}>
        <button
          type="button"
          className="horizon-btn horizon-btn--secondary horizon-btn--sm horizon-pagination-btn"
          disabled={first}
          onClick={() => onPageChange(Math.max(0, safePage - 1))}
          aria-label="Previous page"
        >
          <ChevronLeft size={14} aria-hidden="true" />
          <span>Previous</span>
        </button>

        <span className="horizon-pagination-counter">
          Page {safePage + 1} of {Math.max(1, totalPages)}
        </span>

        <button
          type="button"
          className="horizon-btn horizon-btn--secondary horizon-btn--sm horizon-pagination-btn"
          disabled={last}
          onClick={() => onPageChange(safePage + 1)}
          aria-label="Next page"
        >
          <span>Next</span>
          <ChevronRight size={14} aria-hidden="true" />
        </button>
      </nav>
    </div>
  )
}

