import {
  type FormEvent,
  useEffect,
  useState,
} from 'react'
import {
  Link,
  useNavigate,
  useSearchParams,
} from 'react-router-dom'
import {
  Layers,
  Lock,
  Eye,
  EyeOff,
  ArrowLeft,
  AlertCircle,
  CheckCircle2,
} from 'lucide-react'

import { resetPassword } from '../api/auth'
import { useAuth } from '../auth/useAuth'
import { ThemeSwitcher } from '../components/ui/ThemeSwitcher'

export function ResetPasswordPage() {
  const { signOut } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  // Safely capture token from URL synchronously once, then strip it from browser address bar
  const [token] = useState<string>(() => {
    return searchParams.get('token')?.trim() ?? ''
  })

  useEffect(() => {
    if (searchParams.has('token')) {
      // Remove raw token from URL immediately to prevent exposure in history / referrers
      window.history.replaceState({}, document.title, window.location.pathname)
    }
  }, [searchParams])

  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isSuccess, setIsSuccess] = useState(false)

  if (!token) {
    return (
      <main className="login-page horizon-auth-page">
        <div style={{ position: 'absolute', top: '1.5rem', right: '1.5rem' }}>
          <ThemeSwitcher variant="toggle" />
        </div>

        <section className="login-card horizon-auth-card" aria-labelledby="reset-error-heading">
          <div className="horizon-auth-brand-row">
            <div className="horizon-auth-brand-icon" aria-hidden="true">
              <Layers size={20} />
            </div>
            <span className="horizon-auth-brand-name eyebrow">FlowDesk</span>
            <span className="horizon-auth-badge">Horizon</span>
          </div>

          <div className="login-heading horizon-auth-heading">
            <h1 id="reset-error-heading">Invalid reset link</h1>
            <p className="horizon-auth-subtitle">
              This password reset link is invalid or incomplete.
            </p>
          </div>

          <div className="form-error horizon-auth-alert horizon-auth-alert--error" role="alert">
            <AlertCircle size={18} className="horizon-auth-alert-icon" />
            <span>
              Please make sure you copied the full link from your email, or request a new reset link.
            </span>
          </div>

          <div className="login-secondary-actions" style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginTop: '1.5rem' }}>
            <Link to="/forgot-password" className="forgot-password-link horizon-btn horizon-btn--primary horizon-btn--md horizon-btn--full-width">
              Request a new reset link
            </Link>
            <Link to="/login" className="back-to-login-link horizon-btn horizon-btn--secondary horizon-btn--md horizon-btn--full-width">
              <ArrowLeft size={16} />
              <span>Return to sign in</span>
            </Link>
          </div>
        </section>
      </main>
    )
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    if (!newPassword) {
      setError('New password is required.')
      return
    }

    if (newPassword.length < 8 || newPassword.length > 72) {
      setError('Password must be between 8 and 72 characters.')
      return
    }

    if (newPassword !== confirmPassword) {
      setError('Passwords do not match.')
      return
    }

    setIsSubmitting(true)

    try {
      await resetPassword({
        token,
        newPassword,
        confirmPassword,
      })

      // Ensure any existing frontend auth state is cleared
      signOut()
      setIsSuccess(true)

      // Wipe passwords from component state
      setNewPassword('')
      setConfirmPassword('')

      setTimeout(() => {
        navigate('/login', {
          replace: true,
          state: {
            message: 'Your password has been successfully reset. Please sign in with your new password.',
          },
        })
      }, 2500)
    } catch (err) {
      if (err instanceof Error) {
        setError(err.message)
      } else {
        setError('Unable to reset password. The link may have expired or already been used.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="login-page horizon-auth-page">
      <div style={{ position: 'absolute', top: '1.5rem', right: '1.5rem' }}>
        <ThemeSwitcher variant="toggle" />
      </div>

      <section className="login-card horizon-auth-card" aria-labelledby="reset-password-heading">
        <div className="horizon-auth-brand-row">
          <div className="horizon-auth-brand-icon" aria-hidden="true">
            <Layers size={20} />
          </div>
          <span className="horizon-auth-brand-name eyebrow">FlowDesk</span>
          <span className="horizon-auth-badge">Horizon</span>
        </div>

        <div className="login-heading horizon-auth-heading">
          <h1 id="reset-password-heading">Reset your password</h1>
          <p className="horizon-auth-subtitle">
            Choose a strong, unique password to secure your account.
          </p>
        </div>

        {isSuccess ? (
          <div className="reset-success-block" role="status">
            <div className="form-success horizon-auth-alert horizon-auth-alert--success">
              <CheckCircle2 size={18} className="horizon-auth-alert-icon" />
              <span>
                Your password has been reset successfully! Redirecting you to sign in...
              </span>
            </div>
            <div className="login-secondary-actions" style={{ marginTop: '1.5rem' }}>
              <Link to="/login" className="back-to-login-link horizon-btn horizon-btn--primary horizon-btn--md horizon-btn--full-width">
                <span>Sign in now &rarr;</span>
              </Link>
            </div>
          </div>
        ) : (
          <form className="login-form horizon-auth-form" onSubmit={handleSubmit} noValidate>
            <div className="horizon-form-field">
              <label htmlFor="reset-new-password" className="horizon-label">
                New password
              </label>
              <div className="password-input-wrapper horizon-auth-input-wrapper">
                <span className="horizon-auth-input-icon" aria-hidden="true">
                  <Lock size={16} />
                </span>
                <input
                  id="reset-new-password"
                  type={showPassword ? 'text' : 'password'}
                  className="horizon-auth-input horizon-auth-input-with-icon"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  autoComplete="new-password"
                  disabled={isSubmitting}
                  placeholder="••••••••••••"
                  required
                />
                <button
                  type="button"
                  className="password-toggle-btn horizon-auth-password-toggle"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  tabIndex={-1}
                >
                  {showPassword ? <EyeOff size={16} aria-hidden="true" /> : <Eye size={16} aria-hidden="true" />}
                  <span style={{ marginLeft: '4px' }}>{showPassword ? 'Hide' : 'Show'}</span>
                </button>
              </div>
              <span className="field-hint horizon-form-helper" style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', marginTop: '4px' }}>
                Must be between 8 and 72 characters
              </span>
            </div>

            <div className="horizon-form-field">
              <label htmlFor="reset-confirm-password" className="horizon-label">
                Confirm new password
              </label>
              <div className="password-input-wrapper horizon-auth-input-wrapper">
                <span className="horizon-auth-input-icon" aria-hidden="true">
                  <Lock size={16} />
                </span>
                <input
                  id="reset-confirm-password"
                  type={showPassword ? 'text' : 'password'}
                  className="horizon-auth-input horizon-auth-input-with-icon"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  autoComplete="new-password"
                  disabled={isSubmitting}
                  placeholder="••••••••••••"
                  required
                />
              </div>
            </div>

            {error && (
              <div className="form-error horizon-auth-alert horizon-auth-alert--error" role="alert">
                <AlertCircle size={18} className="horizon-auth-alert-icon" />
                <span>{error}</span>
              </div>
            )}

            <button
              type="submit"
              className="horizon-btn horizon-btn--primary horizon-btn--md horizon-btn--full-width"
              disabled={isSubmitting}
              style={{ marginTop: '0.5rem' }}
            >
              {isSubmitting ? 'Resetting password...' : 'Reset password'}
            </button>

            <div style={{ textAlign: 'center', marginTop: '1rem' }}>
              <Link to="/login" className="back-to-login-link horizon-auth-link" style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                <ArrowLeft size={14} />
                <span>Return to sign in</span>
              </Link>
            </div>
          </form>
        )}

        <div className="horizon-auth-footer">
          <p>FlowDesk Horizon &bull; Password Reset</p>
        </div>
      </section>
    </main>
  )
}
