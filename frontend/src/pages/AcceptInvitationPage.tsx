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
import { acceptInvitation, getGoogleOAuthConfig } from '../api/auth'
import { useAuth } from '../auth/useAuth'
import type { GoogleOAuthConfigResponse } from '../types/auth'
import { generateCodeChallenge, generateRandomString } from '../utils/pkce'

export function AcceptInvitationPage() {
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
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isSuccess, setIsSuccess] = useState(false)
  const [googleConfig, setGoogleConfig] = useState<GoogleOAuthConfigResponse | null>(null)

  useEffect(() => {
    async function loadConfig() {
      try {
        const config = await getGoogleOAuthConfig()
        if (config.enabled) {
          setGoogleConfig(config)
        }
      } catch {
        // Silently fall back to password setup
      }
    }
    void loadConfig()
  }, [])

  if (!token) {
    return (
      <main className="login-page">
        <section className="login-card" aria-labelledby="invitation-error-heading">
          <div className="login-heading">
            <p className="eyebrow">FlowDesk</p>
            <h1 id="invitation-error-heading">Invalid invitation link</h1>
            <p>
              This account activation link is invalid, incomplete, or has expired.
            </p>
          </div>

          <p className="form-error" role="alert">
            Please make sure you clicked or copied the complete invitation link from your email, or contact your administrator to resend the invitation.
          </p>

          <div className="login-secondary-actions" style={{ marginTop: '1.5rem' }}>
            <Link to="/login" className="back-to-login-link">
              &larr; Return to sign in
            </Link>
          </div>
        </section>
      </main>
    )
  }

  async function handleGoogleActivate() {
    if (!googleConfig || !googleConfig.enabled) return

    // Generate random state for CSRF mitigation
    const state = crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2)
    const codeVerifier = generateRandomString(32)
    const codeChallenge = await generateCodeChallenge(codeVerifier)

    sessionStorage.setItem('oauth_state', state)
    sessionStorage.setItem('oauth_code_verifier', codeVerifier)
    sessionStorage.setItem('oauth_invitation_token', token)

    const params = new URLSearchParams({
      client_id: googleConfig.clientId,
      redirect_uri: googleConfig.redirectUri,
      response_type: 'code',
      scope: 'openid email profile',
      state,
      code_challenge: codeChallenge,
      code_challenge_method: 'S256',
      prompt: 'select_account',
    })

    window.location.href = `${googleConfig.authUri}?${params.toString()}`
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    if (!newPassword) {
      setError('Password is required.')
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
      await acceptInvitation({
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
            message: 'Your account has been activated successfully! Please sign in with your new password.',
          },
        })
      }, 2500)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to complete account setup.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-card" aria-labelledby="invitation-heading">
        <div className="login-heading">
          <p className="eyebrow">FlowDesk</p>
          <h1 id="invitation-heading">Set up your account</h1>
          <p>
            Welcome to FlowDesk. Please choose a secure password to activate your account.
          </p>
        </div>

        {isSuccess ? (
          <div className="reset-success-block" role="status">
            <p className="form-success">
              Account activated successfully! Redirecting you to sign in...
            </p>
            <div className="login-secondary-actions" style={{ marginTop: '1.5rem' }}>
              <Link to="/login" className="back-to-login-link">
                Sign in now &rarr;
              </Link>
            </div>
          </div>
        ) : (
          <div>
            {googleConfig?.enabled && (
              <div style={{ marginBottom: '1.5rem' }}>
                <button
                  type="button"
                  className="google-button"
                  onClick={handleGoogleActivate}
                  disabled={isSubmitting}
                >
                  <svg
                    className="google-icon"
                    viewBox="0 0 24 24"
                    width="18"
                    height="18"
                    xmlns="http://www.w3.org/2000/svg"
                  >
                    <path
                      d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                      fill="#4285F4"
                    />
                    <path
                      d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                      fill="#34A853"
                    />
                    <path
                      d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                      fill="#FBBC05"
                    />
                    <path
                      d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                      fill="#EA4335"
                    />
                  </svg>
                  <span>Activate with Google</span>
                </button>
                <div className="auth-divider">
                  <span>or set a FlowDesk password</span>
                </div>
              </div>
            )}

            <form className="login-form" onSubmit={handleSubmit} noValidate>
              <label htmlFor="accept-new-password">
                New password
                <div className="password-input-wrapper">
                  <input
                    id="accept-new-password"
                    name="newPassword"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="new-password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    disabled={isSubmitting}
                    minLength={8}
                    maxLength={72}
                    required
                    placeholder="At least 8 characters"
                  />
                  <button
                    type="button"
                    className="password-toggle-btn"
                    onClick={() => setShowPassword(!showPassword)}
                    tabIndex={-1}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                  >
                    {showPassword ? 'Hide' : 'Show'}
                  </button>
                </div>
                <span className="field-hint">Must be between 8 and 72 characters</span>
              </label>

              <label htmlFor="accept-confirm-password">
                Confirm new password
                <div className="password-input-wrapper">
                  <input
                    id="accept-confirm-password"
                    name="confirmPassword"
                    type={showConfirmPassword ? 'text' : 'password'}
                    autoComplete="new-password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    disabled={isSubmitting}
                    minLength={8}
                    maxLength={72}
                    required
                    placeholder="Re-enter your password"
                  />
                  <button
                    type="button"
                    className="password-toggle-btn"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    tabIndex={-1}
                    aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                  >
                    {showConfirmPassword ? 'Hide' : 'Show'}
                  </button>
                </div>
              </label>

              {error && (
                <p className="form-error" role="alert">
                  {error}
                </p>
              )}

              <button
                type="submit"
                disabled={isSubmitting}
                style={{ marginTop: '0.5rem' }}
              >
                {isSubmitting ? 'Activating account...' : 'Activate account'}
              </button>

              <div className="login-secondary-actions" style={{ textAlign: 'center' }}>
                <Link to="/login" className="back-to-login-link">
                  &larr; Already have a password? Sign in
                </Link>
              </div>
            </form>
          </div>
        )}
      </section>
    </main>
  )
}
