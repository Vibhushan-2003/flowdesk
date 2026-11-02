import {
  forwardRef,
  type InputHTMLAttributes,
} from 'react'
import { Search, X } from 'lucide-react'

export interface SearchInputProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  onClear?: () => void
  shortcut?: string
}

export const SearchInput = forwardRef<HTMLInputElement, SearchInputProps>(
  ({ value, onChange, onClear, shortcut, className = '', placeholder = 'Search...', ...props }, ref) => {
    const hasValue = Boolean(value && String(value).length > 0)

    return (
      <div className={`horizon-search-wrapper ${className}`}>
        <Search size={16} className="horizon-search-icon" aria-hidden="true" />
        <input
          ref={ref}
          type="search"
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          className="horizon-search-input"
          {...props}
        />
        {hasValue && onClear && (
          <button
            type="button"
            onClick={onClear}
            className="horizon-search-clear-btn"
            aria-label="Clear search"
          >
            <X size={14} aria-hidden="true" />
          </button>
        )}
        {!hasValue && shortcut && (
          <kbd className="horizon-search-shortcut" aria-hidden="true">
            {shortcut}
          </kbd>
        )}
      </div>
    )
  },
)

SearchInput.displayName = 'SearchInput'

