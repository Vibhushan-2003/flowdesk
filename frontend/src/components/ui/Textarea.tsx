import {
  forwardRef,
  type TextareaHTMLAttributes,
  useId,
} from 'react'

export interface TextareaProps
  extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string
  error?: string | null
  helperText?: string
  showCount?: boolean
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  (
    {
      label,
      error,
      helperText,
      showCount = false,
      maxLength,
      value,
      id,
      className = '',
      disabled,
      required,
      ...props
    },
    ref,
  ) => {
    const generatedId = useId()
    const textareaId = id || generatedId
    const isInvalid = Boolean(error)
    const currentLength = typeof value === 'string' ? value.length : 0

    return (
      <div className={`horizon-form-field ${disabled ? 'is-disabled' : ''} ${className}`}>
        {label && (
          <label htmlFor={textareaId} className="horizon-field-label">
            {label}
            {required && <span className="horizon-field-required" aria-hidden="true">*</span>}
          </label>
        )}

        <div className={`horizon-textarea-wrapper ${isInvalid ? 'is-invalid' : ''}`}>
          <textarea
            ref={ref}
            id={textareaId}
            value={value}
            maxLength={maxLength}
            disabled={disabled}
            required={required}
            aria-invalid={isInvalid}
            className="horizon-textarea"
            {...props}
          />
        </div>

        <div className="horizon-field-bottom">
          <div>
            {error && (
              <p className="horizon-field-error form-error" role="alert">
                {error}
              </p>
            )}
            {!error && helperText && (
              <p className="horizon-field-helper">{helperText}</p>
            )}
          </div>

          {showCount && maxLength && (
            <span className="horizon-char-counter">
              {currentLength}/{maxLength}
            </span>
          )}
        </div>
      </div>
    )
  },
)

Textarea.displayName = 'Textarea'

