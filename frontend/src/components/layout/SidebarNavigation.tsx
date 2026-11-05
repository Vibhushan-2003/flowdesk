import {
  NavLink,
  useNavigate,
} from 'react-router-dom'
import {
  LayoutDashboard,
  Ticket,
  PlusCircle,
  Inbox,
  UserCheck,
  BookOpen,
  CheckSquare,
  Clock,
  BarChart3,
  Users,
  ShieldCheck,
  ShieldAlert,
  ChevronLeft,
  ChevronRight,
  LogOut,
  Layers,
} from 'lucide-react'
import { useAuth } from '../../auth/useAuth'
import { UserAvatar } from '../ui/UserAvatar'
import { Badge } from '../ui/Badge'

export interface SidebarNavigationProps {
  isCollapsed: boolean
  onToggleCollapse: () => void
  onNavigate?: () => void
  isMobile?: boolean
}

export function SidebarNavigation({
  isCollapsed,
  onToggleCollapse,
  onNavigate,
  isMobile = false,
}: SidebarNavigationProps) {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()

  const roles = user?.roles || []
  const isSupport = roles.includes('SUPPORT_ENGINEER')
  const isLead =
    roles.includes('TEAM_LEAD') ||
    roles.includes('ADMIN')
  const isAdmin = roles.includes('ADMIN')

  function handleSignOut() {
    signOut()
    navigate('/login')
  }

  const primaryRole = roles[0] || 'EMPLOYEE'

  return (
    <aside
      className={`horizon-sidebar ${
        isCollapsed && !isMobile ? 'is-collapsed' : 'is-expanded'
      } ${isMobile ? 'is-mobile' : ''}`}
      aria-label="Sidebar navigation"
    >
      {/* Brand Header */}
      <div className="horizon-sidebar-header">
        <NavLink
          to="/dashboard"
          className="horizon-brand-link"
          onClick={onNavigate}
        >
          <div className="horizon-brand-icon" aria-hidden="true">
            <Layers size={22} />
          </div>
          {(!isCollapsed || isMobile) && (
            <div className="horizon-brand-text">
              <span className="horizon-brand-name">FlowDesk</span>
              <span className="horizon-brand-badge">Horizon</span>
            </div>
          )}
        </NavLink>

        {!isMobile && (
          <button
            type="button"
            className="horizon-collapse-toggle-btn"
            onClick={onToggleCollapse}
            aria-label={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            title={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {isCollapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
          </button>
        )}
      </div>

      {/* Navigation Groups */}
      <nav className="horizon-sidebar-nav">
        {/* WORKSPACE */}
        <div className="horizon-nav-group">
          {(!isCollapsed || isMobile) && (
            <span className="horizon-nav-group-title">Workspace</span>
          )}
          <ul className="horizon-nav-list">
            <li>
              <NavLink
                to="/dashboard"
                end
                className={({ isActive }) =>
                  `horizon-nav-item ${isActive ? 'is-active' : ''}`
                }
                onClick={onNavigate}
                title={isCollapsed && !isMobile ? 'Dashboard' : undefined}
              >
                <LayoutDashboard size={18} className="horizon-nav-icon" />
                {(!isCollapsed || isMobile) && <span>Dashboard</span>}
              </NavLink>
            </li>

            <li>
              <NavLink
                to="/tickets"
                end
                className={({ isActive }) =>
                  `horizon-nav-item ${isActive ? 'is-active' : ''}`
                }
                onClick={onNavigate}
                title={isCollapsed && !isMobile ? 'My Tickets' : undefined}
              >
                <Ticket size={18} className="horizon-nav-icon" />
                {(!isCollapsed || isMobile) && <span>My Tickets</span>}
              </NavLink>
            </li>

            <li>
              <NavLink
                to="/tickets/new"
                className={({ isActive }) =>
                  `horizon-nav-item ${isActive ? 'is-active' : ''}`
                }
                onClick={onNavigate}
                title={isCollapsed && !isMobile ? 'Create Ticket' : undefined}
              >
                <PlusCircle size={18} className="horizon-nav-icon" />
                {(!isCollapsed || isMobile) && <span>Create Ticket</span>}
              </NavLink>
            </li>

            {isSupport && (
              <>
                <li>
                  <NavLink
                    to="/support/queue"
                    className={({ isActive }) =>
                      `horizon-nav-item ${isActive ? 'is-active' : ''}`
                    }
                    onClick={onNavigate}
                    title={isCollapsed && !isMobile ? 'Support Queue' : undefined}
                  >
                    <Inbox size={18} className="horizon-nav-icon" />
                    {(!isCollapsed || isMobile) && <span>Support Queue</span>}
                  </NavLink>
                </li>

                <li>
                  <NavLink
                    to="/support/tickets"
                    className={({ isActive }) =>
                      `horizon-nav-item ${isActive ? 'is-active' : ''}`
                    }
                    onClick={onNavigate}
                    title={isCollapsed && !isMobile ? 'Assigned to Me' : undefined}
                  >
                    <UserCheck size={18} className="horizon-nav-icon" />
                    {(!isCollapsed || isMobile) && <span>Assigned to Me</span>}
                  </NavLink>
                </li>
              </>
            )}

            <li>
              <NavLink
                to="/kb"
                className={({ isActive }) =>
                  `horizon-nav-item ${isActive ? 'is-active' : ''}`
                }
                onClick={onNavigate}
                title={isCollapsed && !isMobile ? 'Knowledge Base' : undefined}
              >
                <BookOpen size={18} className="horizon-nav-icon" />
                {(!isCollapsed || isMobile) && <span>Knowledge Base</span>}
              </NavLink>
            </li>
          </ul>
        </div>

        {/* OPERATIONS */}
        {isLead && (
          <div className="horizon-nav-group">
            {(!isCollapsed || isMobile) && (
              <span className="horizon-nav-group-title">Operations</span>
            )}
            <ul className="horizon-nav-list">
              <li>
                <NavLink
                  to="/approvals"
                  className={({ isActive }) =>
                    `horizon-nav-item ${isActive ? 'is-active' : ''}`
                  }
                  onClick={onNavigate}
                  title={isCollapsed && !isMobile ? 'Approvals' : undefined}
                >
                  <CheckSquare size={18} className="horizon-nav-icon" />
                  {(!isCollapsed || isMobile) && <span>Approvals</span>}
                </NavLink>
              </li>

              <li>
                <NavLink
                  to="/sla/dashboard"
                  className={({ isActive }) =>
                    `horizon-nav-item ${isActive ? 'is-active' : ''}`
                  }
                  onClick={onNavigate}
                  title={isCollapsed && !isMobile ? 'SLA Monitoring' : undefined}
                >
                  <Clock size={18} className="horizon-nav-icon" />
                  {(!isCollapsed || isMobile) && <span>SLA Monitoring</span>}
                </NavLink>
              </li>

              <li>
                <NavLink
                  to="/reports"
                  className={({ isActive }) =>
                    `horizon-nav-item ${isActive ? 'is-active' : ''}`
                  }
                  onClick={onNavigate}
                  title={isCollapsed && !isMobile ? 'Reports' : undefined}
                >
                  <BarChart3 size={18} className="horizon-nav-icon" />
                  {(!isCollapsed || isMobile) && <span>Reports</span>}
                </NavLink>
              </li>
            </ul>
          </div>
        )}

        {/* ADMINISTRATION */}
        {isAdmin && (
          <div className="horizon-nav-group">
            {(!isCollapsed || isMobile) && (
              <span className="horizon-nav-group-title">Administration</span>
            )}
            <ul className="horizon-nav-list">
              <li>
                <NavLink
                  to="/admin/users"
                  className={({ isActive }) =>
                    `horizon-nav-item ${isActive ? 'is-active' : ''}`
                  }
                  onClick={onNavigate}
                  title={isCollapsed && !isMobile ? 'User Management' : undefined}
                >
                  <Users size={18} className="horizon-nav-icon" />
                  {(!isCollapsed || isMobile) && <span>User Management</span>}
                </NavLink>
              </li>

              <li>
                <NavLink
                  to="/audit"
                  className={({ isActive }) =>
                    `horizon-nav-item ${isActive ? 'is-active' : ''}`
                  }
                  onClick={onNavigate}
                  title={isCollapsed && !isMobile ? 'Audit Trail' : undefined}
                >
                  <ShieldCheck size={18} className="horizon-nav-icon" />
                  {(!isCollapsed || isMobile) && <span>Audit Trail</span>}
                </NavLink>
              </li>

              <li>
                <NavLink
                  to="/admin/attachments"
                  className={({ isActive }) =>
                    `horizon-nav-item ${isActive ? 'is-active' : ''}`
                  }
                  onClick={onNavigate}
                  title={isCollapsed && !isMobile ? 'Attachment Security' : undefined}
                >
                  <ShieldAlert size={18} className="horizon-nav-icon" />
                  {(!isCollapsed || isMobile) && <span>Attachment Security</span>}
                </NavLink>
              </li>
            </ul>
          </div>
        )}
      </nav>

      {/* User Footer Profile */}
      <div className="horizon-sidebar-footer">
        <div className="horizon-user-profile-row">
          <NavLink
            to="/profile"
            className="horizon-sidebar-profile-link"
            onClick={onNavigate}
            title={isCollapsed && !isMobile ? 'View Profile' : undefined}
            style={{ display: 'flex', alignItems: 'center', gap: '10px', textDecoration: 'none', color: 'inherit', flex: 1, minWidth: 0 }}
          >
            <UserAvatar
              email={user?.email}
              size={isCollapsed && !isMobile ? 'sm' : 'md'}
            />
            {(!isCollapsed || isMobile) && (
              <div className="horizon-user-info-text">
                <span className="horizon-user-email" title={user?.email}>
                  {user?.email}
                </span>
                <Badge variant="neutral" size="sm" className="horizon-user-role-badge">
                  {primaryRole.replace('_', ' ')}
                </Badge>
              </div>
            )}
          </NavLink>
          <button
            type="button"
            className="horizon-sidebar-signout-btn"
            onClick={handleSignOut}
            aria-label="Sign out"
            title="Sign out"
          >
            <LogOut size={16} />
            <span className="sr-only">Sign out</span>
          </button>
        </div>
      </div>
    </aside>
  )
}

