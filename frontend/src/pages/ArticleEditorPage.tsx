import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import {
  Save,
  CheckCircle2,
  AlertCircle,
  Eye,
  Edit3,
  Archive,
  Send,
} from 'lucide-react'

import {
  createDraftArticle,
  getArticleByIdForManagement,
  getCategories,
  transitionArticleStatus,
  updateArticle,
} from '../api/kb'
import { useAuth } from '../auth/useAuth'
import type { ArticleStatus, KbArticle, KbCategory } from '../types/kb'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { Card } from '../components/ui/Card'
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
      <div className="kb-container" style={{ textAlign: 'center', padding: '4rem 1rem', color: 'var(--color-text-secondary)' }}>
        <p>Loading article editor...</p>
      </div>
    )
  }

  return (
    <div className="kb-container horizon-article-editor">
      {/* Page Header */}
      <PageHeader
        title={isEditing ? 'Edit Knowledge Base Article' : 'Create Knowledge Base Article'}
        subtitle={
          existingArticle ? (
            <span style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span>{existingArticle.title}</span>
              <span>&bull;</span>
              <Badge
                variant={existingArticle.status === 'PUBLISHED' ? 'success' : existingArticle.status === 'DRAFT' ? 'warning' : 'neutral'}
                size="sm"
              >
                {existingArticle.status}
              </Badge>
            </span>
          ) : (
            'Draft self-service guidance and operating procedures for employees.'
          )
        }
        actions={
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
            <Button
              variant="secondary"
              size="sm"
              disabled={isSubmitting}
              onClick={() => navigate('/kb')}
            >
              Cancel
            </Button>

            {!isEditing ? (
              <>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={isSubmitting}
                  onClick={() => handleSave('DRAFT')}
                  leadingIcon={<Save size={14} />}
                >
                  Save Draft
                </Button>
                <Button
                  variant="primary"
                  size="sm"
                  disabled={isSubmitting}
                  onClick={() => handleSave('PUBLISHED')}
                  leadingIcon={<Send size={14} />}
                >
                  Publish Immediately
                </Button>
              </>
            ) : (
              <>
                <Button
                  variant="primary"
                  size="sm"
                  disabled={isSubmitting}
                  onClick={() => handleSave()}
                  leadingIcon={<Save size={14} />}
                >
                  Save Changes
                </Button>

                {existingArticle?.status === 'DRAFT' && (
                  <Button
                    variant="primary"
                    size="sm"
                    disabled={isSubmitting}
                    onClick={() => handleStatusChange('PUBLISHED')}
                    leadingIcon={<Send size={14} />}
                  >
                    Publish
                  </Button>
                )}

                {existingArticle?.status === 'PUBLISHED' && (
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={isSubmitting}
                    onClick={() => handleStatusChange('ARCHIVED')}
                    leadingIcon={<Archive size={14} />}
                  >
                    Archive
                  </Button>
                )}

                {existingArticle?.status === 'ARCHIVED' && (
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={isSubmitting}
                    onClick={() => handleStatusChange('DRAFT')}
                    leadingIcon={<Edit3 size={14} />}
                  >
                    Restore to Draft
                  </Button>
                )}
              </>
            )}
          </div>
        }
      />

      {/* Feedback Alerts */}
      {errorMessage && (
        <div className="error-banner horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ marginBottom: '1.5rem' }}>
          <AlertCircle size={18} className="horizon-auth-alert-icon" />
          <span>{errorMessage}</span>
        </div>
      )}

      {successMessage && (
        <div className="success-banner horizon-auth-alert horizon-auth-alert--success" role="status" style={{ marginBottom: '1.5rem' }}>
          <CheckCircle2 size={18} className="horizon-auth-alert-icon" />
          <span>{successMessage}</span>
        </div>
      )}

      {/* Editor & Preview Form Container */}
      <Card padded style={{ marginBottom: '2rem' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          {/* Metadata Controls: Category & Title */}
          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 260px', gap: '1.25rem', alignItems: 'start' }}>
            <div className="horizon-form-field" style={{ margin: 0 }}>
              <label htmlFor="article-title" className="horizon-label">
                Article Title <span className="horizon-field-required">*</span>
              </label>
              <div className="horizon-input-wrapper">
                <input
                  id="article-title"
                  type="text"
                  className="horizon-input"
                  placeholder="e.g. How to Connect to Corporate VPN from macOS"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  disabled={isSubmitting}
                  required
                />
              </div>
            </div>

            <div className="horizon-form-field" style={{ margin: 0 }}>
              <label htmlFor="article-category" className="horizon-label">
                Category <span className="horizon-field-required">*</span>
              </label>
              <div className="horizon-select-wrapper">
                <select
                  id="article-category"
                  className="horizon-select"
                  value={categoryId}
                  onChange={(e) => setCategoryId(e.target.value)}
                  disabled={isSubmitting}
                  required
                >
                  <option value="">Select a category</option>
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          {/* Short Summary */}
          <div className="horizon-form-field" style={{ margin: 0 }}>
            <label htmlFor="article-summary" className="horizon-label">
              Short Summary <span className="horizon-field-required">*</span>
            </label>
            <div className="horizon-input-wrapper">
              <input
                id="article-summary"
                type="text"
                className="horizon-input"
                placeholder="A concise 1-2 sentence overview shown in search results and deflection cards"
                value={summary}
                maxLength={300}
                onChange={(e) => setSummary(e.target.value)}
                disabled={isSubmitting}
                required
              />
            </div>
            <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', marginTop: '4px', textAlign: 'right' }}>
              {summary.length}/300
            </span>
          </div>

          {/* Edit vs Preview Segmented Switcher */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid var(--color-divider)', paddingBottom: '0.75rem', marginTop: '0.5rem' }}>
            <div style={{ display: 'inline-flex', padding: '3px', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', gap: '4px' }}>
              <button
                type="button"
                className={`horizon-btn horizon-btn--sm ${activeTab === 'edit' ? 'horizon-btn--primary' : 'horizon-btn--ghost'}`}
                onClick={() => setActiveTab('edit')}
              >
                <Edit3 size={14} />
                <span>Write (Markdown)</span>
              </button>
              <button
                type="button"
                className={`horizon-btn horizon-btn--sm ${activeTab === 'preview' ? 'horizon-btn--primary' : 'horizon-btn--ghost'}`}
                onClick={() => setActiveTab('preview')}
              >
                <Eye size={14} />
                <span>Preview</span>
              </button>
            </div>

            <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
              GitHub Flavored Markdown supported
            </span>
          </div>

          {/* Editor Body or Preview Display */}
          {activeTab === 'edit' ? (
            <div className="horizon-form-field" style={{ margin: 0 }}>
              <label htmlFor="article-content" className="sr-only">
                Article Content
              </label>
              <div className="horizon-input-wrapper">
                <textarea
                  id="article-content"
                  className="horizon-textarea"
                  style={{ minHeight: '380px', fontFamily: 'var(--font-family-mono)', fontSize: 'var(--font-size-sm)' }}
                  placeholder="## Overview&#10;&#10;Write detailed instructions, step-by-step troubleshooting, requirements, and commands here..."
                  value={contentMarkdown}
                  onChange={(e) => setContentMarkdown(e.target.value)}
                  disabled={isSubmitting}
                  required
                />
              </div>
            </div>
          ) : (
            <div
              className="kb-article-body"
              style={{
                minHeight: '380px',
                padding: '1.5rem',
                background: 'var(--color-surface-subtle)',
                borderRadius: 'var(--radius-md)',
                border: '1px solid var(--color-border)',
                lineHeight: 'var(--line-height-relaxed)',
                color: 'var(--color-text-primary)',
              }}
            >
              {contentMarkdown.trim() ? (
                <ReactMarkdown>{contentMarkdown}</ReactMarkdown>
              ) : (
                <p style={{ color: 'var(--color-text-muted)', fontStyle: 'italic', margin: 0 }}>
                  No content written yet. Switch back to Write mode to compose your guide.
                </p>
              )}
            </div>
          )}
        </div>
      </Card>
    </div>
  )
}
