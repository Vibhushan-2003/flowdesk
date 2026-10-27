import {
  type FormEvent,
  useEffect,
  useRef,
  useState,
} from 'react'
import {
  Link,
  useNavigate,
  useSearchParams,
} from 'react-router-dom'
import { completeGoogleLink, googleLogin } from '../api/auth'
import { useAuth } from '../auth/useAuth'

export function GoogleCallbackPage() {
  const { setSessionToken } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()

  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [is403Forbidden, setIs403Forbidden] = useState(false)

  // 409 Conflict state: pending linking transaction token from server
  const [isLinkingRequired, setIsLinkingRequired] = useState(false)
  const [linkToken, setLinkToken] = useState<string | null>(null)
  const [linkingEmail, setLinkingEmail] = useState<string | null>(null)
  const [linkingMessage, setLinkingMessage] = useState<string | null>(null)
  const [flowDeskPassword, setFlowDeskPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [isLinkingSubmitting, setIsLinkingSubmitting] = useState(false)
  const [linkingError, setLinkingError] = useState<string | null>(null)

  // Synchronously capture callback parameters on mount
  const [params] = useState(() => ({
    code: searchParams.get('code'),
    state: searchParams.get('state'),
    errorParam: searchParams.get('error'),
    errorDesc: searchParams.get('error_description'),
  }))

  // Immediately strip sensitive OAuth query parameters from the browser address bar
  useEffect(() => {
    if (
      searchParams.has('code') ||
      searchParams.has('state') ||
      searchParams.has('error') ||
      searchParams.has('error_description')
    ) {
      window.history.replaceState({}, document.title, window.location.pathname)
    }
  }, [searchParams])

  const processedRef = useRef(false)

  useEffect(() => {
    if (processedRef.current) return
    processedRef.current = true

    async function handleCallback() {
      const { code, state, errorParam, errorDesc } = params

      if (errorParam) {
        setIsLoading(false)
        setError(errorDesc || `Authentication provider reported: ${errorParam}`)
        return
      }

      if (!code || !state) {
        setIsLoading(false)
        setError('Missing required authentication parameters (code or state).')
        return
      }

      const storedState = sessionStorage.getItem('oauth_state')
      if (!storedState || storedState !== state) {
        setIsLoading(false)
        setError('Invalid authentication state. This request may have expired or originated from an untrusted source.')
        return
      }

      const storedCodeVerifier = sessionStorage.getItem('oauth_code_verifier') || undefined
      const storedInvitationToken = sessionStorage.getItem('oauth_invitation_token') || undefined

      try {
        const response = await googleLogin({
          code,
          state,
          codeVerifier: storedCodeVerifier,
          invitationToken: storedInvitationToken,
        })

        sessionStorage.removeItem('oauth_state')
        sessionStorage.removeItem('oauth_code_verifier')
        sessionStorage.removeItem('oauth_invitation_token')

        await setSessionToken(response.accessToken)
        navigate('/dashboard', { replace: true })
      } catch (err: unknown) {
        setIsLoading(false)

        // Clear transient OAuth state to prevent any code replay attempts
        sessionStorage.removeItem('oauth_state')
        sessionStorage.removeItem('oauth_code_verifier')
        sessionStorage.removeItem('oauth_invitation_token')

        // Check if 409 Conflict (Structured account-linking requirement)
        if (
          err instanceof Error &&
          'status' in err &&
          err.status === 409 &&
          'linkToken' in err &&
          typeof (err as { linkToken?: unknown }).linkToken === 'string'
        ) {
          const linkingErr = err as Error & { status: 409; linkToken: string; email?: string }
          setIsLinkingRequired(true)
          setLinkToken(linkingErr.linkToken)
          setLinkingEmail(linkingErr.email || null)
          setLinkingMessage(linkingErr.message)
          return
        }

        // Check if 403 Forbidden (Uninvited or Inactive user)
        if (
          (err instanceof Error && 'status' in err && err.status === 403) ||
          (err instanceof Error &&
            (err.message.toLowerCase().includes('not registered') ||
              err.message.toLowerCase().includes('deactivated') ||
              err.message.toLowerCase().includes('access denied')))
        ) {
          setIs403Forbidden(true)
          setError(err instanceof Error ? err.message : 'Access restricted.')
          return
        }

        setError(err instanceof Error ? err.message : 'Authentication failed.')
      }
    }

    void handleCallback()
  }, [navigate, params, setSessionToken])

  async function handleLinkPasswordSubmit(event: FormEvent) {
    event.preventDefault()
    if (!linkToken) return

    setLinkingError(null)
    setIsLinkingSubmitting(true)

    try {
      const response = await completeGoogleLink({
        linkToken,
        flowDeskPassword,
      })

      await setSessionToken(response.accessToken)
      navigate('/dashboard', { replace: true })
    } catch (err: unknown) {
      setLinkingError(err instanceof Error ? err.message : 'Failed to verify password and link account.')
    } finally {
      setIsLinkingSubmitting(false)
    }
  }

  if (isLoading) {
    return (
      <main className="login-page">
        <section className="login-card" style={{ textAlign: 'center' }}>
          <div className="login-heading">
            <p className="eyebrow">FlowDesk</p>
            <h1>Signing you in</h1>
            <p>Connecting to Google and verifying your credentials...</p>
          </div>
          <div style={{ marginTop: '2rem', display: 'flex', justifyContent: 'center' }}>
            <div
              style={{
                width: '40px',
                height: '40px',
                border: '3px solid #e5e7eb',
                borderTopColor: '#1f2937',
                borderRadius: '50%',
                animation: 'spin 1s linear infinite',
              }}
            />
          </div>
        </section>
      </main>
    )
  }

  if (isLinkingRequired) {
    return (
      <main className="login-page">
        <section className="login-card" aria-labelledby="linking-heading">
          <div className="login-heading">
            <p className="eyebrow">FlowDesk Security</p>
            <h1 id="linking-heading">Link Google Account</h1>
            <p>
              {linkingMessage ||
                (linkingEmail
                  ? `An existing FlowDesk account was found for ${linkingEmail}. To confirm your identity and link Google sign-in, please enter your FlowDesk password.`
                  : 'An existing FlowDesk account was found for your email address. To confirm your identity and link Google sign-in, please enter your FlowDesk password.')}
            </p>
          </div>

          <form className="login-form" onSubmit={handleLinkPasswordSubmit} noValidate>
            <label htmlFor="flowdesk-password">
              FlowDesk password
              <div className="password-input-wrapper">
                <input
                  id="flowdesk-password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  value={flowDeskPassword}
                  onChange={(e) => setFlowDeskPassword(e.target.value)}
                  disabled={isLinkingSubmitting}
                  required
                  placeholder="Enter your existing password"
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
              <span className="field-hint">
                Enter your existing FlowDesk password once to link this Google account permanently.
              </span>
            </label>

            {linkingError && (
              <p className="form-error" role="alert">
                {linkingError}
              </p>
            )}

            <button
              type="submit"
              disabled={isLinkingSubmitting || !flowDeskPassword.trim()}
              style={{ marginTop: '0.5rem' }}
            >
              {isLinkingSubmitting ? 'Verifying & linking...' : 'Link account & sign in'}
            </button>

            <div className="login-secondary-actions" style={{ marginTop: '1.5rem', textAlign: 'center' }}>
              <Link to="/login" className="back-to-login-link">
                &larr; Cancel and return to sign in
              </Link>
            </div>
          </form>
        </section>
      </main>
    )
  }

  return (
    <main className="login-page">
      <section className="login-card" aria-labelledby="auth-error-heading">
        <div className="login-heading">
          <p className="eyebrow">FlowDesk</p>
          <h1 id="auth-error-heading">
            {is403Forbidden ? 'Access restricted' : 'Sign in failed'}
          </h1>
          <p>
            {is403Forbidden
              ? 'FlowDesk is an invitation-only service.'
              : 'We were unable to complete your sign in with Google.'}
          </p>
        </div>

        <p className="form-error" role="alert" style={{ marginBottom: '1.5rem' }}>
          {error || 'An unexpected error occurred during Google authentication.'}
        </p>

        {is403Forbidden && (
          <p style={{ fontSize: '0.875rem', color: '#4b5563', lineHeight: 1.5, marginBottom: '1.5rem' }}>
            If you are a member of an organization using FlowDesk, ask your administrator to send you an invitation email. If you already received an invitation email, click the activation link in that email to set up your account.
          </p>
        )}

        <div className="login-secondary-actions" style={{ textAlign: 'center' }}>
          <Link to="/login" className="primary-button" style={{ display: 'inline-block', width: 'auto' }}>
            &larr; Return to sign in
          </Link>
        </div>
      </section>
    </main>
  )
}
