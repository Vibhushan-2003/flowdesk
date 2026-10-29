import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import {
  createDraftArticle,
  getArticleByIdForManagement,
  getCategories,
  transitionArticleStatus,
  updateArticle,
} from '../api/kb'
import { useAuth } from '../auth/useAuth'
import type { ArticleStatus, KbArticle, KbCategory } from '../types/kb'
import '../styles/kb.css'

export function ArticleEditorPage() {
  const { id } = useParams<{ id: string }>()
  const isEditing = Boolean(id)

  const { accessToken } = useAuth()
  const navigate = useNavigate()

  const [categories, setCategories] = useState<KbCategory[]>([])
  const [existingArticle, setExistingArticle] = useState<KbArticle | null>(null)

  // Form State
  const [title, setTitle] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [summary, setSummary] = useState('')
  const [contentMarkdown, setContentMarkdown] = useState('')

  // View state
  const [activeTab, setActiveTab] = useState<'edit' | 'preview'>('edit')
  const [isLoading, setIsLoading] = useState(true)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  // Load Categories & Existing Article
  useEffect(() => {
    let cancelled = false

    async function initialize() {
      if (!accessToken) return
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const catList = await getCategories(accessToken)
        if (!cancelled) {
          setCategories(catList)
          if (catList.length > 0) {
            setCategoryId((prev) => (prev ? prev : catList[0].id))
          }
        }

        if (isEditing && id) {
          const art = await getArticleByIdForManagement(accessToken, id)
          if (!cancelled) {
            setExistingArticle(art)
            setTitle(art.title)
            setCategoryId(art.categoryId)
            setSummary(art.summary)
            setContentMarkdown(art.contentMarkdown)
          }
        }
      } catch (err) {
        if (!cancelled) {
          setErrorMessage(err instanceof Error ? err.message : 'Failed to load editor data')
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    void initialize()
    return () => {
      cancelled = true
    }
  }, [accessToken, id, isEditing])

  function validate(): boolean {
    if (!title.trim() || title.trim().length < 3) {
      setErrorMessage('Title must be at least 3 characters.')
      return false
    }
    if (!categoryId) {
      setErrorMessage('Please select a category.')
      return false
    }
    if (!summary.trim() || summary.trim().length < 5) {
      setErrorMessage('Summary must be at least 5 characters.')
      return false
    }
    if (!contentMarkdown.trim()) {
      setErrorMessage('Article content cannot be empty.')
      return false
    }
    setErrorMessage(null)
    return true
  }

  async function handleSave(targetStatus?: ArticleStatus) {
    if (!validate() || !accessToken) return
    setIsSubmitting(true)
    setErrorMessage(null)
    setSuccessMessage(null)

    try {
      if (isEditing && id) {
        // Update content first
        await updateArticle(accessToken, id, {
          title: title.trim(),
          categoryId,
          summary: summary.trim(),
          contentMarkdown: contentMarkdown.trim(),
        })

        // Status transition if requested
        if (targetStatus && targetStatus !== existingArticle?.status) {
          await transitionArticleStatus(accessToken, id, targetStatus)
        }

        setSuccessMessage('Article updated successfully!')
        setTimeout(() => {
          navigate('/kb')
        }, 1200)
      } else {
        // Create new draft
        const created = await createDraftArticle(accessToken, {
          title: title.trim(),
          categoryId,
          summary: summary.trim(),
          contentMarkdown: contentMarkdown.trim(),
        })

        // If publish requested immediately
        if (targetStatus === 'PUBLISHED') {
          await transitionArticleStatus(accessToken, created.id, 'PUBLISHED')
        }

        setSuccessMessage('Article created successfully!')
        setTimeout(() => {
          navigate('/kb')
        }, 1200)
      }
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : 'Failed to save article')
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleStatusChange(status: ArticleStatus) {
    if (!id || !accessToken) return
    setIsSubmitting(true)
    setErrorMessage(null)

    try {
      await transitionArticleStatus(accessToken, id, status)
      setSuccessMessage(`Article status updated to ${status}!`)
      setExistingArticle((prev) => (prev ? { ...prev, status } : null))
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : 'Failed to update article status')
    } finally {
      setIsSubmitting(false)
    }
  }

  if (isLoading) {
    return (
      <main className="kb-container" style={{ textAlign: 'center', padding: '4rem 1rem', color: '#64748b' }}>
        Loading article editor...
      </main>
    )
  }

  return (
    <main className="kb-container">
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <div>
          <button
            type="button"
            className="dashboard-secondary-button"
            onClick={() => navigate('/kb')}
            style={{ marginBottom: '0.5rem' }}
          >
            ← Back to Knowledge Base
          </button>
          <h1 style={{ fontSize: '1.85rem', fontWeight: 700, color: '#0f172a', margin: 0 }}>
            {isEditing ? `Edit Article: ${existingArticle?.title ?? ''}` : 'Create Knowledge Base Article'}
          </h1>
          {existingArticle && (
            <span
              className={`kb-status-badge ${existingArticle.status.toLowerCase()}`}
              style={{ marginTop: '0.4rem', display: 'inline-block' }}
            >
              Current Status: {existingArticle.status}
            </span>
          )}
        </div>

        {/* Action Controls */}
        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
          <button
            type="button"
            className="dashboard-secondary-button"
            disabled={isSubmitting}
            onClick={() => navigate('/kb')}
          >
            Cancel
          </button>

          {!isEditing ? (
            <>
              <button
                type="button"
                className="dashboard-secondary-button"
                disabled={isSubmitting}
                onClick={() => handleSave('DRAFT')}
              >
                Save as Draft
              </button>
              <button
                type="button"
                className="kb-search-btn"
                disabled={isSubmitting}
                onClick={() => handleSave('PUBLISHED')}
              >
                {isSubmitting ? 'Saving...' : 'Publish Article'}
              </button>
            </>
          ) : (
            <>
              <button
                type="button"
                className="dashboard-secondary-button"
                disabled={isSubmitting}
                onClick={() => handleSave()}
              >
                Save Changes
              </button>

              {existingArticle?.status === 'DRAFT' && (
                <button
                  type="button"
                  className="kb-search-btn"
                  disabled={isSubmitting}
                  onClick={() => handleStatusChange('PUBLISHED')}
                >
                  Publish Article
                </button>
              )}

              {existingArticle?.status === 'PUBLISHED' && (
                <>
                  <button
                    type="button"
                    className="dashboard-secondary-button"
                    disabled={isSubmitting}
                    onClick={() => handleStatusChange('DRAFT')}
                  >
                    Unpublish to Draft
                  </button>
                  <button
                    type="button"
                    className="dashboard-secondary-button"
                    style={{ color: '#b45309' }}
                    disabled={isSubmitting}
                    onClick={() => handleStatusChange('ARCHIVED')}
                  >
                    Archive
                  </button>
                </>
              )}

              {existingArticle?.status === 'ARCHIVED' && (
                <button
                  type="button"
                  className="kb-search-btn"
                  disabled={isSubmitting}
                  onClick={() => handleStatusChange('PUBLISHED')}
                >
                  Republish Article
                </button>
              )}
            </>
          )}
        </div>
      </div>

      {/* Notifications */}
      {errorMessage && (
        <div className="error-banner" style={{ marginBottom: '1.5rem' }}>
          {errorMessage}
        </div>
      )}

      {successMessage && (
        <div
          style={{
            padding: '1rem',
            backgroundColor: '#ecfdf5',
            color: '#065f46',
            border: '1px solid #a7f3d0',
            borderRadius: '6px',
            marginBottom: '1.5rem',
            fontWeight: 500,
          }}
        >
          {successMessage}
        </div>
      )}

      {/* Form Fields */}
      <div className="kb-editor-layout">
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '1.5rem' }}>
          <div className="kb-editor-form-group">
            <label htmlFor="article-title">Article Title *</label>
            <input
              id="article-title"
              type="text"
              className="kb-editor-input"
              placeholder="e.g. How to Connect to FlowDesk Office VPN"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </div>

          <div className="kb-editor-form-group">
            <label htmlFor="article-category">Category *</label>
            <select
              id="article-category"
              className="kb-editor-select"
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
            >
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="kb-editor-form-group">
          <label htmlFor="article-summary">
            Summary / Overview * (Short description displayed in search results and deflection cards)
          </label>
          <textarea
            id="article-summary"
            rows={2}
            className="kb-editor-textarea"
            placeholder="Brief 1-2 sentence description of the solution..."
            value={summary}
            onChange={(e) => setSummary(e.target.value)}
          />
        </div>

        {/* Markdown Editor & Preview Tabs */}
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <label style={{ fontSize: '0.9rem', fontWeight: 600, color: '#334155' }}>
              Article Body (Markdown) *
            </label>
            <div className="kb-tabs">
              <button
                type="button"
                className={`kb-tab-btn ${activeTab === 'edit' ? 'active' : ''}`}
                style={{ padding: '0.35rem 0.85rem', fontSize: '0.85rem' }}
                onClick={() => setActiveTab('edit')}
              >
                Edit Markdown
              </button>
              <button
                type="button"
                className={`kb-tab-btn ${activeTab === 'preview' ? 'active' : ''}`}
                style={{ padding: '0.35rem 0.85rem', fontSize: '0.85rem' }}
                onClick={() => setActiveTab('preview')}
              >
                Live Preview
              </button>
            </div>
          </div>

          {activeTab === 'edit' ? (
            <textarea
              rows={18}
              className="kb-editor-textarea kb-markdown-textarea"
              placeholder="# Step-by-Step Guide&#10;&#10;Explain the troubleshooting procedure here using Markdown headings, lists, and code blocks...&#10;&#10;## Prerequisites&#10;- Ensure your laptop is connected to Wi-Fi.&#10;&#10;```bash&#10;ping vpn.flowdesk.internal&#10;```"
              value={contentMarkdown}
              onChange={(e) => setContentMarkdown(e.target.value)}
            />
          ) : (
            <div className="kb-preview-pane kb-article-body">
              {contentMarkdown.trim() ? (
                <ReactMarkdown>{contentMarkdown}</ReactMarkdown>
              ) : (
                <p style={{ color: '#94a3b8', fontStyle: 'italic' }}>
                  Nothing to preview yet. Switch back to Edit Markdown and write your guide.
                </p>
              )}
            </div>
          )}
        </div>
      </div>
    </main>
  )
}
