import {
  type FormEvent,
  useEffect,
  useState,
} from 'react'
import {
  Link,
  Navigate,
  useLocation,
  useNavigate,
} from 'react-router-dom'
import {
  Layers,
  Mail,
  Lock,
  Eye,
  EyeOff,
  AlertCircle,
  CheckCircle2,
} from 'lucide-react'

import { getGoogleOAuthConfig } from '../api/auth'
import { useAuth } from '../auth/useAuth'
import type { GoogleOAuthConfigResponse } from '../types/auth'
import { generateCodeChallenge, generateRandomString } from '../utils/pkce'
import { ThemeSwitcher } from '../components/ui/ThemeSwitcher'

function getSafeRedirectPath(rawPath?: unknown): string {
  let candidate: string | null = null

  if (typeof rawPath === 'string') {
    candidate = rawPath
  } else if (
    rawPath &&
    typeof rawPath === 'object' &&
    'pathname' in rawPath &&
    typeof (rawPath as { pathname?: unknown }).pathname === 'string'
  ) {
    candidate = (rawPath as { pathname: string }).pathname
  }

  if (!candidate) {
    return '/dashboard'
  }

  const trimmed = candidate.trim()
  if (
    trimmed.startsWith('/') &&
    !trimmed.startsWith('//') &&
    !trimmed.startsWith('/\\') &&
    !trimmed.includes('\\') &&
    !trimmed.includes(':')
  ) {
    return trimmed
  }

  return '/dashboard'
}

export function LoginPage() {
  const {
    signIn,
    isAuthenticated,
  } = useAuth()

  const navigate = useNavigate()
  const location = useLocation()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [googleConfig, setGoogleConfig] = useState<GoogleOAuthConfigResponse | null>(null)

  const locationState = location.state as {
    message?: string
    sessionExpired?: boolean
    from?: { pathname?: string } | string
  } | null

  const targetPath = getSafeRedirectPath(locationState?.from)

  const isSessionExpired = Boolean(locationState?.sessionExpired)
  const sessionNotice = isSessionExpired
    ? (locationState?.message || 'Your session has expired. Please sign in again.')
    : null
  const successNotice = !isSessionExpired ? locationState?.message : null

  useEffect(() => {
    async function loadConfig() {
      try {
        const config = await getGoogleOAuthConfig()
        if (config.enabled) {
          setGoogleConfig(config)
        }
      } catch {
        // Fall back gracefully to password login
      }
    }
    void loadConfig()
  }, [])

  if (isAuthenticated) {
    return <Navigate to={targetPath} replace />
  }

  async function handleGoogleLogin() {
    if (!googleConfig || !googleConfig.enabled) return

    const state = crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2)
    const codeVerifier = generateRandomString(32)
    const codeChallenge = await generateCodeChallenge(codeVerifier)

    sessionStorage.setItem('oauth_state', state)
    sessionStorage.setItem('oauth_code_verifier', codeVerifier)

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
    setIsSubmitting(true)

    try {
      await signIn(email, password)
      const targetPath = getSafeRedirectPath(locationState?.from)
      navigate(targetPath, { replace: true })
    } catch (caughtError) {
      if (caughtError instanceof Error) {
        setError(caughtError.message)
      } else {
        setError('Unable to sign in')
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

      <section className="login-card horizon-auth-card" aria-labelledby="login-heading-id">
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
          <h1 id="login-heading-id">Welcome back</h1>
          <p className="horizon-auth-subtitle">
            Sign in to manage your IT service requests and operational workflows.
          </p>
        </div>

        {/* Alerts */}
        {sessionNotice && (
          <div className="form-error horizon-auth-alert horizon-auth-alert--error" role="alert">
            <AlertCircle size={18} className="horizon-auth-alert-icon" />
            <span>{sessionNotice}</span>
          </div>
        )}

        {successNotice && (
          <div className="form-success horizon-auth-alert horizon-auth-alert--success" role="status">
            <CheckCircle2 size={18} className="horizon-auth-alert-icon" />
            <span>{successNotice}</span>
          </div>
        )}

        {/* Google OAuth Option */}
        {googleConfig?.enabled && (
          <div style={{ marginBottom: '1.25rem' }}>
            <button
              type="button"
              className="google-button horizon-auth-google-btn"
              onClick={handleGoogleLogin}
              disabled={isSubmitting}
            >
              <svg
                className="google-icon"
                viewBox="0 0 24 24"
                width="18"
                height="18"
                xmlns="http://www.w3.org/2000/svg"
                aria-hidden="true"
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
              <span>Continue with Google</span>
            </button>
            <div className="auth-divider horizon-auth-divider">
              <span>or sign in with password</span>
            </div>
          </div>
        )}

        {/* Credentials Form */}
        <form
          className="login-form horizon-auth-form"
          onSubmit={handleSubmit}
        >
          {/* Email field */}
          <div className="horizon-form-field">
            <label htmlFor="login-email" className="horizon-label">
              Email
            </label>
            <div className="horizon-auth-input-wrapper">
              <span className="horizon-auth-input-icon" aria-hidden="true">
                <Mail size={16} />
              </span>
              <input
                id="login-email"
                name="email"
                type="email"
                className="horizon-auth-input horizon-auth-input-with-icon"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                autoComplete="username"
                placeholder="name@company.com"
                required
              />
            </div>
          </div>

          {/* Password field */}
          <div className="horizon-form-field">
            <div className="horizon-auth-label-row">
              <label htmlFor="login-password" className="horizon-label">
                Password
              </label>
              <Link to="/forgot-password" className="forgot-password-link horizon-auth-link">
                Forgot password?
              </Link>
            </div>

            <div className="password-input-wrapper horizon-auth-input-wrapper">
              <span className="horizon-auth-input-icon" aria-hidden="true">
                <Lock size={16} />
              </span>
              <input
                id="login-password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                className="horizon-auth-input horizon-auth-input-with-icon"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
                placeholder="••••••••••••"
                required
              />
              <button
                type="button"
                className="password-toggle-btn horizon-auth-password-toggle"
                onClick={() => setShowPassword(!showPassword)}
                tabIndex={-1}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? <EyeOff size={16} aria-hidden="true" /> : <Eye size={16} aria-hidden="true" />}
                <span style={{ marginLeft: '4px' }}>{showPassword ? 'Hide' : 'Show'}</span>
              </button>
            </div>
          </div>

          {/* Error Message */}
          {error && (
            <div className="form-error horizon-auth-alert horizon-auth-alert--error" role="alert">
              <AlertCircle size={18} className="horizon-auth-alert-icon" />
              <span>{error}</span>
            </div>
          )}

          {/* Submit Button */}
          <button
            type="submit"
            className="horizon-btn horizon-btn--primary horizon-btn--md horizon-btn--full-width"
            disabled={isSubmitting}
            style={{ marginTop: '0.5rem' }}
          >
            {isSubmitting ? 'Signing in...' : 'Sign in'}
          </button>
        </form>

        <div className="horizon-auth-footer">
          <p>FlowDesk Horizon &bull; Enterprise IT Service Management</p>
        </div>
      </section>
    </main>
  )
}
