import {
  type FormEvent,
  useState,
} from 'react'
import { useNavigate } from 'react-router-dom'
import { changePassword } from '../api/auth'
import { useAuth } from '../auth/useAuth'

export function ChangePasswordCard() {
  const {
    accessToken,
    signOut,
  } = useAuth()

  const navigate = useNavigate()

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
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

      // Never keep credentials in memory or write to local storage
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')

      setSuccess('Password changed successfully. Signing out...')

      // Invalidate frontend session and redirect to login for fresh authentication
      setTimeout(() => {
        signOut()
        navigate('/login', {
          replace: true,
          state: {
            message: 'Password changed successfully. Please sign in with your new password.',
          },
        })
      }, 1000)
    } catch (err) {
      if (err instanceof Error) {
        setError(err.message)
      } else {
        setError('Unable to change password. Please try again.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section
      className="user-card change-password-card"
      aria-labelledby="change-password-heading"
    >
      <div className="account-card-heading">
        <div>
          <p className="dashboard-card-label">Security</p>
          <h2 id="change-password-heading">Change password</h2>
        </div>
      </div>

      <p className="change-password-description">
        Update your account password. Once changed, all existing sessions and access tokens will be immediately invalidated and you will be prompted to sign in again.
      </p>

      <form
        className="login-form change-password-form"
        onSubmit={handleSubmit}
        noValidate
      >
        <label htmlFor="current-password-input">
          Current password
          <input
            id="current-password-input"
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            autoComplete="current-password"
            disabled={isSubmitting}
            required
          />
        </label>

        <label htmlFor="new-password-input">
          New password
          <input
            id="new-password-input"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            autoComplete="new-password"
            disabled={isSubmitting}
            required
          />
          <span className="field-hint">Must be between 8 and 72 characters</span>
        </label>

        <label htmlFor="confirm-password-input">
          Confirm new password
          <input
            id="confirm-password-input"
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            autoComplete="new-password"
            disabled={isSubmitting}
            required
          />
        </label>

        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}

        {success && (
          <p className="form-success" role="status">
            {success}
          </p>
        )}

        <div className="change-password-actions">
          <button
            type="submit"
            disabled={isSubmitting}
          >
            {isSubmitting ? 'Updating password...' : 'Update password'}
          </button>
        </div>
      </form>
    </section>
  )
}
