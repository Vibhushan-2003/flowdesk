import { type ReactNode } from 'react'
import { AlertCircle, RefreshCw } from 'lucide-react'
import { Button } from './Button'

export interface ErrorStateProps {
  title?: string
  message: string
  onRetry?: () => void
  isRetrying?: boolean
  extraAction?: ReactNode
  className?: string
}

export function ErrorState({
  title = 'Unable to load data',
  message,
  onRetry,
  isRetrying = false,
  extraAction,
  className = '',
}: ErrorStateProps) {
  return (
    <div className={`horizon-error-state ${className}`} role="alert">
      <div className="horizon-error-icon" aria-hidden="true">
        <AlertCircle size={28} />
      </div>
      <div className="horizon-error-text">
        <h4 className="horizon-error-title">{title}</h4>
        <p className="horizon-error-message">{message}</p>
      </div>
      {(onRetry || extraAction) && (
        <div className="horizon-error-actions">
          {onRetry && (
            <Button
              variant="outline"
              size="sm"
              onClick={onRetry}
              isLoading={isRetrying}
              leftIcon={<RefreshCw size={14} />}
            >
              Retry
            </Button>
          )}
          {extraAction}
        </div>
      )}
    </div>
  )
}

