import {
  type FormEvent,
  useEffect,
  useState,
} from 'react'
import { useNavigate } from 'react-router-dom'

import {
  createInvitation,
  createUser,
  getUsers,
  resendInvitation,
  updateUserRoles,
  updateUserStatus,
} from '../api/userManagement'
import { useAuth } from '../auth/useAuth'
import { NotificationBell } from '../components/NotificationBell'
import type { Role } from '../types/auth'
import type {
  AdminUserResponse,
  UserDirectoryFilter,
  UserDirectoryPage,
  UserStatus,
} from '../types/userManagement'

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

export function UserManagementPage() {
  const { accessToken, user, signOut } = useAuth()
  const navigate = useNavigate()

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

  // Create User Modal state
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [createEmail, setCreateEmail] = useState('')
  const [createPassword, setCreatePassword] = useState('')
  const [createFirstName, setCreateFirstName] = useState('')
  const [createLastName, setCreateLastName] = useState('')
  const [createRoles, setCreateRoles] = useState<Set<Role>>(
    () => new Set<Role>(['EMPLOYEE']),
  )
  const [isCreating, setIsCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  // Invite User Modal state
  const [isInviteModalOpen, setIsInviteModalOpen] = useState(false)
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteFirstName, setInviteFirstName] = useState('')
  const [inviteLastName, setInviteLastName] = useState('')
  const [inviteRoles, setInviteRoles] = useState<Set<Role>>(
    () => new Set<Role>(['EMPLOYEE']),
  )
  const [isInviting, setIsInviting] = useState(false)
  const [inviteError, setInviteError] = useState<string | null>(null)
  const [isResendingId, setIsResendingId] = useState<string | null>(null)

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
        if (!cancelled) {
          setError(
            caughtError instanceof Error
              ? caughtError.message
              : 'Failed to load user directory.',
          )
        }
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

  // Create User Handlers
  function openCreateModal() {
    setCreateEmail('')
    setCreatePassword('')
    setCreateFirstName('')
    setCreateLastName('')
    setCreateRoles(new Set<Role>(['EMPLOYEE']))
    setCreateError(null)
    setIsCreateModalOpen(true)
  }

  function closeCreateModal() {
    setIsCreateModalOpen(false)
    setCreateError(null)
  }

  function toggleCreateRole(role: Role) {
    setCreateRoles((prev) => {
      const next = new Set(prev)
      if (next.has(role)) {
        if (next.size > 1) {
          next.delete(role)
        }
      } else {
        next.add(role)
      }
      return next
    })
  }

  async function handleCreateUserSubmit(event: FormEvent) {
    event.preventDefault()
    if (!accessToken) return

    const trimmedEmail = createEmail.trim()
    const trimmedFirstName = createFirstName.trim()
    const trimmedLastName = createLastName.trim()

    if (!trimmedEmail || !createPassword || !trimmedFirstName || !trimmedLastName) {
      setCreateError('All fields are required.')
      return
    }

    if (createPassword.length < 8 || createPassword.length > 72) {
      setCreateError('Password must be between 8 and 72 characters.')
      return
    }

    if (createRoles.size === 0) {
      setCreateError('At least one role must be assigned.')
      return
    }

    setIsCreating(true)
    setCreateError(null)

    try {
      await createUser(accessToken, {
        email: trimmedEmail,
        password: createPassword,
        firstName: trimmedFirstName,
        lastName: trimmedLastName,
        roles: Array.from(createRoles),
      })

      setIsCreateModalOpen(false)
      setSuccessMessage(`User "${trimmedEmail}" created successfully.`)
      setRefreshTrigger((prev) => prev + 1)
    } catch (caughtError) {
      setCreateError(
        caughtError instanceof Error
          ? caughtError.message
          : 'Failed to create user.',
      )
    } finally {
      setIsCreating(false)
    }
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
        if (next.size > 1) {
          next.delete(role)
        }
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
      setSuccessMessage(`Invitation created for "${trimmedEmail}". Activation email dispatched.`)
      setRefreshTrigger((prev) => prev + 1)
    } catch (caughtError) {
      setInviteError(
        caughtError instanceof Error
          ? caughtError.message
          : 'Failed to send invitation.',
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
      await resendInvitation(accessToken, targetUser.id)
      setSuccessMessage(`Invitation re-issued for "${targetUser.email}". Activation email dispatched.`)
      setRefreshTrigger((prev) => prev + 1)
    } catch (caughtError) {
      setError(
        caughtError instanceof Error
          ? caughtError.message
          : 'Failed to resend invitation.',
      )
    } finally {
      setIsResendingId(null)
    }
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
    if (!roleModalUser) return
    const isSelf = roleModalUser.id === user?.userId

    if (isSelf && role === 'ADMIN') {
      return // Self-demotion not allowed
    }

    setEditRoles((prev) => {
      const next = new Set(prev)
      if (next.has(role)) {
        next.delete(role)
      } else {
        next.add(role)
      }
      return next
    })
  }

  async function handleSaveRoles(event: FormEvent) {
    event.preventDefault()
    if (!accessToken || !roleModalUser) return

    if (editRoles.size === 0) {
      setRoleError('User must have at least one role.')
      return
    }

    setIsSavingRoles(true)
    setRoleError(null)

    try {
      await updateUserRoles(accessToken, roleModalUser.id, {
        roles: Array.from(editRoles),
      })

      closeRoleModal()
      setSuccessMessage(
        `Roles updated successfully for "${roleModalUser.email}".`,
      )
      setRefreshTrigger((prev) => prev + 1)
    } catch (caughtError) {
      setRoleError(
        caughtError instanceof Error
          ? caughtError.message
          : 'Failed to update user roles.',
      )
    } finally {
      setIsSavingRoles(false)
    }
  }

  // Status Change Handlers
  function openStatusModal(targetUser: AdminUserResponse) {
    setStatusModalUser(targetUser)
  }

  function closeStatusModal() {
    setStatusModalUser(null)
  }

  async function handleConfirmStatusChange() {
    if (!accessToken || !statusModalUser) return

    const newStatus: UserStatus =
      statusModalUser.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'

    setIsUpdatingStatus(true)

    try {
      await updateUserStatus(accessToken, statusModalUser.id, {
        status: newStatus,
      })

      const actionWord = newStatus === 'ACTIVE' ? 'activated' : 'deactivated'
      setSuccessMessage(
        `User "${statusModalUser.email}" has been ${actionWord}.`,
      )
      closeStatusModal()
      setRefreshTrigger((prev) => prev + 1)
    } catch (caughtError) {
      setError(
        caughtError instanceof Error
          ? caughtError.message
          : 'Failed to update user status.',
      )
      closeStatusModal()
    } finally {
      setIsUpdatingStatus(false)
    }
  }

  const hasActiveFilters = Boolean(
    appliedFilters.search || appliedFilters.role || appliedFilters.status,
  )

  return (
    <main className="dashboard-page">
      <header className="dashboard-header">
        <div>
          <p className="eyebrow">Administration</p>
          <h1>User management</h1>
          <p className="dashboard-subtitle">
            Manage user accounts, assign roles, and control account activation status.
          </p>
        </div>

        <div className="dashboard-header-actions">
          <NotificationBell />

          <button
            type="button"
            className="primary-button"
            onClick={openCreateModal}
          >
            + Create user
          </button>

          <button
            type="button"
            className="primary-button"
            style={{ background: '#059669', borderColor: '#059669' }}
            onClick={openInviteModal}
          >
            + Invite user
          </button>

          <button
            type="button"
            className="dashboard-secondary-button"
            onClick={() => navigate('/dashboard')}
          >
            Dashboard
          </button>

          <button
            type="button"
            onClick={() => {
              signOut()
              navigate('/login')
            }}
          >
            Sign out
          </button>
        </div>
      </header>

      {successMessage && (
        <section
          className="user-card"
          style={{
            background: '#ecfdf5',
            border: '1px solid #a7f3d0',
            color: '#065f46',
            padding: '14px 20px',
            marginBottom: '16px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <span>{successMessage}</span>
          <button
            type="button"
            className="secondary-button"
            style={{ padding: '4px 10px', fontSize: '0.8rem' }}
            onClick={() => setSuccessMessage(null)}
          >
            Dismiss
          </button>
        </section>
      )}

      {error && (
        <section className="user-card" style={{ marginBottom: '16px' }}>
          <p className="notification-error">{error}</p>
        </section>
      )}

      {/* Filter and Search Bar */}
      <form
        className="audit-filters-form"
        onSubmit={handleFilterSubmit}
        aria-label="User directory filters"
      >
        <label>
          Search user
          <input
            type="search"
            value={searchQuery}
            maxLength={100}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by name or email..."
          />
        </label>

        <label>
          Role
          <select
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
        </label>

        <label>
          Status
          <select
            value={selectedStatus}
            onChange={(e) => setSelectedStatus(e.target.value as UserStatus | '')}
          >
            <option value="">All statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
            <option value="INVITED">Invited</option>
          </select>
        </label>

        <button type="submit" className="primary-button">
          Filter
        </button>

        <button
          type="button"
          className="secondary-button"
          onClick={handleResetFilters}
        >
          Clear
        </button>
      </form>

      {/* Directory Table */}
      <section className="user-card">
        <div className="account-card-heading">
          <div>
            <p className="dashboard-card-label">Directory</p>
            <h2>User directory</h2>
          </div>

          {usersPage && (
            <span className="account-status">
              {usersPage.totalElements} users
            </span>
          )}
        </div>

        {isLoading && <p>Loading users...</p>}

        {!isLoading && usersPage && usersPage.content.length === 0 && (
          <div className="empty-state">
            <h2>{hasActiveFilters ? 'No users found' : 'No users registered yet'}</h2>
            <p>
              {hasActiveFilters
                ? 'Try adjusting your search query or role/status filters.'
                : 'Click "+ Create user" to add the first user account.'}
            </p>
            {hasActiveFilters && (
              <button
                type="button"
                className="secondary-button"
                onClick={handleResetFilters}
                style={{ marginTop: '0.75rem' }}
              >
                Clear filters
              </button>
            )}
          </div>
        )}

        {!isLoading && usersPage && usersPage.content.length > 0 && (
          <div style={{ overflowX: 'auto', marginTop: '16px' }}>
            <table
              className="user-management-table"
              style={{
                width: '100%',
                borderCollapse: 'collapse',
                textAlign: 'left',
              }}
            >
              <thead>
                <tr style={{ borderBottom: '2px solid #e5e7eb' }}>
                  <th style={{ padding: '12px 14px', fontSize: '0.85rem', color: '#4b5563' }}>
                    User
                  </th>
                  <th style={{ padding: '12px 14px', fontSize: '0.85rem', color: '#4b5563' }}>
                    Status
                  </th>
                  <th style={{ padding: '12px 14px', fontSize: '0.85rem', color: '#4b5563' }}>
                    Roles
                  </th>
                  <th style={{ padding: '12px 14px', fontSize: '0.85rem', color: '#4b5563' }}>
                    Created
                  </th>
                  <th style={{ padding: '12px 14px', fontSize: '0.85rem', color: '#4b5563' }}>
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody>
                {usersPage.content.map((item) => {
                  const isSelf = item.id === user?.userId
                  return (
                    <tr
                      key={item.id}
                      style={{
                        borderBottom: '1px solid #f3f4f6',
                        background: isSelf ? '#f9fafb' : 'transparent',
                      }}
                    >
                      <td style={{ padding: '14px', verticalAlign: 'middle' }}>
                        <div style={{ fontWeight: 600, color: '#111827' }}>
                          {item.firstName} {item.lastName}
                          {isSelf && (
                            <span
                              style={{
                                marginLeft: '8px',
                                fontSize: '0.75rem',
                                color: '#1d4ed8',
                                background: '#dbeafe',
                                padding: '2px 6px',
                                borderRadius: '4px',
                              }}
                            >
                              You
                            </span>
                          )}
                        </div>
                        <div style={{ fontSize: '0.85rem', color: '#6b7280' }}>
                          {item.email}
                        </div>
                      </td>

                      <td style={{ padding: '14px', verticalAlign: 'middle' }}>
                        <span
                          style={{
                            display: 'inline-block',
                            padding: '3px 10px',
                            borderRadius: '12px',
                            fontSize: '0.75rem',
                            fontWeight: 600,
                            background:
                              item.status === 'ACTIVE'
                                ? '#ecfdf5'
                                : item.status === 'INVITED'
                                  ? '#fef9c3'
                                  : '#f3f4f6',
                            color:
                              item.status === 'ACTIVE'
                                ? '#047857'
                                : item.status === 'INVITED'
                                  ? '#854d0e'
                                  : '#6b7280',
                            border: `1px solid ${
                              item.status === 'ACTIVE'
                                ? '#a7f3d0'
                                : item.status === 'INVITED'
                                  ? '#fde047'
                                  : '#e5e7eb'
                            }`,
                          }}
                        >
                          {item.status}
                        </span>
                      </td>

                      <td style={{ padding: '14px', verticalAlign: 'middle' }}>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                          {item.roles.map((r) => (
                            <span
                              key={r}
                              style={{
                                display: 'inline-block',
                                padding: '2px 8px',
                                borderRadius: '6px',
                                fontSize: '0.75rem',
                                fontWeight: 500,
                                background: r === 'ADMIN' ? '#fef3c7' : '#eff6ff',
                                color: r === 'ADMIN' ? '#92400e' : '#1d4ed8',
                                border: `1px solid ${
                                  r === 'ADMIN' ? '#fde68a' : '#bfdbfe'
                                }`,
                              }}
                            >
                              {ROLE_LABELS[r]}
                            </span>
                          ))}
                        </div>
                      </td>

                      <td
                        style={{
                          padding: '14px',
                          verticalAlign: 'middle',
                          fontSize: '0.85rem',
                          color: '#4b5563',
                        }}
                      >
                        {new Date(item.createdAt).toLocaleDateString()}
                      </td>

                      <td style={{ padding: '14px', verticalAlign: 'middle' }}>
                        <div className="user-table-actions">
                          <button
                            type="button"
                            className="secondary-button"
                            style={{
                              padding: '6px 12px',
                              fontSize: '0.8rem',
                              height: 'auto',
                            }}
                            onClick={() => openRoleModal(item)}
                          >
                            Edit roles
                          </button>

                          {item.status === 'INVITED' ? (
                            <button
                              type="button"
                              className="secondary-button"
                              style={{
                                padding: '6px 12px',
                                fontSize: '0.8rem',
                                height: 'auto',
                              }}
                              disabled={isResendingId === item.id}
                              onClick={() => handleResendInvitation(item)}
                            >
                              {isResendingId === item.id ? 'Resending...' : 'Resend invitation'}
                            </button>
                          ) : (
                            <button
                              type="button"
                              className={
                                item.status === 'ACTIVE'
                                  ? 'secondary-button'
                                  : 'primary-button'
                              }
                              disabled={isSelf}
                              title={
                                isSelf
                                  ? 'Administrators cannot deactivate their own account'
                                  : item.status === 'ACTIVE'
                                    ? 'Deactivate account'
                                    : 'Activate account'
                              }
                              style={{
                                padding: '6px 12px',
                                fontSize: '0.8rem',
                                height: 'auto',
                                color:
                                  item.status === 'ACTIVE' && !isSelf
                                    ? '#b91c1c'
                                    : undefined,
                                borderColor:
                                  item.status === 'ACTIVE' && !isSelf
                                    ? '#fca5a5'
                                    : undefined,
                              }}
                              onClick={() => openStatusModal(item)}
                            >
                              {item.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Pagination */}
      {usersPage && usersPage.totalPages > 1 && (
        <nav className="pagination" aria-label="User directory pagination">
          <button
            type="button"
            disabled={usersPage.first}
            onClick={() => setPage((prev) => Math.max(0, prev - 1))}
          >
            Previous
          </button>

          <span>
            Page {usersPage.page + 1} of {usersPage.totalPages}
          </span>

          <button
            type="button"
            disabled={usersPage.last}
            onClick={() => setPage((prev) => prev + 1)}
          >
            Next
          </button>
        </nav>
      )}

      {/* Create User Modal */}
      {isCreateModalOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="create-user-modal-title"
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '16px',
            zIndex: 1000,
          }}
        >
          <div
            style={{
              background: '#ffffff',
              borderRadius: '12px',
              maxWidth: '520px',
              width: '100%',
              padding: '28px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            }}
          >
            <h2 id="create-user-modal-title" style={{ marginTop: 0, marginBottom: '8px' }}>
              Create user
            </h2>
            <p style={{ color: '#6b7280', fontSize: '0.9rem', marginBottom: '20px' }}>
              Create a new employee, support engineer, or administrator account.
            </p>

            {createError && (
              <p
                className="notification-error"
                style={{ marginBottom: '16px', padding: '10px' }}
              >
                {createError}
              </p>
            )}

            <form onSubmit={handleCreateUserSubmit}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '14px' }}>
                <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '0.85rem', fontWeight: 600 }}>
                  First name *
                  <input
                    type="text"
                    required
                    maxLength={100}
                    value={createFirstName}
                    onChange={(e) => setCreateFirstName(e.target.value)}
                    placeholder="First name"
                    style={{ height: '40px', padding: '0 10px', border: '1px solid #d1d5db', borderRadius: '6px' }}
                  />
                </label>

                <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '0.85rem', fontWeight: 600 }}>
                  Last name *
                  <input
                    type="text"
                    required
                    maxLength={100}
                    value={createLastName}
                    onChange={(e) => setCreateLastName(e.target.value)}
                    placeholder="Last name"
                    style={{ height: '40px', padding: '0 10px', border: '1px solid #d1d5db', borderRadius: '6px' }}
                  />
                </label>
              </div>

              <div style={{ marginBottom: '14px' }}>
                <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '0.85rem', fontWeight: 600 }}>
                  Email address *
                  <input
                    type="email"
                    required
                    value={createEmail}
                    onChange={(e) => setCreateEmail(e.target.value)}
                    placeholder="user@example.com"
                    style={{ height: '40px', padding: '0 10px', border: '1px solid #d1d5db', borderRadius: '6px' }}
                  />
                </label>
              </div>

              <div style={{ marginBottom: '18px' }}>
                <label style={{ display: 'flex', flexDirection: 'column', gap: '4px', fontSize: '0.85rem', fontWeight: 600 }}>
                  Password * (8 - 72 characters)
                  <input
                    type="password"
                    required
                    minLength={8}
                    maxLength={72}
                    value={createPassword}
                    onChange={(e) => setCreatePassword(e.target.value)}
                    placeholder="Secure password"
                    style={{ height: '40px', padding: '0 10px', border: '1px solid #d1d5db', borderRadius: '6px' }}
                  />
                </label>
              </div>

              <div style={{ marginBottom: '22px' }}>
                <span style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '8px' }}>
                  Assign roles *
                </span>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                  {ALL_ROLES.map((role) => (
                    <label
                      key={role}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        fontSize: '0.85rem',
                        cursor: 'pointer',
                        padding: '6px 8px',
                        border: '1px solid #e5e7eb',
                        borderRadius: '6px',
                        background: createRoles.has(role) ? '#eff6ff' : '#ffffff',
                      }}
                    >
                      <input
                        type="checkbox"
                        checked={createRoles.has(role)}
                        onChange={() => toggleCreateRole(role)}
                      />
                      <span>{ROLE_LABELS[role]}</span>
                    </label>
                  ))}
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                <button
                  type="button"
                  className="secondary-button"
                  disabled={isCreating}
                  onClick={closeCreateModal}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="primary-button"
                  disabled={isCreating}
                >
                  {isCreating ? 'Creating...' : 'Create user'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Invite User Modal */}
      {isInviteModalOpen && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="invite-user-modal-title"
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '16px',
            zIndex: 1000,
          }}
        >
          <div
            style={{
              background: '#ffffff',
              borderRadius: '12px',
              maxWidth: '520px',
              width: '100%',
              padding: '28px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            }}
          >
            <h2 id="invite-user-modal-title" style={{ marginTop: 0, marginBottom: '8px' }}>
              Invite user
            </h2>
            <p style={{ color: '#6b7280', fontSize: '0.9rem', marginBottom: '20px' }}>
              Send a secure onboarding invitation email. The user will activate their account and choose their own password.
            </p>

            {inviteError && (
              <p
                className="notification-error"
                style={{ marginBottom: '16px', padding: '10px' }}
              >
                {inviteError}
              </p>
            )}

            <form onSubmit={handleInviteUserSubmit}>
              <div style={{ marginBottom: '16px' }}>
                <label
                  htmlFor="invite-email"
                  style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '6px' }}
                >
                  Email address *
                </label>
                <input
                  id="invite-email"
                  type="email"
                  value={inviteEmail}
                  onChange={(e) => setInviteEmail(e.target.value)}
                  placeholder="user@example.com"
                  required
                  style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', border: '1px solid #d1d5db' }}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '16px' }}>
                <div>
                  <label
                    htmlFor="invite-first-name"
                    style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '6px' }}
                  >
                    First name *
                  </label>
                  <input
                    id="invite-first-name"
                    type="text"
                    value={inviteFirstName}
                    onChange={(e) => setInviteFirstName(e.target.value)}
                    placeholder="Jane"
                    required
                    style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', border: '1px solid #d1d5db' }}
                  />
                </div>
                <div>
                  <label
                    htmlFor="invite-last-name"
                    style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '6px' }}
                  >
                    Last name *
                  </label>
                  <input
                    id="invite-last-name"
                    type="text"
                    value={inviteLastName}
                    onChange={(e) => setInviteLastName(e.target.value)}
                    placeholder="Doe"
                    required
                    style={{ width: '100%', padding: '8px 12px', borderRadius: '6px', border: '1px solid #d1d5db' }}
                  />
                </div>
              </div>

              <div style={{ marginBottom: '22px' }}>
                <span style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: '8px' }}>
                  Assign roles *
                </span>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                  {ALL_ROLES.map((role) => (
                    <label
                      key={role}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        fontSize: '0.85rem',
                        cursor: 'pointer',
                        padding: '6px 8px',
                        border: '1px solid #e5e7eb',
                        borderRadius: '6px',
                        background: inviteRoles.has(role) ? '#ecfdf5' : '#ffffff',
                      }}
                    >
                      <input
                        type="checkbox"
                        checked={inviteRoles.has(role)}
                        onChange={() => toggleInviteRole(role)}
                      />
                      <span>{ROLE_LABELS[role]}</span>
                    </label>
                  ))}
                </div>
              </div>

              <div
                style={{
                  background: '#f0fdf4',
                  border: '1px solid #bbf7d0',
                  borderRadius: '6px',
                  padding: '12px',
                  marginBottom: '20px',
                  fontSize: '0.82rem',
                  color: '#166534',
                }}
              >
                An invitation link valid for 48 hours will be emailed to the user. No password is set at this stage.
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                <button
                  type="button"
                  className="secondary-button"
                  disabled={isInviting}
                  onClick={closeInviteModal}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="primary-button"
                  style={{ background: '#059669', borderColor: '#059669' }}
                  disabled={isInviting}
                >
                  {isInviting ? 'Sending invitation...' : 'Send invitation'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit Roles Modal */}
      {roleModalUser && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="edit-roles-modal-title"
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '16px',
            zIndex: 1000,
          }}
        >
          <div
            style={{
              background: '#ffffff',
              borderRadius: '12px',
              maxWidth: '480px',
              width: '100%',
              padding: '28px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            }}
          >
            <h2 id="edit-roles-modal-title" style={{ marginTop: 0, marginBottom: '6px' }}>
              Edit user roles
            </h2>
            <p style={{ color: '#6b7280', fontSize: '0.9rem', marginBottom: '16px' }}>
              Configuring roles for <strong>{roleModalUser.firstName} {roleModalUser.lastName}</strong> ({roleModalUser.email}).
            </p>

            {roleError && (
              <p
                className="notification-error"
                style={{ marginBottom: '16px', padding: '10px' }}
              >
                {roleError}
              </p>
            )}

            {roleModalUser.id === user?.userId && (
              <p
                style={{
                  background: '#fef3c7',
                  border: '1px solid #fde68a',
                  color: '#92400e',
                  fontSize: '0.82rem',
                  padding: '8px 12px',
                  borderRadius: '6px',
                  marginBottom: '16px',
                }}
              >
                You are editing your own roles. The Administrator role cannot be removed from yourself.
              </p>
            )}

            <form onSubmit={handleSaveRoles}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '22px' }}>
                {ALL_ROLES.map((role) => {
                  const isSelfAdmin =
                    roleModalUser.id === user?.userId && role === 'ADMIN'

                  return (
                    <label
                      key={role}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '10px 14px',
                        border: '1px solid #e5e7eb',
                        borderRadius: '8px',
                        background: editRoles.has(role) ? '#eff6ff' : '#ffffff',
                        cursor: isSelfAdmin ? 'not-allowed' : 'pointer',
                        opacity: isSelfAdmin ? 0.75 : 1,
                      }}
                    >
                      <span style={{ fontWeight: 500, fontSize: '0.9rem' }}>
                        {ROLE_LABELS[role]}
                      </span>
                      <input
                        type="checkbox"
                        disabled={isSelfAdmin}
                        checked={editRoles.has(role)}
                        onChange={() => toggleEditRole(role)}
                      />
                    </label>
                  )
                })}
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
                <button
                  type="button"
                  className="secondary-button"
                  disabled={isSavingRoles}
                  onClick={closeRoleModal}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="primary-button"
                  disabled={isSavingRoles || editRoles.size === 0}
                >
                  {isSavingRoles ? 'Saving...' : 'Save changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Confirmation Modal for Status Change */}
      {statusModalUser && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="status-modal-title"
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '16px',
            zIndex: 1000,
          }}
        >
          <div
            style={{
              background: '#ffffff',
              borderRadius: '12px',
              maxWidth: '460px',
              width: '100%',
              padding: '28px',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            }}
          >
            <h2 id="status-modal-title" style={{ marginTop: 0, marginBottom: '10px' }}>
              {statusModalUser.status === 'ACTIVE'
                ? 'Deactivate user account?'
                : 'Activate user account?'}
            </h2>

            <p style={{ color: '#4b5563', fontSize: '0.9rem', marginBottom: '20px' }}>
              {statusModalUser.status === 'ACTIVE' ? (
                <>
                  Are you sure you want to deactivate{' '}
                  <strong>
                    {statusModalUser.firstName} {statusModalUser.lastName}
                  </strong>{' '}
                  ({statusModalUser.email})? This user will immediately lose access to FlowDesk, and any active sessions will be invalidated.
                </>
              ) : (
                <>
                  Are you sure you want to activate{' '}
                  <strong>
                    {statusModalUser.firstName} {statusModalUser.lastName}
                  </strong>{' '}
                  ({statusModalUser.email})? The user will be able to sign in and perform actions matching their assigned roles.
                </>
              )}
            </p>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
              <button
                type="button"
                className="secondary-button"
                disabled={isUpdatingStatus}
                onClick={closeStatusModal}
              >
                Cancel
              </button>

              <button
                type="button"
                className={
                  statusModalUser.status === 'ACTIVE'
                    ? 'secondary-button'
                    : 'primary-button'
                }
                disabled={isUpdatingStatus}
                style={{
                  color: statusModalUser.status === 'ACTIVE' ? '#b91c1c' : undefined,
                  borderColor:
                    statusModalUser.status === 'ACTIVE' ? '#fca5a5' : undefined,
                }}
                onClick={handleConfirmStatusChange}
              >
                {isUpdatingStatus
                  ? 'Processing...'
                  : statusModalUser.status === 'ACTIVE'
                    ? 'Confirm deactivation'
                    : 'Confirm activation'}
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  )
}
