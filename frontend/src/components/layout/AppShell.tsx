import { useState, useCallback } from 'react'
import { Outlet } from 'react-router-dom'
import { SidebarNavigation } from './SidebarNavigation'
import { TopBar } from './TopBar'
import { MobileDrawer } from './MobileDrawer'

const SIDEBAR_COLLAPSED_KEY = 'flowdesk_horizon_sidebar_collapsed'

export function AppShell() {
  const [isCollapsed, setIsCollapsed] = useState<boolean>(() => {
    try {
      return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true'
    } catch {
      return false
    }
  })

  const [isMobileOpen, setIsMobileOpen] = useState(false)

  const handleToggleCollapse = useCallback(() => {
    setIsCollapsed((prev) => {
      const next = !prev
      try {
        localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(next))
      } catch {
        // Ignore storage errors
      }
      return next
    })
  }, [])

  const handleOpenMobile = useCallback(() => {
    setIsMobileOpen(true)
  }, [])

  const handleCloseMobile = useCallback(() => {
    setIsMobileOpen(false)
  }, [])

  return (
    <div
      className={`horizon-app-layout ${
        isCollapsed ? 'is-sidebar-collapsed' : 'is-sidebar-expanded'
      }`}
    >
      {/* Desktop Sidebar */}
      <SidebarNavigation
        isCollapsed={isCollapsed}
        onToggleCollapse={handleToggleCollapse}
      />

      {/* Mobile Drawer */}
      <MobileDrawer
        isOpen={isMobileOpen}
        onClose={handleCloseMobile}
      />

      {/* Main Workspace Area */}
      <div className="horizon-main-area">
        <TopBar onOpenMobileMenu={handleOpenMobile} />

        <main className="horizon-page-viewport" id="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

