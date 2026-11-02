import {
  forwardRef,
  type SelectHTMLAttributes,
  useId,
} from 'react'
import { ChevronDown } from 'lucide-react'

export interface SelectOption {
  value: string
  label: string
}

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string
  options?: SelectOption[]
  error?: string | null
  helperText?: string
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  (
    {
      label,
      options,
      error,
      helperText,
      id,
      className = '',
      disabled,
      required,
      children,
      ...props
    },
    ref,
  ) => {
    const generatedId = useId()
    const selectId = id || generatedId
    const isInvalid = Boolean(error)

    return (
      <div className={`horizon-form-field ${disabled ? 'is-disabled' : ''} ${className}`}>
        {label && (
          <label htmlFor={selectId} className="horizon-field-label">
            {label}
            {required && <span className="horizon-field-required" aria-hidden="true">*</span>}
          </label>
        )}

        <div className={`horizon-select-wrapper ${isInvalid ? 'is-invalid' : ''}`}>
          <select
            ref={ref}
            id={selectId}
            disabled={disabled}
            required={required}
            aria-invalid={isInvalid}
            className="horizon-select"
            {...props}
          >
            {options
              ? options.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))
              : children}
          </select>
          <ChevronDown size={16} className="horizon-select-chevron" aria-hidden="true" />
        </div>

        {error && (
          <p className="horizon-field-error form-error" role="alert">
            {error}
          </p>
        )}

        {!error && helperText && (
          <p className="horizon-field-helper">{helperText}</p>
        )}
      </div>
    )
  },
)

Select.displayName = 'Select'

