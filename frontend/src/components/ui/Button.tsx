import {
  forwardRef,
  type ButtonHTMLAttributes,
  type ReactNode,
} from 'react'
import { Loader2 } from 'lucide-react'

export type ButtonVariant =
  | 'primary'
  | 'secondary'
  | 'outline'
  | 'ghost'
  | 'danger'
  | 'subtle'

export type ButtonSize = 'sm' | 'md' | 'lg'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  isLoading?: boolean
  leftIcon?: ReactNode
  rightIcon?: ReactNode
  leadingIcon?: ReactNode
  trailingIcon?: ReactNode
  fullWidth?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      variant = 'primary',
      size = 'md',
      isLoading = false,
      leftIcon,
      rightIcon,
      leadingIcon,
      trailingIcon,
      fullWidth = false,
      disabled,
      className = '',
      children,
      ...props
    },
    ref,
  ) => {
    const iconStart = leftIcon || leadingIcon
    const iconEnd = rightIcon || trailingIcon

    const baseClass = 'horizon-btn'
    const variantClass = `horizon-btn--${variant}`
    const sizeClass = `horizon-btn--${size}`
    const widthClass = fullWidth ? 'horizon-btn--full-width' : ''
    const loadingClass = isLoading ? 'horizon-btn--loading' : ''

    const combinedClassName = [
      baseClass,
      variantClass,
      sizeClass,
      widthClass,
      loadingClass,
      className,
    ]
      .filter(Boolean)
      .join(' ')

    return (
      <button
        ref={ref}
        disabled={disabled || isLoading}
        className={combinedClassName}
        {...props}
      >
        {isLoading && (
          <Loader2
            className="horizon-btn__spinner"
            size={size === 'sm' ? 14 : size === 'lg' ? 18 : 16}
            aria-hidden="true"
          />
        )}
        {!isLoading && iconStart && (
          <span className="horizon-btn__icon--left" aria-hidden="true">
            {iconStart}
          </span>
        )}
        <span className="horizon-btn__text">{children}</span>
        {!isLoading && iconEnd && (
          <span className="horizon-btn__icon--right" aria-hidden="true">
            {iconEnd}
          </span>
        )}
      </button>
    )
  },
)

Button.displayName = 'Button'
