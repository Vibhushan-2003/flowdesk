import { type ReactNode } from 'react'

export interface Column<T> {
  key: string
  header: ReactNode
  render?: (item: T) => ReactNode
  width?: string
  className?: string
  align?: 'left' | 'center' | 'right'
}

export interface DataTableProps<T> {
  columns: Column<T>[]
  data: T[]
  keyExtractor: (item: T) => string
  isLoading?: boolean
  emptyMessage?: string
  emptyAction?: ReactNode
  className?: string
  onRowClick?: (item: T) => void
}

export function DataTable<T>({
  columns,
  data,
  keyExtractor,
  isLoading = false,
  emptyMessage = 'No records found',
  emptyAction,
  className = '',
  onRowClick,
}: DataTableProps<T>) {
  return (
    <div className={`horizon-table-container ${className}`}>
      <table className="horizon-table">
        <thead>
          <tr>
            {columns.map((col) => (
              <th
                key={col.key}
                style={{ width: col.width, textAlign: col.align || 'left' }}
                className={col.className || ''}
              >
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={columns.length} className="horizon-table-loading">
                <div className="horizon-table-loading-content">
                  <span className="horizon-btn__spinner" />
                  <span>Loading records...</span>
                </div>
              </td>
            </tr>
          ) : data.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="horizon-table-empty">
                <p>{emptyMessage}</p>
                {emptyAction && <div style={{ marginTop: '0.75rem' }}>{emptyAction}</div>}
              </td>
            </tr>
          ) : (
            data.map((item) => {
              const isClickable = Boolean(onRowClick)
              return (
                <tr
                  key={keyExtractor(item)}
                  onClick={() => onRowClick?.(item)}
                  className={isClickable ? 'horizon-table-row--clickable' : ''}
                >
                  {columns.map((col) => {
                    const content = col.render
                      ? col.render(item)
                      : (item as Record<string, unknown>)[col.key] as ReactNode
                    return (
                      <td
                        key={col.key}
                        style={{ textAlign: col.align || 'left' }}
                        className={col.className || ''}
                      >
                        {content}
                      </td>
                    )
                  })}
                </tr>
              )
            })
          )}
        </tbody>
      </table>
    </div>
  )
}

