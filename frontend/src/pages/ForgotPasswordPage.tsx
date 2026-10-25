import {
  type FormEvent,
  useState,
} from 'react'
import {
  Link,
  Navigate,
} from 'react-router-dom'
import { forgotPassword } from '../api/auth'
import { useAuth } from '../auth/useAuth'

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
    <main className="login-page">
      <section className="login-card" aria-labelledby="forgot-password-heading">
        <div className="login-heading">
          <p className="eyebrow">FlowDesk</p>
          <h1 id="forgot-password-heading">Forgot password</h1>
          <p>
            Enter your registered email address and we will send you instructions to reset your password.
          </p>
        </div>

        {isSubmitted ? (
          <div className="forgot-password-success-block" role="status">
            <p className="form-success">
              If an account with that email exists, password reset instructions have been sent. Please check your inbox and follow the link to reset your password.
            </p>
            <p className="forgot-password-expiry-notice">
              The reset link will expire in <strong>15 minutes</strong>. If you do not see the email, check your spam or junk folder.
            </p>
            <div className="login-form-actions">
              <Link to="/login" className="back-to-login-link">
                &larr; Return to sign in
              </Link>
            </div>
          </div>
        ) : (
          <form className="login-form" onSubmit={handleSubmit} noValidate>
            <label htmlFor="forgot-password-email">
              Email
              <input
                id="forgot-password-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                placeholder="employee@example.com"
                disabled={isSubmitting}
                required
              />
            </label>

            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}

            <button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Sending instructions...' : 'Send reset instructions'}
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
