import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  User,
  Mail,
  Shield,
  KeyRound,
  Sliders,
  Copy,
  Check,
  CheckCircle2,
  Info,
} from 'lucide-react'
import { useAuth } from '../auth/useAuth'
import { PageHeader } from '../components/ui/PageHeader'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { Button } from '../components/ui/Button'
import { UserAvatar } from '../components/ui/UserAvatar'

export function ProfilePage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [copied, setCopied] = useState(false)

  const roles = user?.roles || []
  const primaryRole = roles[0] || 'EMPLOYEE'
  const displayName = user?.email ? user.email.split('@')[0] : 'FlowDesk User'

  function copyUserId() {
    if (user?.userId) {
      void navigator.clipboard.writeText(user.userId)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  return (
    <div className="profile-page horizon-page-container">
      <PageHeader
        title="My Profile"
        subtitle="View your account details and assigned roles."
        badge={
          <Badge variant="primary" size="md">
            {primaryRole.replace('_', ' ')}
          </Badge>
        }
        actions={
          <div style={{ display: 'flex', gap: '8px' }}>
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate('/account')}
              leadingIcon={<KeyRound size={14} />}
            >
              Security Settings
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate('/account?tab=preferences')}
              leadingIcon={<Sliders size={14} />}
            >
              Preferences
            </Button>
          </div>
        }
      />

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 280px), 1fr))', gap: '24px', marginTop: '20px' }}>
        {/* Identity Hero Card */}
        <Card padded hoverable={false}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '20px', marginBottom: '24px' }}>
            <UserAvatar email={user?.email} size="lg" />
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <h2 style={{ margin: 0, fontSize: 'var(--font-size-xl)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)', textTransform: 'capitalize' }}>
                  {displayName}
                </h2>
                <Badge variant="success" size="sm">
                  Active
                </Badge>
              </div>
              <p style={{ margin: '4px 0 0', color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-sm)' }}>
                {user?.email}
              </p>
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', borderTop: '1px solid var(--color-border)', paddingTop: '20px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-sm)' }}>
                <Mail size={16} /> Email Address
              </span>
              <strong style={{ color: 'var(--color-text-primary)', fontSize: 'var(--font-size-sm)' }}>
                {user?.email}
              </strong>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-sm)' }}>
                <User size={16} /> System User ID
              </span>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <code style={{ fontSize: 'var(--font-size-xs)', fontFamily: 'var(--font-family-mono)', background: 'var(--color-surface-subtle)', padding: '2px 6px', borderRadius: '4px', color: 'var(--color-text-primary)' }}>
                  {user?.userId || 'N/A'}
                </code>
                {user?.userId && (
                  <button
                    type="button"
                    onClick={copyUserId}
                    style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '4px', color: 'var(--color-text-muted)' }}
                    title="Copy User ID"
                    aria-label="Copy User ID"
                  >
                    {copied ? <Check size={14} style={{ color: 'var(--color-success)' }} /> : <Copy size={14} />}
                  </button>
                )}
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-sm)' }}>
                <Shield size={16} /> Account Status
              </span>
              <span style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--color-success-text)', fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-medium)' }}>
                <CheckCircle2 size={14} /> Verified & Active
              </span>
            </div>
          </div>
        </Card>

        {/* Roles & Permissions Card */}
        <Card padded title="Your Roles & Permissions" subtitle="Your assigned roles determine which workspaces and actions you can access.">
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div>
              <label style={{ display: 'block', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '8px', fontWeight: 'var(--font-weight-semibold)' }}>
                Your Roles ({roles.length})
              </label>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                {roles.map((r) => (
                  <Badge key={r} variant="primary" size="md">
                    {r.replace('_', ' ')}
                  </Badge>
                ))}
              </div>
            </div>

            <div style={{ marginTop: '12px', padding: '14px', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
                <Info size={16} style={{ color: 'var(--color-primary)', marginTop: '2px', flexShrink: 0 }} />
                <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', lineHeight: '1.5' }}>
                  <strong>Role-Based Access:</strong> Roles are managed by system administrators. Team Leads and Administrators can review approvals, inspect metrics, and manage user settings.
                </div>
              </div>
            </div>

            <div style={{ marginTop: '8px', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', lineHeight: '1.5' }}>
              Profile avatars are generated automatically from your registered email address.
            </div>
          </div>
        </Card>
      </div>
    </div>
  )
}

