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
  Clock,
  Sparkles,
  ShieldCheck,
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
    <div className="horizon-auth-split-layout">
      {/* Left Brand Showcase Panel (Desktop) */}
      <aside className="horizon-auth-brand-side" aria-label="Product introduction">
        <div className="horizon-auth-brand-header">
          <div className="horizon-auth-brand-logo" aria-hidden="true">
            <Layers size={22} />
          </div>
          <span className="horizon-auth-brand-title">FlowDesk</span>
          <span
            style={{
              fontSize: '11px',
              fontWeight: 600,
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
              background: 'rgba(59, 130, 246, 0.2)',
              color: '#93c5fd',
              padding: '2px 8px',
              borderRadius: '999px',
              border: '1px solid rgba(59, 130, 246, 0.3)',
            }}
          >
            Horizon
          </span>
        </div>

        <div className="horizon-auth-brand-content">
          <h2 className="horizon-auth-hero-heading">
            Precision Workspace for IT Service Operations
          </h2>
          <p className="horizon-auth-hero-desc">
            Empower service desks and engineering teams with continuous SLA monitoring, Gemini AI triage assistance, and role-based operational workflows.
          </p>

          <div className="horizon-auth-feature-list">
            <div className="horizon-auth-feature-item">
              <div className="horizon-auth-feature-icon" aria-hidden="true">
                <Clock size={18} />
              </div>
              <div className="horizon-auth-feature-text">
                <h3>Real-Time SLA Engine</h3>
                <p>Continuous response and resolution tracking with automated breach prevention.</p>
              </div>
            </div>

            <div className="horizon-auth-feature-item">
              <div className="horizon-auth-feature-icon" aria-hidden="true">
                <Sparkles size={18} />
              </div>
              <div className="horizon-auth-feature-text">
                <h3>Gemini AI Copilot & Triage</h3>
                <p>Context-aware resolution drafting, KB self-service deflection, and smart categorization.</p>
              </div>
            </div>

            <div className="horizon-auth-feature-item">
              <div className="horizon-auth-feature-icon" aria-hidden="true">
                <ShieldCheck size={18} />
              </div>
              <div className="horizon-auth-feature-text">
                <h3>Enterprise Role Governance</h3>
                <p>Strict RBAC boundaries, malware quarantine protection, and immutable audit logging.</p>
              </div>
            </div>
          </div>
        </div>

        <div className="horizon-auth-brand-footer">
          <span>Enterprise Service Operations &bull; Zero-Trust Authorization &bull; FlowDesk Horizon</span>
        </div>
      </aside>

      {/* Right Form Panel */}
      <main className="horizon-auth-form-side">
        <div className="horizon-auth-theme-toggle">
          <ThemeSwitcher variant="toggle" />
        </div>

        <div className="horizon-auth-form-container">
          <section className="login-card horizon-auth-card" aria-labelledby="login-heading-id">
            {/* Mobile Brand Row */}
            <div
              className="horizon-auth-brand-row"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                marginBottom: '1.25rem',
              }}
            >
              <div
                style={{
                  width: '32px',
                  height: '32px',
                  borderRadius: 'var(--radius-md)',
                  background: 'linear-gradient(135deg, var(--color-primary), var(--color-primary-active))',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#ffffff',
                }}
                aria-hidden="true"
              >
                <Layers size={18} />
              </div>
              <span style={{ fontWeight: 700, fontSize: 'var(--font-size-lg)', color: 'var(--color-text-primary)' }}>
                FlowDesk
              </span>
              <span
                style={{
                  fontSize: '10px',
                  fontWeight: 600,
                  textTransform: 'uppercase',
                  letterSpacing: '0.04em',
                  background: 'var(--color-primary-subtle)',
                  color: 'var(--color-primary)',
                  padding: '1px 6px',
                  borderRadius: '999px',
                  border: '1px solid var(--color-primary-border)',
                }}
              >
                Horizon
              </span>
            </div>

            {/* Headings */}
            <div className="login-heading horizon-auth-heading">
              <h1 id="login-heading-id" style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 700, margin: '0 0 0.5rem', color: 'var(--color-text-primary)' }}>
                Welcome back
              </h1>
              <p className="horizon-auth-subtitle" style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', margin: '0 0 1.5rem' }}>
                Sign in to manage your IT service requests and operational workflows.
              </p>
            </div>

            {/* Alerts */}
            {sessionNotice && (
              <div className="form-error horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ marginBottom: '1.25rem' }}>
                <AlertCircle size={18} className="horizon-auth-alert-icon" />
                <span>{sessionNotice}</span>
              </div>
            )}

            {successNotice && (
              <div className="form-success horizon-auth-alert horizon-auth-alert--success" role="status" style={{ marginBottom: '1.25rem' }}>
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
                <div className="horizon-auth-label-row" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '4px' }}>
                  <label htmlFor="login-password" className="horizon-label">
                    Password
                  </label>
                  <Link to="/forgot-password" className="forgot-password-link horizon-auth-link" style={{ fontSize: 'var(--font-size-xs)' }}>
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
                <div className="form-error horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ marginBottom: '1rem' }}>
                  <AlertCircle size={18} className="horizon-auth-alert-icon" />
                  <span>{error}</span>
                </div>
              )}

              {/* Submit Button */}
              <button
                type="submit"
                className="horizon-btn horizon-btn--primary horizon-btn--md horizon-btn--full-width"
                disabled={isSubmitting}
                style={{ marginTop: '0.75rem' }}
              >
                {isSubmitting ? 'Signing in...' : 'Sign in'}
              </button>
            </form>

            <div className="horizon-auth-footer" style={{ marginTop: '1.5rem', textAlign: 'center', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
              <p style={{ margin: 0 }}>FlowDesk Horizon &bull; Enterprise IT Service Management</p>
            </div>
          </section>
        </div>
      </main>
    </div>
  )
}
