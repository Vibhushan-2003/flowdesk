import {
  type FormEvent,
  useState,
} from 'react'
import {
  Link,
  useNavigate,
  useSearchParams,
} from 'react-router-dom'
import { resetPassword } from '../api/auth'
import { useAuth } from '../auth/useAuth'

export function ResetPasswordPage() {
  const { signOut } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  const token = searchParams.get('token')?.trim() ?? ''

  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isSuccess, setIsSuccess] = useState(false)

  if (!token) {
    return (
      <main className="login-page">
        <section className="login-card" aria-labelledby="reset-error-heading">
          <div className="login-heading">
            <p className="eyebrow">FlowDesk</p>
            <h1 id="reset-error-heading">Invalid reset link</h1>
            <p>
              This password reset link is invalid or incomplete.
            </p>
          </div>

          <p className="form-error" role="alert">
            Please make sure you copied the full link from your email, or request a new reset link.
          </p>

          <div className="login-secondary-actions" style={{ marginTop: '1.5rem' }}>
            <Link to="/forgot-password" className="forgot-password-link">
              Request a new reset link
            </Link>
            <Link to="/login" className="back-to-login-link">
              &larr; Return to sign in
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
    <main className="login-page">
      <section className="login-card" aria-labelledby="reset-password-heading">
        <div className="login-heading">
          <p className="eyebrow">FlowDesk</p>
          <h1 id="reset-password-heading">Reset your password</h1>
          <p>
            Choose a strong, unique password to secure your account.
          </p>
        </div>

        {isSuccess ? (
          <div className="reset-success-block" role="status">
            <p className="form-success">
              Your password has been reset successfully! Redirecting you to sign in...
            </p>
            <div className="login-secondary-actions" style={{ marginTop: '1.5rem' }}>
              <Link to="/login" className="back-to-login-link">
                Sign in now &rarr;
              </Link>
            </div>
          </div>
        ) : (
          <form className="login-form" onSubmit={handleSubmit} noValidate>
            <label htmlFor="reset-new-password">
              New password
              <div className="password-input-wrapper">
                <input
                  id="reset-new-password"
                  type={showPassword ? 'text' : 'password'}
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  autoComplete="new-password"
                  disabled={isSubmitting}
                  required
                />
                <button
                  type="button"
                  className="password-toggle-btn"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  tabIndex={-1}
                >
                  {showPassword ? 'Hide' : 'Show'}
                </button>
              </div>
              <span className="field-hint">Must be between 8 and 72 characters</span>
            </label>

            <label htmlFor="reset-confirm-password">
              Confirm new password
              <div className="password-input-wrapper">
                <input
                  id="reset-confirm-password"
                  type={showPassword ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  autoComplete="new-password"
                  disabled={isSubmitting}
                  required
                />
              </div>
            </label>

            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}

            <button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Resetting password...' : 'Reset password'}
            </button>

            <div className="login-secondary-actions">
              <Link to="/login" className="back-to-login-link">
                &larr; Return to sign in
              </Link>
            </div>
          </form>
        )}
      </section>
    </main>
  )
}
