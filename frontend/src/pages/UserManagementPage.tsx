import {
  type FormEvent,
  useEffect,
  useState,
} from 'react'
import {
  AlertCircle,
  Check,
  CheckCircle2,
  Copy,
  Mail,
  RefreshCw,
  Search,
  Users,
} from 'lucide-react'

import {
  createInvitation,
  getUsers,
  resendInvitation,
  updateUserRoles,
  updateUserStatus,
} from '../api/userManagement'
import { useAuth } from '../auth/useAuth'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { UserAvatar } from '../components/ui/UserAvatar'
import { Modal } from '../components/ui/Modal'
import { Pagination } from '../components/ui/Pagination'
import { EmptyState } from '../components/ui/EmptyState'
import type { Role } from '../types/auth'
import type {
  AdminUserResponse,
  UserDirectoryFilter,
  UserDirectoryPage,
  UserStatus,
} from '../types/userManagement'
import { formatDate } from '../utils/formatters'

const ALL_ROLES: Role[] = [
  'EMPLOYEE',
  'SUPPORT_ENGINEER',
  'TEAM_LEAD',
  'ADMIN',
]

const ROLE_LABELS: Record<Role, string> = {
  EMPLOYEE: 'Employee',
  SUPPORT_ENGINEER: 'Support Engineer',
  TEAM_LEAD: 'Team Lead',
  ADMIN: 'Administrator',
}

interface ShareableInvitation {
  userId: string
  userEmail: string
  userName: string
  invitationUrl: string
}

