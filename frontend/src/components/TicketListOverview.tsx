
import type { TicketListFilters } from '../api/ticket'

interface TicketListOverviewProps {
  filters: TicketListFilters
  totalElements: number | null
  visibleCount: number
  isLoading: boolean
  onRefresh: () => void
}

export function TicketListOverview({
  filters,
  totalElements,
  visibleCount,
  isLoading,
  onRefresh,
}: TicketListOverviewProps) {
  const activeFilters: string[] = []

  if (filters.search?.trim()) {
    activeFilters.push(`Search: ${filters.search.trim()}`)
  }

  if (filters.status) {
    activeFilters.push(
      `Status: ${filters.status.replaceAll('_', ' ')}`,
    )
  }

  if (filters.priority) {
    activeFilters.push(`Priority: ${filters.priority}`)
  }

  if (filters.sort === 'oldest') {
    activeFilters.push('Order: Oldest first')
  }

  return (
    <section
      className="ticket-list-overview"
      aria-label="Ticket results summary"
    >
      <div className="ticket-list-overview-content">
        <div className="ticket-list-result-count">
          {isLoading ? (
            <span>Updating results...</span>
          ) : totalElements === null ? (
            <span>Results unavailable</span>
          ) : (
            <span>
              <strong>{totalElements}</strong>{' '}
              matching {totalElements === 1 ? 'ticket' : 'tickets'}
              <span className="ticket-list-visible-count">
                {' '}· Showing {visibleCount} on this page
              </span>
            </span>
          )}
        </div>

        <div
          className="ticket-list-active-filters"
          aria-label="Applied filters"
        >
          {activeFilters.length === 0 ? (
            <span className="ticket-list-no-filters">
              No filters applied
            </span>
          ) : (
            activeFilters.map((filter) => (
              <span
                className="ticket-list-filter-chip"
                key={filter}
              >
                {filter}
              </span>
            ))
          )}
        </div>
      </div>

      <button
        className="ticket-list-refresh-button"
        type="button"
        onClick={onRefresh}
        disabled={isLoading}
      >
        {isLoading ? 'Refreshing...' : '↻ Refresh'}
      </button>
    </section>
  )
}
