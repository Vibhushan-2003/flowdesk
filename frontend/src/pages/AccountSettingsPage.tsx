import { useState, type FormEvent } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import {
  KeyRound,
  ShieldCheck,
  Moon,
  Sun,
  Laptop,
  LogOut,
  AlertCircle,
  CheckCircle2,
  Lock,
  Eye,
  EyeOff,
} from 'lucide-react'
import { changePassword } from '../api/auth'
import { useAuth } from '../auth/useAuth'
import { useTheme } from '../theme/useTheme'
import { PageHeader } from '../components/ui/PageHeader'
import { Card } from '../components/ui/Card'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'

export function AccountSettingsPage() {
  const { user, accessToken, signOut } = useAuth()
  const { theme, setTheme } = useTheme()
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()

  const activeTab = searchParams.get('tab') === 'preferences' ? 'preferences' : 'security'

  // Change Password state
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showCurrentPw, setShowCurrentPw] = useState(false)
  const [showNewPw, setShowNewPw] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handlePasswordSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSuccess(null)

    if (!currentPassword) {
      setError('Current password is required')
      return
    }

    if (!newPassword) {
      setError('New password is required')
      return
    }

    if (newPassword.length < 8 || newPassword.length > 72) {
      setError('New password must be between 8 and 72 characters')
      return
    }

    if (newPassword === currentPassword) {
      setError('New password must be different from current password')
      return
    }

    if (newPassword !== confirmPassword) {
      setError('New passwords do not match')
      return
    }

    if (!accessToken) {
      setError('Authentication required to change password')
      return
    }

    setIsSubmitting(true)

    try {
      await changePassword(accessToken, {
        currentPassword,
        newPassword,
      })

      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')

      setSuccess('Password changed successfully. Revoking sessions and signing out...')

      setTimeout(() => {
        signOut()
        navigate('/login', {
          replace: true,
          state: {
            message: 'Password changed successfully. Please sign in with your new credentials.',
          },
        })
      }, 1200)
    } catch (err) {
      if (err instanceof Error) {
        setError(err.message)
      } else {
        setError('Unable to change password. Please verify current password and try again.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  function handleSignOut() {
    signOut()
    navigate('/login')
  }

  return (
    <div className="account-settings-page horizon-page-container">
      <PageHeader
        title="Account Settings"
        subtitle="Manage your password, account security, and display preferences."
      />

      {/* Tabs */}
      <div className="horizon-view-tabs" role="tablist" aria-label="Account settings sections" style={{ marginTop: '16px', marginBottom: '24px' }}>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'security'}
          onClick={() => setSearchParams({ tab: 'security' })}
          className={`horizon-view-tab ${activeTab === 'security' ? 'is-active' : ''}`}
        >
          <KeyRound size={16} />
          <span>Security & Credentials</span>
        </button>

        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'preferences'}
          onClick={() => setSearchParams({ tab: 'preferences' })}
          className={`horizon-view-tab ${activeTab === 'preferences' ? 'is-active' : ''}`}
        >
          <Laptop size={16} />
          <span>Display Preferences</span>
        </button>
      </div>

      {activeTab === 'security' ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 280px), 1fr))', gap: '24px' }}>
          {/* Change Password Card */}
          <Card padded title="Change Password" subtitle="Ensure your account stays secure by using a strong password.">
            <form onSubmit={handlePasswordSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              {error && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '10px 14px', borderRadius: 'var(--radius-md)', background: 'var(--color-danger-subtle)', border: '1px solid var(--color-danger-border)', color: 'var(--color-danger-text)', fontSize: 'var(--font-size-xs)' }} role="alert">
                  <AlertCircle size={16} style={{ flexShrink: 0 }} />
                  <span>{error}</span>
                </div>
              )}

              {success && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '10px 14px', borderRadius: 'var(--radius-md)', background: 'var(--color-success-subtle)', border: '1px solid var(--color-success-border)', color: 'var(--color-success-text)', fontSize: 'var(--font-size-xs)' }} role="status">
                  <CheckCircle2 size={16} style={{ flexShrink: 0 }} />
                  <span>{success}</span>
                </div>
              )}

              <div>
                <label style={{ display: 'block', fontSize: 'var(--font-size-xs)', fontWeight: 600, color: 'var(--color-text-primary)', marginBottom: '6px' }}>
                  Current Password
                </label>
                <div style={{ position: 'relative' }}>
                  <input
                    type={showCurrentPw ? 'text' : 'password'}
                    value={currentPassword}
                    onChange={(e) => setCurrentPassword(e.target.value)}
                    placeholder="Enter current password"
                    style={{ width: '100%', padding: '10px 38px 10px 12px' }}
                    autoComplete="current-password"
                    disabled={isSubmitting}
                  />
                  <button
                    type="button"
                    onClick={() => setShowCurrentPw((v) => !v)}
                    style={{ position: 'absolute', right: '10px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', color: 'var(--color-text-muted)', cursor: 'pointer', padding: '4px' }}
                    aria-label={showCurrentPw ? 'Hide password' : 'Show password'}
                  >
                    {showCurrentPw ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: 'var(--font-size-xs)', fontWeight: 600, color: 'var(--color-text-primary)', marginBottom: '6px' }}>
                  New Password
                </label>
                <div style={{ position: 'relative' }}>
                  <input
                    type={showNewPw ? 'text' : 'password'}
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="8 to 72 characters"
                    style={{ width: '100%', padding: '10px 38px 10px 12px' }}
                    autoComplete="new-password"
                    disabled={isSubmitting}
                  />
                  <button
                    type="button"
                    onClick={() => setShowNewPw((v) => !v)}
                    style={{ position: 'absolute', right: '10px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', color: 'var(--color-text-muted)', cursor: 'pointer', padding: '4px' }}
                    aria-label={showNewPw ? 'Hide password' : 'Show password'}
                  >
                    {showNewPw ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: 'var(--font-size-xs)', fontWeight: 600, color: 'var(--color-text-primary)', marginBottom: '6px' }}>
                  Confirm New Password
                </label>
                <input
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="Re-enter new password"
                  style={{ width: '100%', padding: '10px 12px' }}
                  autoComplete="new-password"
                  disabled={isSubmitting}
                />
              </div>

              <div style={{ marginTop: '8px' }}>
                <Button
                  type="submit"
                  variant="primary"
                  size="md"
                  disabled={isSubmitting}
                  leadingIcon={<Lock size={16} />}
                >
                  {isSubmitting ? 'Updating Password...' : 'Update Password'}
                </Button>
              </div>
            </form>
          </Card>

          {/* Session Information Card */}
          <Card padded title="Account Security" subtitle="Current sign-in status and session protection.">
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)' }}>
                  Sign-In Method
                </span>
                <Badge variant="neutral" size="sm">
                  Password & SSO
                </Badge>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)' }}>
                  Registered Email
                </span>
                <strong style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-primary)' }}>
                  {user?.email}
                </strong>
              </div>

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)' }}>
                  Session Security
                </span>
                <span style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: 'var(--font-size-sm)', color: 'var(--color-success-text)' }}>
                  <ShieldCheck size={14} /> Active & Protected
                </span>
              </div>

              <div style={{ borderTop: '1px solid var(--color-border)', paddingTop: '16px', marginTop: '8px' }}>
                <p style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', lineHeight: '1.5', margin: '0 0 16px' }}>
                  Updating your password automatically signs you out of all other active sessions to keep your account safe.
                </p>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={handleSignOut}
                  leadingIcon={<LogOut size={14} />}
                >
                  Sign Out from this Device
                </Button>
              </div>
            </div>
          </Card>
        </div>
      ) : (
        <div style={{ maxWidth: '640px' }}>
          {/* Preferences Card */}
          <Card padded title="Display Preferences" subtitle="Choose your preferred color theme for the FlowDesk workspace.">
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              <div>
                <label style={{ display: 'block', fontSize: 'var(--font-size-xs)', fontWeight: 600, color: 'var(--color-text-primary)', marginBottom: '10px' }}>
                  Workspace Color Theme
                </label>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '12px' }}>
                  <button
                    type="button"
                    onClick={() => setTheme('light')}
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      gap: '8px',
                      padding: '16px',
                      borderRadius: 'var(--radius-lg)',
                      border: theme === 'light' ? '2px solid var(--color-primary)' : '1px solid var(--color-border)',
                      background: theme === 'light' ? 'var(--color-primary-subtle)' : 'var(--color-surface)',
                      cursor: 'pointer',
                      color: 'var(--color-text-primary)',
                      fontWeight: 600,
                      fontSize: 'var(--font-size-sm)',
                    }}
                  >
                    <Sun size={22} style={{ color: theme === 'light' ? 'var(--color-primary)' : 'var(--color-text-secondary)' }} />
                    <span>Light Mode</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setTheme('dark')}
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      gap: '8px',
                      padding: '16px',
                      borderRadius: 'var(--radius-lg)',
                      border: theme === 'dark' ? '2px solid var(--color-primary)' : '1px solid var(--color-border)',
                      background: theme === 'dark' ? 'var(--color-primary-subtle)' : 'var(--color-surface)',
                      cursor: 'pointer',
                      color: 'var(--color-text-primary)',
                      fontWeight: 600,
                      fontSize: 'var(--font-size-sm)',
                    }}
                  >
                    <Moon size={22} style={{ color: theme === 'dark' ? 'var(--color-primary)' : 'var(--color-text-secondary)' }} />
                    <span>Dark Mode</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setTheme('system')}
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      gap: '8px',
                      padding: '16px',
                      borderRadius: 'var(--radius-lg)',
                      border: theme === 'system' ? '2px solid var(--color-primary)' : '1px solid var(--color-border)',
                      background: theme === 'system' ? 'var(--color-primary-subtle)' : 'var(--color-surface)',
                      cursor: 'pointer',
                      color: 'var(--color-text-primary)',
                      fontWeight: 600,
                      fontSize: 'var(--font-size-sm)',
                    }}
                  >
                    <Laptop size={22} style={{ color: theme === 'system' ? 'var(--color-primary)' : 'var(--color-text-secondary)' }} />
                    <span>System Sync</span>
                  </button>
                </div>
              </div>

              <div style={{ borderTop: '1px solid var(--color-border)', paddingTop: '16px' }}>
                <span style={{ display: 'block', fontSize: 'var(--font-size-xs)', fontWeight: 600, color: 'var(--color-text-primary)', marginBottom: '4px' }}>
                  Accessibility & Motion Standards
                </span>
                <p style={{ margin: 0, fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', lineHeight: '1.5' }}>
                  FlowDesk Horizon respects the <code>prefers-reduced-motion</code> media query automatically. All transitions and animation durations are suppressed when reduced-motion is requested in your operating system settings.
                </p>
              </div>
            </div>
          </Card>
        </div>
      )}
    </div>
  )
}

