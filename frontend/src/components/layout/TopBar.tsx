import { useState, useRef, useEffect } from 'react'
import {
  Menu,
  LogOut,
  ChevronDown,
} from 'lucide-react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { ThemeSwitcher } from '../ui/ThemeSwitcher'
import { NotificationBell } from '../NotificationBell'
import { UserAvatar } from '../ui/UserAvatar'

export interface TopBarProps {
  onOpenMobileMenu: () => void
}

function getRouteBreadcrumb(pathname: string): { section: string; title: string } {
  if (pathname === '/dashboard') return { section: 'Workspace', title: 'Dashboard' }
  if (pathname === '/tickets') return { section: 'Workspace', title: 'My Tickets' }
  if (pathname === '/tickets/new') return { section: 'Workspace', title: 'Create Ticket' }
  if (pathname.startsWith('/tickets/')) return { section: 'Workspace', title: 'Ticket Details' }
  if (pathname === '/support/queue') return { section: 'Workspace', title: 'Support Queue' }
  if (pathname === '/support/tickets') return { section: 'Workspace', title: 'Assigned to Me' }
  if (pathname.startsWith('/support/tickets/')) return { section: 'Workspace', title: 'Support Workbench' }
  if (pathname === '/kb') return { section: 'Knowledge Base', title: 'Articles' }
  if (pathname.startsWith('/kb/articles/')) return { section: 'Knowledge Base', title: 'Article' }
  if (pathname === '/approvals') return { section: 'Operations', title: 'Approvals' }
  if (pathname === '/sla/dashboard') return { section: 'Operations', title: 'SLA Monitoring' }
  if (pathname === '/reports') return { section: 'Operations', title: 'Reports' }
  if (pathname === '/admin/users') return { section: 'Administration', title: 'User Management' }
  if (pathname === '/audit') return { section: 'Administration', title: 'Audit Trail' }
  return { section: 'FlowDesk', title: 'Workspace' }
}

export function TopBar({ onOpenMobileMenu }: TopBarProps) {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [isProfileOpen, setIsProfileOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  const { section, title } = getRouteBreadcrumb(location.pathname)

  function handleSignOut() {
    setIsProfileOpen(false)
    signOut()
    navigate('/login')
  }

  // Close profile dropdown on outside click
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsProfileOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  return (
    <header className="horizon-topbar">
      <div className="horizon-topbar-left">
        <button
          type="button"
          className="horizon-mobile-menu-btn"
          onClick={onOpenMobileMenu}
          aria-label="Open mobile navigation"
        >
          <Menu size={20} />
        </button>

        <nav className="horizon-topbar-breadcrumbs" aria-label="Current location">
          <span className="horizon-topbar-crumb-section">{section}</span>
          <span className="horizon-topbar-crumb-sep">/</span>
          <span className="horizon-topbar-crumb-title">{title}</span>
        </nav>
      </div>

      <div className="horizon-topbar-right">
        <ThemeSwitcher variant="toggle" />

        <div className="horizon-topbar-notification">
          <NotificationBell />
        </div>

        <div className="horizon-profile-menu-container" ref={menuRef}>
          <button
            type="button"
            className="horizon-profile-btn"
            onClick={() => setIsProfileOpen((prev) => !prev)}
            aria-expanded={isProfileOpen}
            aria-haspopup="menu"
            aria-label="User account menu"
          >
            <UserAvatar email={user?.email} size="sm" />
            <ChevronDown size={14} className="horizon-profile-chevron" />
          </button>

          {isProfileOpen && (
            <div className="horizon-profile-dropdown" role="menu">
              <div className="horizon-dropdown-user-header">
                <span className="horizon-dropdown-email">{user?.email}</span>
                <span className="horizon-dropdown-roles">
                  {user?.roles?.join(', ')}
                </span>
              </div>

              <div className="horizon-dropdown-divider" />

              <button
                type="button"
                role="menuitem"
                className="horizon-dropdown-item horizon-dropdown-signout"
                onClick={handleSignOut}
              >
                <LogOut size={16} />
                <span>Sign out</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}

