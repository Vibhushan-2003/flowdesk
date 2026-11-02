import { useEffect } from 'react'
import { X } from 'lucide-react'
import { SidebarNavigation } from './SidebarNavigation'

export interface MobileDrawerProps {
  isOpen: boolean
  onClose: () => void
}

export function MobileDrawer({ isOpen, onClose }: MobileDrawerProps) {
  // Lock body scroll when mobile drawer is open
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
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && isOpen) {
        onClose()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onClose])

  if (!isOpen) return null

  return (
    <div className="horizon-mobile-drawer-portal" role="dialog" aria-modal="true" aria-label="Navigation drawer">
      <div
        className="horizon-drawer-backdrop"
        onClick={onClose}
        aria-hidden="true"
      />

      <div className="horizon-drawer-panel">
        <button
          type="button"
          className="horizon-drawer-close-btn"
          onClick={onClose}
          aria-label="Close navigation"
        >
          <X size={20} />
        </button>

        <SidebarNavigation
          isCollapsed={false}
          onToggleCollapse={() => {}}
          onNavigate={onClose}
          isMobile={true}
        />
      </div>
    </div>
  )
}

