import {
  type FormEvent,
  useState,
} from 'react'
import {
  Link,
  Navigate,
} from 'react-router-dom'
import {
  Layers,
  Mail,
  ArrowLeft,
  AlertCircle,
  CheckCircle2,
} from 'lucide-react'

import { forgotPassword } from '../api/auth'
import { useAuth } from '../auth/useAuth'
import { ThemeSwitcher } from '../components/ui/ThemeSwitcher'

export function ForgotPasswordPage() {
  const { isAuthenticated } = useAuth()

  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isSubmitted, setIsSubmitted] = useState(false)

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    const trimmedEmail = email.trim()
    if (!trimmedEmail) {
      setError('Please enter your email address.')
      return
    }

    setIsSubmitting(true)

    try {
      await forgotPassword({ email: trimmedEmail })
      setIsSubmitted(true)
    } catch (err) {
      if (err instanceof Error) {
        setError(err.message)
      } else {
        setError('Unable to request password reset. Please try again later.')
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

      <section className="login-card horizon-auth-card" aria-labelledby="forgot-password-heading">
        {/* Brand Row */}
        <div className="horizon-auth-brand-row">
          <div className="horizon-auth-brand-icon" aria-hidden="true">
            <Layers size={20} />
          </div>
          <span className="horizon-auth-brand-name eyebrow">FlowDesk</span>
          <span className="horizon-auth-badge">Horizon</span>
        </div>

        {/* Headings */}
        <div className="login-heading horizon-auth-heading">
          <h1 id="forgot-password-heading">Forgot password</h1>
          <p className="horizon-auth-subtitle">
            Enter your registered email address and we will send you instructions to reset your password.
          </p>
        </div>

        {isSubmitted ? (
          <div className="forgot-password-success-block" role="status">
            <div className="form-success horizon-auth-alert horizon-auth-alert--success">
              <CheckCircle2 size={18} className="horizon-auth-alert-icon" />
              <span>
                If an account with that email exists, password reset instructions have been sent. Please check your inbox and follow the link to reset your password.
              </span>
            </div>
            <p className="forgot-password-expiry-notice" style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', margin: '1rem 0' }}>
              The reset link will expire in <strong>15 minutes</strong>. If you do not see the email, check your spam or junk folder.
            </p>
            <div className="login-form-actions" style={{ marginTop: '1.5rem' }}>
              <Link to="/login" className="back-to-login-link horizon-btn horizon-btn--secondary horizon-btn--md horizon-btn--full-width">
                <ArrowLeft size={16} />
                <span>Return to sign in</span>
              </Link>
            </div>
          </div>
        ) : (
          <form className="login-form horizon-auth-form" onSubmit={handleSubmit} noValidate>
            <div className="horizon-form-field">
              <label htmlFor="forgot-password-email" className="horizon-label">
                Email
              </label>
              <div className="horizon-auth-input-wrapper">
                <span className="horizon-auth-input-icon" aria-hidden="true">
                  <Mail size={16} />
                </span>
                <input
                  id="forgot-password-email"
                  type="email"
                  className="horizon-auth-input horizon-auth-input-with-icon"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  autoComplete="email"
                  placeholder="employee@example.com"
                  disabled={isSubmitting}
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
              {isSubmitting ? 'Sending instructions...' : 'Send reset instructions'}
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
          <p>FlowDesk Horizon &bull; Account Recovery</p>
        </div>
      </section>
    </main>
  )
}
