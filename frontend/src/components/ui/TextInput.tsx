import {
  forwardRef,
  type InputHTMLAttributes,
  type ReactNode,
  useId,
} from 'react'

export interface TextInputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string | null
  helperText?: string
  leadingIcon?: ReactNode
  trailingIcon?: ReactNode
}

export const TextInput = forwardRef<HTMLInputElement, TextInputProps>(
  (
    {
      label,
      error,
      helperText,
      leadingIcon,
      trailingIcon,
      id,
      className = '',
      disabled,
      required,
      ...props
    },
    ref,
  ) => {
    const generatedId = useId()
    const inputId = id || generatedId
    const errorId = `${inputId}-error`
    const helperId = `${inputId}-helper`

    const isInvalid = Boolean(error)

    return (
      <div className={`horizon-form-field ${disabled ? 'is-disabled' : ''} ${className}`}>
        {label && (
          <label htmlFor={inputId} className="horizon-field-label">
            {label}
            {required && <span className="horizon-field-required" aria-hidden="true">*</span>}
          </label>
        )}

        <div className={`horizon-input-wrapper ${isInvalid ? 'is-invalid' : ''}`}>
          {leadingIcon && (
            <span className="horizon-input-icon-left" aria-hidden="true">
              {leadingIcon}
            </span>
          )}

          <input
            ref={ref}
            id={inputId}
            disabled={disabled}
            required={required}
            aria-invalid={isInvalid}
            aria-describedby={
              isInvalid ? errorId : helperText ? helperId : undefined
            }
            className="horizon-input"
            {...props}
          />

          {trailingIcon && (
            <span className="horizon-input-icon-right" aria-hidden="true">
              {trailingIcon}
            </span>
          )}
        </div>

        {error && (
          <p id={errorId} className="horizon-field-error form-error" role="alert">
            {error}
          </p>
        )}

        {!error && helperText && (
          <p id={helperId} className="horizon-field-helper">
            {helperText}
          </p>
        )}
      </div>
    )
  },
)

TextInput.displayName = 'TextInput'

