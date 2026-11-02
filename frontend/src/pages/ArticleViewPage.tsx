import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import {
  ArrowLeft,
  Calendar,
  User,
  Eye,
  Check,
  X,
  ExternalLink,
  Lightbulb,
} from 'lucide-react'

import { getArticleBySlug, recordArticleDeflection } from '../api/kb'
import { useAuth } from '../auth/useAuth'
import type { KbArticle } from '../types/kb'
import { formatDate } from '../utils/formatters'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import '../styles/kb.css'

export function ArticleViewPage() {
  const { slug } = useParams<{ slug: string }>()
  const { accessToken, user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const navState = location.state as {
    fromTicket?: boolean
    ticketDraft?: {
      type: string
      title: string
      description: string
    }
  } | null

  const isFromTicket = Boolean(navState?.fromTicket)
  const ticketDraft = navState?.ticketDraft

  const [article, setArticle] = useState<KbArticle | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [feedbackGiven, setFeedbackGiven] = useState<'yes' | 'no' | null>(null)

  const isStaff = user?.roles.some(
    (role) =>
      role === 'SUPPORT_ENGINEER' ||
      role === 'TEAM_LEAD' ||
      role === 'ADMIN',
  ) ?? false

  useEffect(() => {
    let cancelled = false

    async function loadArticle() {
      if (!accessToken || !slug) return
      setIsLoading(true)
      setError(null)

      try {
        const data = await getArticleBySlug(accessToken, slug)
        if (!cancelled) {
          setArticle(data)
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error
              ? err.message
              : 'Article not found or you do not have permission to view it',
          )
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    void loadArticle()
    return () => {
      cancelled = true
    }
  }, [accessToken, slug])

  async function handleFeedback(helpful: boolean) {
    if (!article || !accessToken) return
    setFeedbackGiven(helpful ? 'yes' : 'no')
    try {
      if (helpful) {
        await recordArticleDeflection(accessToken, article.id)
      }
    } catch {
      // Ignore feedback logging errors
    }
  }

  if (isLoading) {
    return (
      <div className="kb-container" style={{ textAlign: 'center', padding: '4rem 1rem', color: 'var(--color-text-secondary)' }}>
        <p>Loading article...</p>
      </div>
    )
  }

  if (error || !article) {
    return (
      <div className="kb-container">
        <div style={{ margin: '2rem 0' }}>
          <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
            {isFromTicket && (
              <Button
                variant="primary"
                size="sm"
                onClick={() => navigate('/tickets/new', { state: { ticketDraft } })}
                leadingIcon={<ArrowLeft size={14} />}
              >
                Return to Ticket Form
              </Button>
            )}
            <Button
              variant="secondary"
              size="sm"
              onClick={() => navigate('/kb')}
              leadingIcon={<ArrowLeft size={14} />}
            >
              Back to Knowledge Base
            </Button>
          </div>

          <div className="error-banner horizon-auth-alert horizon-auth-alert--error" style={{ padding: '2rem', textAlign: 'center' }}>
            <h2 style={{ marginBottom: '0.5rem', color: 'var(--color-danger)' }}>Article Unavailable</h2>
            <p style={{ color: 'var(--color-danger-text)', margin: 0 }}>
              {error ?? 'This article does not exist or has not yet been published.'}
            </p>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="kb-container horizon-article-view">
      {/* Return to Ticket Banner if navigated from ticket deflection */}
      {isFromTicket && (
        <aside
          className="kb-ticket-return-bar horizon-card"
          aria-label="Return to ticket submission"
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '1rem',
            padding: '12px 18px',
            marginBottom: '1.5rem',
            background: 'var(--color-primary-subtle)',
            border: '1px solid var(--color-primary-border)',
            borderRadius: 'var(--radius-lg)',
            flexWrap: 'wrap',
          }}
        >
          <div className="kb-ticket-return-content" style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-primary)' }}>
            <Lightbulb size={18} style={{ color: 'var(--color-primary)' }} />
            <span>
              Reviewing suggested guide for your ticket:{' '}
              <strong>{ticketDraft?.title || 'Draft ticket'}</strong>
            </span>
          </div>
          <Button
            variant="primary"
            size="sm"
            className="kb-ticket-return-btn"
            onClick={() => navigate('/tickets/new', { state: { ticketDraft } })}
            leadingIcon={<ArrowLeft size={14} />}
          >
            Return to Ticket Form
          </Button>
        </aside>
      )}

      {/* Header & Metadata */}
      <header className="kb-view-header horizon-card" style={{ padding: '2rem', marginBottom: '2rem' }}>
        <nav className="kb-breadcrumbs" style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', marginBottom: '1rem' }}>
          <Link to="/kb" style={{ color: 'var(--color-primary)', textDecoration: 'none' }}>
            Knowledge Base
          </Link>
          <span>/</span>
          <span>{article.categoryName}</span>
          <span>/</span>
          <span style={{ color: 'var(--color-text-primary)', fontWeight: 'var(--font-weight-medium)' }}>
            {article.title}
          </span>
        </nav>

        <div className="kb-view-title-row" style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '1.5rem', flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: '260px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.75rem' }}>
              <Badge variant="primary" size="md" className="kb-category-tag">
                {article.categoryName}
              </Badge>
              {article.status !== 'PUBLISHED' && (
                <Badge
                  variant={article.status === 'DRAFT' ? 'warning' : 'neutral'}
                  size="md"
                  className={`kb-status-badge ${article.status.toLowerCase()}`}
                >
                  {article.status}
                </Badge>
              )}
            </div>

            <h1 style={{ fontSize: 'var(--font-size-3xl)', fontWeight: 'var(--font-weight-bold)', color: 'var(--color-text-primary)', margin: '0 0 0.75rem', lineHeight: 'var(--line-height-tight)' }}>
              {article.title}
            </h1>

            <p style={{ fontSize: 'var(--font-size-md)', color: 'var(--color-text-secondary)', lineHeight: 'var(--line-height-relaxed)', margin: 0 }}>
              {article.summary}
            </p>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            {isStaff && (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => navigate(`/support/kb/articles/${article.id}/edit`)}
                trailingIcon={<ExternalLink size={14} />}
              >
                Edit Article
              </Button>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate('/kb')}
              leadingIcon={<ArrowLeft size={14} />}
            >
              Back
            </Button>
          </div>
        </div>

        <div className="kb-meta-bar" style={{ display: 'flex', alignItems: 'center', gap: '1.5rem', flexWrap: 'wrap', marginTop: '1.5rem', paddingTop: '1rem', borderTop: '1px solid var(--color-divider)', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
            <User size={13} />
            Author: <strong style={{ color: 'var(--color-text-primary)' }}>{article.authorName}</strong>
          </span>
          {article.publishedAt && (
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
              <Calendar size={13} />
              Published: <strong style={{ color: 'var(--color-text-primary)' }}>{formatDate(article.publishedAt)}</strong>
            </span>
          )}
          <span>
            Last updated: <strong style={{ color: 'var(--color-text-primary)' }}>{formatDate(article.updatedAt)}</strong>
          </span>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
            <Eye size={13} />
            {article.viewCount} views
          </span>
        </div>
      </header>

      {/* Markdown Content Article Body */}
      <article className="kb-article-body horizon-card" style={{ padding: '2.5rem', marginBottom: '2rem', lineHeight: 'var(--line-height-relaxed)', fontSize: 'var(--font-size-base)', color: 'var(--color-text-primary)' }}>
        <ReactMarkdown>{article.contentMarkdown}</ReactMarkdown>
      </article>

      {/* Helpful Feedback Box */}
      <section className="kb-feedback-box horizon-card" style={{ padding: '1.5rem 2rem', textAlign: 'center', maxWidth: '560px', margin: '0 auto 3rem' }}>
        {feedbackGiven ? (
          <div>
            <p style={{ color: 'var(--color-success)', fontWeight: 'var(--font-weight-semibold)', margin: '0 0 6px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}>
              <Check size={18} />
              Thank you for your feedback!
            </p>
            <p style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', margin: 0 }}>
              Your response helps us maintain high quality documentation.
            </p>
          </div>
        ) : (
          <div>
            <p style={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)', margin: '0 0 0.75rem' }}>
              Did this article resolve your issue?
            </p>
            <div style={{ display: 'flex', justifyContent: 'center', gap: '0.75rem' }}>
              <Button
                variant="outline"
                size="sm"
                onClick={() => handleFeedback(true)}
                leadingIcon={<Check size={14} style={{ color: 'var(--color-success)' }} />}
              >
                Yes, it helped
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => handleFeedback(false)}
                leadingIcon={<X size={14} style={{ color: 'var(--color-danger)' }} />}
              >
                No, I need more help
              </Button>
            </div>
          </div>
        )}
      </section>
    </div>
  )
}
