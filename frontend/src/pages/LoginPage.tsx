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

import { getGoogleOAuthConfig } from '../api/auth'
import { useAuth } from '../auth/useAuth'
import type { GoogleOAuthConfigResponse } from '../types/auth'
import { generateCodeChallenge, generateRandomString } from '../utils/pkce'

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

  const successNotice = (location.state as { message?: string } | null)?.message

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
    return <Navigate to="/dashboard" replace />
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

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    setError(null)
    setIsSubmitting(true)

    try {
      await signIn(email, password)

      navigate('/dashboard', {
        replace: true,
      })
    } catch (error) {
      if (error instanceof Error) {
        setError(error.message)
      } else {
        setError('Unable to sign in')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-card">
        <div className="login-heading">
          <p className="eyebrow">FlowDesk</p>

          <h1>Welcome back</h1>

          <p>
            Sign in to manage your IT service requests.
          </p>
        </div>

        {successNotice && (
          <p
            className="form-success"
            role="status"
          >
            {successNotice}
          </p>
        )}

        {googleConfig?.enabled && (
          <div style={{ marginBottom: '1.25rem' }}>
            <button
              type="button"
              className="google-button"
              onClick={handleGoogleLogin}
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
              <span>Continue with Google</span>
            </button>
            <div className="auth-divider">
              <span>or sign in with password</span>
            </div>
          </div>
        )}

        <form
          className="login-form"
          onSubmit={handleSubmit}
        >
          <label htmlFor="login-email">
            Email

            <input
              id="login-email"
              name="email"
              type="email"
              value={email}
              onChange={(event) =>
                setEmail(event.target.value)
              }
              autoComplete="username"
              required
            />
          </label>

          <label htmlFor="login-password">
            <div className="label-with-link">
              <span>Password</span>
              <Link to="/forgot-password" className="forgot-password-link">
                Forgot password?
              </Link>
            </div>

            <div className="password-input-wrapper">
              <input
                id="login-password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(event) =>
                  setPassword(event.target.value)
                }
                autoComplete="current-password"
                required
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
          </label>

          {error && (
            <p
              className="form-error"
              role="alert"
            >
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={isSubmitting}
            style={{ marginTop: '0.25rem' }}
          >
            {isSubmitting
              ? 'Signing in...'
              : 'Sign in'}
          </button>
        </form>
      </section>
    </main>
  )
}