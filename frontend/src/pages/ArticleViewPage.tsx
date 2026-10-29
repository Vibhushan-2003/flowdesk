import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import { getArticleBySlug } from '../api/kb'
import { useAuth } from '../auth/useAuth'
import type { KbArticle } from '../types/kb'
import { formatDate } from '../utils/formatters'
import '../styles/kb.css'

export function ArticleViewPage() {
  const { slug } = useParams<{ slug: string }>()
  const { accessToken, user } = useAuth()
  const navigate = useNavigate()

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

  if (isLoading) {
    return (
      <main className="kb-container" style={{ textAlign: 'center', padding: '4rem 1rem', color: '#64748b' }}>
        Loading article...
      </main>
    )
  }

  if (error || !article) {
    return (
      <main className="kb-container">
        <div style={{ margin: '2rem 0' }}>
          <button
            type="button"
            className="dashboard-secondary-button"
            onClick={() => navigate('/kb')}
            style={{ marginBottom: '1.5rem' }}
          >
            ← Back to Knowledge Base
          </button>

          <div className="error-banner" style={{ padding: '2rem', textAlign: 'center' }}>
            <h2 style={{ marginBottom: '0.5rem', color: '#991b1b' }}>Article Unavailable</h2>
            <p style={{ color: '#b91c1c' }}>
              {error ?? 'This article does not exist or has not yet been published.'}
            </p>
          </div>
        </div>
      </main>
    )
  }

  return (
    <main className="kb-container">
      {/* Breadcrumbs */}
      <nav className="kb-breadcrumbs">
        <Link to="/kb">Knowledge Base</Link>
        <span>/</span>
        <span>{article.categoryName}</span>
        <span>/</span>
        <span style={{ color: '#0f172a', fontWeight: 500 }}>{article.title}</span>
      </nav>

      {/* Header */}
      <header className="kb-view-header">
        <div className="kb-view-title-row">
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
              <span className="kb-category-tag">{article.categoryName}</span>
              {article.status !== 'PUBLISHED' && (
                <span className={`kb-status-badge ${article.status.toLowerCase()}`}>
                  {article.status}
                </span>
              )}
            </div>
            <h1>{article.title}</h1>
          </div>

          {isStaff && (
            <button
              type="button"
              className="dashboard-secondary-button"
              onClick={() => navigate(`/support/kb/articles/${article.id}/edit`)}
            >
              Edit Article ↗
            </button>
          )}
        </div>

        <p style={{ fontSize: '1.1rem', color: '#475569', margin: '0.75rem 0 1rem', lineHeight: 1.5 }}>
          {article.summary}
        </p>

        <div className="kb-meta-bar">
          <span>Author: <strong>{article.authorName}</strong></span>
          {article.publishedAt && (
            <span>Published: <strong>{formatDate(article.publishedAt)}</strong></span>
          )}
          <span>Last Updated: <strong>{formatDate(article.updatedAt)}</strong></span>
          <span>Views: <strong>{article.viewCount}</strong></span>
        </div>
      </header>

      {/* Markdown Content Body */}
      <article className="kb-article-body">
        <ReactMarkdown>{article.contentMarkdown}</ReactMarkdown>
      </article>

      {/* Helpful Feedback Box */}
      <section className="kb-feedback-box">
        {feedbackGiven ? (
          <p style={{ color: '#059669', fontWeight: 600, margin: 0 }}>
            Thank you for your feedback! This helps us improve our knowledge base guides.
          </p>
        ) : (
          <div>
            <p style={{ fontWeight: 600, color: '#334155', margin: '0 0 0.5rem 0' }}>
              Was this article helpful?
            </p>
            <div className="kb-feedback-buttons">
              <button
                type="button"
                className="kb-feedback-btn"
                onClick={() => setFeedbackGiven('yes')}
              >
                👍 Yes, this solved my issue
              </button>
              <button
                type="button"
                className="kb-feedback-btn"
                onClick={() => setFeedbackGiven('no')}
              >
                👎 No, I still need help
              </button>
            </div>
          </div>
        )}
      </section>

      {/* Back button */}
      <div style={{ marginTop: '2.5rem' }}>
        <button
          type="button"
          className="dashboard-secondary-button"
          onClick={() => navigate('/kb')}
        >
          ← Back to Knowledge Base
        </button>
      </div>
    </main>
  )
}
