import {
  forwardRef,
  type InputHTMLAttributes,
  type ReactNode,
  useId,
} from 'react'
import { Check } from 'lucide-react'

export interface CheckboxProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label?: ReactNode
  description?: ReactNode
}

export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(
  ({ label, description, id, className = '', disabled, checked, ...props }, ref) => {
    const generatedId = useId()
    const checkboxId = id || generatedId

    return (
      <div className={`horizon-checkbox-wrapper ${disabled ? 'is-disabled' : ''} ${className}`}>
        <div className="horizon-checkbox-input-box">
          <input
            ref={ref}
            type="checkbox"
            id={checkboxId}
            disabled={disabled}
            checked={checked}
            className="horizon-checkbox-native"
            {...props}
          />
          <div className="horizon-checkbox-custom" aria-hidden="true">
            <Check size={12} className="horizon-checkbox-check" />
          </div>
        </div>

        {(label || description) && (
          <label htmlFor={checkboxId} className="horizon-checkbox-label-wrap">
            {label && <span className="horizon-checkbox-label">{label}</span>}
            {description && (
              <span className="horizon-checkbox-desc">{description}</span>
            )}
          </label>
        )}
      </div>
    )
  },
)

Checkbox.displayName = 'Checkbox'