export function UserManagementPage() {
  const { accessToken, user } = useAuth()

  const [page, setPage] = useState(0)
  const [usersPage, setUsersPage] = useState<UserDirectoryPage | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  // Filter inputs
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedRole, setSelectedRole] = useState<Role | ''>('')
  const [selectedStatus, setSelectedStatus] = useState<UserStatus | ''>('')

  // Applied filters
  const [appliedFilters, setAppliedFilters] = useState<UserDirectoryFilter>({})
  const [refreshTrigger, setRefreshTrigger] = useState(0)


  // Invite User Modal state
  const [isInviteModalOpen, setIsInviteModalOpen] = useState(false)
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteFirstName, setInviteFirstName] = useState('')
  const [inviteLastName, setInviteLastName] = useState('')
  const [inviteRoles, setInviteRoles] = useState<Set<Role>>(() => new Set<Role>(['EMPLOYEE']))
  const [isInviting, setIsInviting] = useState(false)
  const [inviteError, setInviteError] = useState<string | null>(null)
  const [isResendingId, setIsResendingId] = useState<string | null>(null)
  const [shareableInvitation, setShareableInvitation] = useState<ShareableInvitation | null>(null)
  const [hasCopiedLink, setHasCopiedLink] = useState(false)

  // Edit Roles Modal state
  const [roleModalUser, setRoleModalUser] = useState<AdminUserResponse | null>(null)
  const [editRoles, setEditRoles] = useState<Set<Role>>(new Set())
  const [isSavingRoles, setIsSavingRoles] = useState(false)
  const [roleError, setRoleError] = useState<string | null>(null)

  // Status Confirmation Modal state
  const [statusModalUser, setStatusModalUser] = useState<AdminUserResponse | null>(null)
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false)

  // Load user directory
  useEffect(() => {
    let cancelled = false

    async function loadUsers() {
      if (!accessToken) {
        if (!cancelled) {
          setError('Your session is no longer valid')
          setIsLoading(false)
        }
        return
      }

      setIsLoading(true)
      setError(null)

      try {
        const response = await getUsers(accessToken, page, 20, appliedFilters)
        if (!cancelled) {
          setUsersPage(response)
        }
      } catch (caughtError) {
        if (cancelled) return
        setError(
          caughtError instanceof Error
            ? caughtError.message
            : 'Unable to load user directory.',
        )
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    void loadUsers()

    return () => {
      cancelled = true
    }
  }, [accessToken, page, appliedFilters, refreshTrigger])

  // Filter Form Handlers
  function handleFilterSubmit(event: FormEvent) {
    event.preventDefault()
    setPage(0)
    setAppliedFilters({
      search: searchQuery.trim() || undefined,
      role: selectedRole || undefined,
      status: selectedStatus || undefined,
    })
  }

  function handleResetFilters() {
    setSearchQuery('')
    setSelectedRole('')
    setSelectedStatus('')
    setAppliedFilters({})
    setPage(0)
  }


  // Invite User Handlers
  function openInviteModal() {
    setInviteEmail('')
    setInviteFirstName('')
    setInviteLastName('')
    setInviteRoles(new Set<Role>(['EMPLOYEE']))
    setInviteError(null)
    setIsInviteModalOpen(true)
  }

  function closeInviteModal() {
    setIsInviteModalOpen(false)
    setInviteError(null)
  }

  function toggleInviteRole(role: Role) {
    setInviteRoles((prev) => {
      const next = new Set(prev)
      if (next.has(role)) {
        if (next.size > 1) next.delete(role)
      } else {
        next.add(role)
      }
      return next
    })
  }

  async function handleInviteUserSubmit(event: FormEvent) {
    event.preventDefault()
    if (!accessToken) return

    const trimmedEmail = inviteEmail.trim()
    const trimmedFirstName = inviteFirstName.trim()
    const trimmedLastName = inviteLastName.trim()

    if (!trimmedEmail || !trimmedFirstName || !trimmedLastName) {
      setInviteError('Email, first name, and last name are required.')
      return
    }

    if (inviteRoles.size === 0) {
      setInviteError('At least one role must be assigned.')
      return
    }

    setIsInviting(true)
    setInviteError(null)

    try {
      await createInvitation(accessToken, {
        email: trimmedEmail,
        firstName: trimmedFirstName,
        lastName: trimmedLastName,
        roles: Array.from(inviteRoles),
      })

      setIsInviteModalOpen(false)
      setSuccessMessage(`Invitation dispatched to "${trimmedEmail}".`)
      setRefreshTrigger((prev) => prev + 1)
    } catch (caughtError) {
      setInviteError(
        caughtError instanceof Error ? caughtError.message : 'Failed to send invitation.',
      )
    } finally {
      setIsInviting(false)
    }
  }

  async function handleResendInvitation(targetUser: AdminUserResponse) {
    if (!accessToken) return

    setIsResendingId(targetUser.id)
    setError(null)

    try {
      const response = await resendInvitation(accessToken, targetUser.id)
      const targetName = `${targetUser.firstName} ${targetUser.lastName}`.trim() || targetUser.email

      if (response.invitationUrl) {
        setShareableInvitation({
          userId: targetUser.id,
          userEmail: targetUser.email,
          userName: targetName,
          invitationUrl: response.invitationUrl,
        })
        setHasCopiedLink(false)

        if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
          navigator.clipboard.writeText(response.invitationUrl)
            .then(() => setHasCopiedLink(true))
            .catch(() => {
              // Ignore clipboard failure on auto-copy
            })
        }
        setSuccessMessage(`Invitation re-issued for "${targetUser.email}". Shareable link generated.`)
      } else {
        setSuccessMessage(`Invitation re-issued for "${targetUser.email}".`)
      }
      setRefreshTrigger((prev) => prev + 1)
    } catch (caughtError) {
      setError(
        caughtError instanceof Error ? caughtError.message : 'Failed to resend invitation.',
      )
    } finally {
      setIsResendingId(null)
    }
  }

  async function handleCopyShareableLink() {
    if (!shareableInvitation) return
    try {
      if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
        await navigator.clipboard.writeText(shareableInvitation.invitationUrl)
        setHasCopiedLink(true)
      }
    } catch {
      // Fallback: clipboard permission denied
    }
  }

  function closeShareableInvitationModal() {
    setShareableInvitation(null)
    setHasCopiedLink(false)
  }

  // Edit Roles Handlers
  function openRoleModal(targetUser: AdminUserResponse) {
    setRoleModalUser(targetUser)
    setEditRoles(new Set(targetUser.roles))
    setRoleError(null)
  }

  function closeRoleModal() {
    setRoleModalUser(null)
    setRoleError(null)
  }

  function toggleEditRole(role: Role) {
    setEditRoles((prev) => {
      const next = new Set(prev)
      if (next.has(role)) {
        if (next.size > 1) next.delete(role)
      } else {
        next.add(role)
      }
      return next
    })
  }

  async function handleSaveRoles() {
    if (!accessToken || !roleModalUser) return

    if (editRoles.size === 0) {
      setRoleError('At least one role must be assigned.')
      return
    }

    setIsSavingRoles(true)
    setRoleError(null)

    try {
      await updateUserRoles(accessToken, roleModalUser.id, {
        roles: Array.from(editRoles),
      })

      closeRoleModal()
      setSuccessMessage(`Roles updated successfully for "${roleModalUser.email}".`)
      setRefreshTrigger((prev) => prev + 1)
    } catch (caughtError) {
      setRoleError(
        caughtError instanceof Error ? caughtError.message : 'Failed to update user roles.',
      )
    } finally {
      setIsSavingRoles(false)
    }
  }

  // Status Change Handlers
  async function handleConfirmStatusChange() {
    if (!accessToken || !statusModalUser) return

    const newStatus: UserStatus = statusModalUser.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
    setIsUpdatingStatus(true)

    try {
      await updateUserStatus(accessToken, statusModalUser.id, { status: newStatus })
      const actionWord = newStatus === 'ACTIVE' ? 'activated' : 'deactivated'
      setSuccessMessage(`User "${statusModalUser.email}" has been ${actionWord}.`)
      setStatusModalUser(null)
      setRefreshTrigger((prev) => prev + 1)
    } catch (caughtError) {
      setError(
        caughtError instanceof Error ? caughtError.message : 'Failed to update user status.',
      )
      setStatusModalUser(null)
    } finally {
      setIsUpdatingStatus(false)
    }
  }

  const hasActiveFilters = Boolean(
    appliedFilters.search || appliedFilters.role || appliedFilters.status,
  )

  return (
    <div className="user-management-page horizon-page-container">
      {/* Page Header */}
      <PageHeader
        title="User Management"
        subtitle="Manage directory accounts, assign role permissions, and control user access across the organization."
        actions={
          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
            <Button
              variant="primary"
              size="sm"
              onClick={openInviteModal}
              leadingIcon={<Mail size={14} />}
            >
              Invite User
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setRefreshTrigger((prev) => prev + 1)}
              leadingIcon={<RefreshCw size={14} className={isLoading ? 'horizon-spin' : ''} />}
              disabled={isLoading}
            >
              Refresh
            </Button>
          </div>
        }
      />

      {/* Global Alerts */}
      {successMessage && (
        <div className="horizon-auth-alert horizon-auth-alert--success" role="status" style={{ marginBottom: '1.5rem' }}>
          <CheckCircle2 size={18} className="horizon-auth-alert-icon" />
          <span style={{ flex: 1 }}>{successMessage}</span>
          <Button variant="ghost" size="sm" onClick={() => setSuccessMessage(null)}>
            Dismiss
          </Button>
        </div>
      )}

      {error && (
        <div className="horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ marginBottom: '1.5rem' }}>
          <AlertCircle size={18} className="horizon-auth-alert-icon" />
          <span>{error}</span>
        </div>
      )}

      {/* Search & Filter Toolbar */}
      <Card padded style={{ marginBottom: '1.5rem' }}>
        <form
          className="horizon-filter-form"
          onSubmit={handleFilterSubmit}
          style={{ display: 'flex', gap: '1rem', alignItems: 'flex-end', flexWrap: 'wrap' }}
          aria-label="User directory filters"
        >
          <div style={{ flex: '1 1 240px' }}>
            <label
              htmlFor="user-search-input"
              style={{ display: 'block', fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-medium)', color: 'var(--color-text-secondary)', marginBottom: '4px' }}
            >
              Search user
            </label>
            <div className="horizon-input-wrapper">
              <Search size={16} className="horizon-input-icon horizon-input-icon--leading" />
              <input
                id="user-search-input"
                type="search"
                className="horizon-input has-leading-icon"
                value={searchQuery}
                maxLength={100}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search by name or email..."
              />
            </div>
          </div>

          <div style={{ flex: '1 1 180px' }}>
            <label
              htmlFor="user-role-select"
              style={{ display: 'block', fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-medium)', color: 'var(--color-text-secondary)', marginBottom: '4px' }}
            >
              Role
            </label>
            <select
              id="user-role-select"
              className="horizon-select"
              value={selectedRole}
              onChange={(e) => setSelectedRole(e.target.value as Role | '')}
            >
              <option value="">All roles</option>
              {ALL_ROLES.map((role) => (
                <option key={role} value={role}>
                  {ROLE_LABELS[role]}
                </option>
              ))}
            </select>
          </div>

          <div style={{ flex: '1 1 160px' }}>
            <label
              htmlFor="user-status-select"
              style={{ display: 'block', fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-medium)', color: 'var(--color-text-secondary)', marginBottom: '4px' }}
            >
              Status
            </label>
            <select
              id="user-status-select"
              className="horizon-select"
              value={selectedStatus}
              onChange={(e) => setSelectedStatus(e.target.value as UserStatus | '')}
            >
              <option value="">All statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
              <option value="INVITED">Invited</option>
            </select>
          </div>

          <div style={{ display: 'flex', gap: '0.5rem', marginLeft: 'auto' }}>
            <Button variant="primary" size="sm" type="submit">
              Filter
            </Button>
            <Button variant="secondary" size="sm" type="button" onClick={handleResetFilters}>
              Clear
            </Button>
          </div>
        </form>
      </Card>

      {/* Directory Table Card */}
      <Card padded>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem', paddingBottom: '0.75rem', borderBottom: '1px solid var(--color-divider)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Users size={18} style={{ color: 'var(--color-primary)' }} />
            <h2 style={{ margin: 0, fontSize: 'var(--font-size-base)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)' }}>
              User Directory
            </h2>
          </div>
          {usersPage && (
            <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
              {usersPage.totalElements} registered {usersPage.totalElements === 1 ? 'user' : 'users'}
            </span>
          )}
        </div>

        {/* Loading state */}
        {isLoading && (
          <div style={{ textAlign: 'center', padding: '3.5rem 1rem', color: 'var(--color-text-secondary)' }}>
            <RefreshCw size={24} className="horizon-spin" style={{ margin: '0 auto 12px', display: 'block', color: 'var(--color-primary)' }} />
            <p>Loading user directory...</p>
          </div>
        )}

        {/* Empty state */}
        {!isLoading && usersPage && usersPage.content.length === 0 && (
          <EmptyState
            icon={<Users size={36} />}
            title={hasActiveFilters ? 'No users match criteria' : 'No users registered yet'}
            description={
              hasActiveFilters
                ? 'Try adjusting your search query or role/status filters.'
                : 'Click "+ Invite User" to add team members.'
            }
            action={
              hasActiveFilters ? (
                <Button variant="secondary" size="sm" onClick={handleResetFilters}>
                  Clear filters
                </Button>
              ) : undefined
            }
          />
        )}

        {/* Desktop Table View (>=768px) */}
        {!isLoading && usersPage && usersPage.content.length > 0 && (
          <>
            <div className="desktop-ticket-table-view" style={{ overflowX: 'auto' }}>
              <table className="horizon-data-table-simple">
                <thead>
                  <tr>
                    <th>User</th>
                    <th>Status</th>
                    <th>Roles</th>
                    <th>Created</th>
                    <th style={{ textAlign: 'right' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {usersPage.content.map((u) => {
                    const fullName = `${u.firstName || ''} ${u.lastName || ''}`.trim() || u.email
                    const isSelf = user?.userId === u.id

                    return (
                      <tr key={u.id}>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                            <UserAvatar name={fullName} email={u.email} size="sm" />
                            <div>
                              <div style={{ fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)' }}>
                                {fullName} {isSelf && <span style={{ fontSize: '11px', color: 'var(--color-primary)' }}>(You)</span>}
                              </div>
                              <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', fontFamily: 'var(--font-family-mono)' }}>
                                {u.email}
                              </div>
                            </div>
                          </div>
                        </td>
                        <td>
                          <Badge
                            variant={
                              u.status === 'ACTIVE'
                                ? 'success'
                                : u.status === 'INVITED'
                                ? 'warning'
                                : 'neutral'
                            }
                            size="sm"
                            dot
                          >
                            {u.status === 'ACTIVE' ? 'Active' : u.status === 'INVITED' ? 'Invited' : 'Inactive'}
                          </Badge>
                        </td>
                        <td>
                          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                            {u.roles.map((r) => (
                              <Badge key={r} variant="neutral" size="sm">
                                {ROLE_LABELS[r]}
                              </Badge>
                            ))}
                          </div>
                        </td>
                        <td style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', fontFamily: 'var(--font-family-mono)', whiteSpace: 'nowrap' }}>
                          {formatDate(u.createdAt)}
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          <div style={{ display: 'inline-flex', gap: '6px', alignItems: 'center' }}>
                            <Button
                              variant="secondary"
                              size="sm"
                              onClick={() => openRoleModal(u)}
                            >
                              Roles
                            </Button>

                            {u.status === 'INVITED' ? (
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => void handleResendInvitation(u)}
                                isLoading={isResendingId === u.id}
                                leadingIcon={<Mail size={13} />}
                              >
                                Resend
                              </Button>
                            ) : (
                              <Button
                                variant={u.status === 'ACTIVE' ? 'danger' : 'secondary'}
                                size="sm"
                                disabled={isSelf}
                                onClick={() => setStatusModalUser(u)}
                                title={isSelf ? 'You cannot deactivate your own account' : undefined}
                              >
                                {u.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                              </Button>
                            )}
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>

            {/* Mobile Card View (<768px) */}
            <div className="mobile-ticket-card-view" style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {usersPage.content.map((u) => {
                const fullName = `${u.firstName || ''} ${u.lastName || ''}`.trim() || u.email
                const isSelf = user?.userId === u.id

                return (
                  <div
                    key={u.id}
                    style={{
                      padding: '1rem',
                      background: 'var(--color-surface-subtle)',
                      borderRadius: 'var(--radius-md)',
                      border: '1px solid var(--color-border)',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '0.75rem',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <UserAvatar name={fullName} email={u.email} size="sm" />
                        <div>
                          <div style={{ fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)' }}>
                            {fullName} {isSelf && <span style={{ fontSize: '11px', color: 'var(--color-primary)' }}>(You)</span>}
                          </div>
                          <div style={{ fontSize: '11px', color: 'var(--color-text-muted)', fontFamily: 'var(--font-family-mono)' }}>
                            {u.email}
                          </div>
                        </div>
                      </div>
                      <Badge
                        variant={u.status === 'ACTIVE' ? 'success' : u.status === 'INVITED' ? 'warning' : 'neutral'}
                        size="sm"
                        dot
                      >
                        {u.status}
                      </Badge>
                    </div>

                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                      {u.roles.map((r) => (
                        <Badge key={r} variant="neutral" size="sm">
                          {ROLE_LABELS[r]}
                        </Badge>
                      ))}
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '6px', paddingTop: '8px', borderTop: '1px solid var(--color-divider)' }}>
                      <Button variant="secondary" size="sm" onClick={() => openRoleModal(u)}>
                        Roles
                      </Button>
                      {u.status === 'INVITED' ? (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => void handleResendInvitation(u)}
                          isLoading={isResendingId === u.id}
                        >
                          Resend Invite
                        </Button>
                      ) : (
                        <Button
                          variant={u.status === 'ACTIVE' ? 'danger' : 'secondary'}
                          size="sm"
                          disabled={isSelf}
                          onClick={() => setStatusModalUser(u)}
                        >
                          {u.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                        </Button>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>

            {/* Pagination */}
            {usersPage.totalPages > 1 && (
              <div style={{ marginTop: '1.5rem', display: 'flex', justifyContent: 'center' }}>
                <Pagination
                  page={page}
                  totalPages={usersPage.totalPages}
                  onPageChange={setPage}
                />
              </div>
            )}
          </>
        )}
      </Card>


      {/* Modal: Invite User */}
      <Modal
        isOpen={isInviteModalOpen}
        onClose={closeInviteModal}
        title="Invite New User"
        subtitle="Send an invitation email containing a secure account activation link."
      >
        <form onSubmit={handleInviteUserSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {inviteError && (
            <div className="horizon-auth-alert horizon-auth-alert--error" role="alert">
              <AlertCircle size={16} />
              <span>{inviteError}</span>
            </div>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <div>
              <label style={{ display: 'block', fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-medium)', marginBottom: '4px' }}>
                First Name *
              </label>
              <input
                type="text"
                className="horizon-input"
                value={inviteFirstName}
                onChange={(e) => setInviteFirstName(e.target.value)}
                required
              />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-medium)', marginBottom: '4px' }}>
                Last Name *
              </label>
              <input
                type="text"
                className="horizon-input"
                value={inviteLastName}
                onChange={(e) => setInviteLastName(e.target.value)}
                required
              />
            </div>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-medium)', marginBottom: '4px' }}>
              Email Address *
            </label>
            <input
              type="email"
              className="horizon-input"
              value={inviteEmail}
              onChange={(e) => setInviteEmail(e.target.value)}
              required
            />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-medium)', marginBottom: '8px' }}>
              Assigned Roles * (select at least one)
            </label>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '8px' }}>
              {ALL_ROLES.map((role) => (
                <label
                  key={role}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    padding: '8px 12px',
                    border: '1px solid var(--color-border)',
                    borderRadius: 'var(--radius-md)',
                    background: inviteRoles.has(role) ? 'var(--color-primary-subtle)' : 'var(--color-surface)',
                    cursor: 'pointer',
                    fontSize: 'var(--font-size-xs)',
                  }}
                >
                  <input
                    type="checkbox"
                    checked={inviteRoles.has(role)}
                    onChange={() => toggleInviteRole(role)}
                    style={{ cursor: 'pointer', accentColor: 'var(--color-primary)' }}
                  />
                  <span>{ROLE_LABELS[role]}</span>
                </label>
              ))}
            </div>
          </div>

          <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end', marginTop: '1rem', paddingTop: '1rem', borderTop: '1px solid var(--color-divider)' }}>
            <Button variant="secondary" size="sm" type="button" onClick={closeInviteModal}>
              Cancel
            </Button>
            <Button variant="primary" size="sm" type="submit" isLoading={isInviting} leadingIcon={<Mail size={14} />}>
              Send Invitation
            </Button>
          </div>
        </form>
      </Modal>

      {/* Modal: Edit Roles */}
      <Modal
        isOpen={roleModalUser !== null}
        onClose={closeRoleModal}
        title="Manage User Roles"
        subtitle={roleModalUser ? `Updating permissions for ${roleModalUser.email}` : undefined}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {roleError && (
            <div className="horizon-auth-alert horizon-auth-alert--error" role="alert">
              <AlertCircle size={16} />
              <span>{roleError}</span>
            </div>
          )}

          <p style={{ margin: 0, fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
            Select the role scopes permitted for this account. Users must retain at least one role.
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '8px' }}>
            {ALL_ROLES.map((role) => (
              <label
                key={role}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                  padding: '10px 12px',
                  border: '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                  background: editRoles.has(role) ? 'var(--color-primary-subtle)' : 'var(--color-surface)',
                  cursor: 'pointer',
                  fontSize: 'var(--font-size-xs)',
                }}
              >
                <input
                  type="checkbox"
                  checked={editRoles.has(role)}
                  onChange={() => toggleEditRole(role)}
                  style={{ cursor: 'pointer', accentColor: 'var(--color-primary)' }}
                />
                <span style={{ fontWeight: editRoles.has(role) ? 'var(--font-weight-semibold)' : 'normal' }}>
                  {ROLE_LABELS[role]}
                </span>
              </label>
            ))}
          </div>

          <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end', marginTop: '1rem', paddingTop: '1rem', borderTop: '1px solid var(--color-divider)' }}>
            <Button variant="secondary" size="sm" type="button" onClick={closeRoleModal}>
              Cancel
            </Button>
            <Button variant="primary" size="sm" onClick={() => void handleSaveRoles()} isLoading={isSavingRoles}>
              Save Roles
            </Button>
          </div>
        </div>
      </Modal>

      {/* Modal: Shareable Invitation Link (Zero-SMTP Demo) */}
      <Modal
        isOpen={shareableInvitation !== null}
        onClose={closeShareableInvitationModal}
        title="Invitation Link Ready"
        subtitle={shareableInvitation ? `Manual activation link for ${shareableInvitation.userEmail}` : undefined}
        footer={
          <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end', width: '100%' }}>
            <Button
              variant="secondary"
              size="sm"
              onClick={closeShareableInvitationModal}
            >
              Done
            </Button>
          </div>
        }
      >
        {shareableInvitation && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div
              className="horizon-auth-alert horizon-auth-alert--info"
              role="status"
              style={{ margin: 0 }}
            >
              <Mail size={16} className="horizon-auth-alert-icon" />
              <div style={{ fontSize: 'var(--font-size-xs)', lineHeight: 'var(--line-height-relaxed)' }}>
                <strong>Outbound email is disabled</strong> on this environment. Deliver this secure invitation link directly to the recipient to complete account activation or Google sign-in.
              </div>
            </div>

            <div
              style={{
                padding: '0.75rem',
                backgroundColor: 'var(--color-surface-subtle, rgba(0, 0, 0, 0.02))',
                borderRadius: 'var(--radius-md)',
                border: '1px solid var(--color-border)',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.25rem',
              }}
            >
              <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
                Target Recipient
              </div>
              <div style={{ fontWeight: 'var(--font-weight-semibold)', fontSize: 'var(--font-size-sm)' }}>
                {shareableInvitation.userName} ({shareableInvitation.userEmail})
              </div>
            </div>

            <div>
              <label
                htmlFor="invitation-url-input"
                style={{
                  display: 'block',
                  fontSize: 'var(--font-size-xs)',
                  fontWeight: 'var(--font-weight-medium)',
                  marginBottom: '6px',
                }}
              >
                Shareable Invitation URL
              </label>
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <input
                  id="invitation-url-input"
                  type="text"
                  readOnly
                  className="horizon-input"
                  value={shareableInvitation.invitationUrl}
                  onFocus={(e) => e.target.select()}
                  style={{ flex: 1, fontFamily: 'monospace', fontSize: 'var(--font-size-xs)' }}
                />
                <Button
                  type="button"
                  variant={hasCopiedLink ? 'secondary' : 'primary'}
                  size="sm"
                  onClick={() => void handleCopyShareableLink()}
                  leadingIcon={hasCopiedLink ? <Check size={14} /> : <Copy size={14} />}
                >
                  {hasCopiedLink ? 'Copied' : 'Copy Link'}
                </Button>
              </div>
              <p
                style={{
                  margin: '6px 0 0',
                  fontSize: 'var(--font-size-xs)',
                  color: 'var(--color-text-secondary)',
                }}
              >
                This single-use link expires in 24 hours. The token is never stored in browser storage.
              </p>
            </div>
          </div>
        )}
      </Modal>

      {/* Modal: Status Confirmation */}
      <Modal
        isOpen={statusModalUser !== null}
        onClose={() => setStatusModalUser(null)}
        title={statusModalUser?.status === 'ACTIVE' ? 'Deactivate User Account' : 'Activate User Account'}
        subtitle={statusModalUser ? `Account: ${statusModalUser.email}` : undefined}
        footer={
          <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
            <Button variant="secondary" size="sm" onClick={() => setStatusModalUser(null)}>
              Cancel
            </Button>
            <Button
              variant={statusModalUser?.status === 'ACTIVE' ? 'danger' : 'primary'}
              size="sm"
              onClick={() => void handleConfirmStatusChange()}
              isLoading={isUpdatingStatus}
            >
              {statusModalUser?.status === 'ACTIVE' ? 'Confirm Deactivation' : 'Confirm Activation'}
            </Button>
          </div>
        }
      >
        <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', lineHeight: 'var(--line-height-relaxed)', color: 'var(--color-text-primary)' }}>
          {statusModalUser?.status === 'ACTIVE'
            ? 'Are you sure you want to deactivate this account? The user will immediately be barred from authenticating and all active sessions will be revoked.'
            : 'Are you sure you want to reactivate this account? The user will immediately be permitted to log in with their existing credentials.'}
        </p>
      </Modal>
    </div>
  )
}
