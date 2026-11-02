import { useEffect, useRef, type ReactNode } from 'react'
import { X } from 'lucide-react'

export interface ModalProps {
  isOpen: boolean
  onClose: () => void
  title: ReactNode
  subtitle?: ReactNode
  children: ReactNode
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg' | 'xl'
  className?: string
}

export function Modal({
  isOpen,
  onClose,
  title,
  subtitle,
  children,
  footer,
  size = 'md',
  className = '',
}: ModalProps) {
  const modalRef = useRef<HTMLDivElement>(null)

  // Lock body scroll when modal is open
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = 'hidden'
    } else {
      document.body.style.overflow = ''
    }
    return () => {
      document.body.style.overflow = ''
    }
  }, [isOpen])

  // Close on Escape key
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape' && isOpen) {
        onClose()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onClose])

  if (!isOpen) return null

  return (
    <div
      className="horizon-modal-portal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="horizon-modal-title"
    >
      <div className="horizon-modal-backdrop" onClick={onClose} aria-hidden="true" />

      <div
        ref={modalRef}
        className={`horizon-modal-dialog horizon-modal-dialog--${size} ${className}`}
      >
        <header className="horizon-modal-header">
          <div>
            <h2 id="horizon-modal-title" className="horizon-modal-title">
              {title}
            </h2>
            {subtitle && <p className="horizon-modal-subtitle">{subtitle}</p>}
          </div>

          <button
            type="button"
            className="horizon-modal-close-btn"
            onClick={onClose}
            aria-label="Close dialog"
          >
            <X size={18} />
          </button>
        </header>

        <div className="horizon-modal-body">{children}</div>

        {footer && <footer className="horizon-modal-footer">{footer}</footer>}
      </div>
    </div>
  )
}

