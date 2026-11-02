import {
  forwardRef,
  type ButtonHTMLAttributes,
  type ReactNode,
} from 'react'

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon: ReactNode
  label: string
  variant?: 'ghost' | 'outline' | 'subtle' | 'primary' | 'danger'
  size?: 'sm' | 'md' | 'lg'
}

export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(
  (
    {
      icon,
      label,
      variant = 'ghost',
      size = 'md',
      className = '',
      ...props
    },
    ref,
  ) => {
    return (
      <button
        ref={ref}
        type="button"
        aria-label={label}
        title={label}
        className={`horizon-icon-btn horizon-icon-btn-${variant} horizon-icon-btn-${size} ${className}`}
        {...props}
      >
        {icon}
      </button>
    )
  },
)

IconButton.displayName = 'IconButton'

