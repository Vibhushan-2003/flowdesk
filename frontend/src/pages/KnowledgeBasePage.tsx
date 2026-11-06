import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Search,
  Plus,
  Eye,
  ExternalLink,
  RotateCcw,
  BookOpen,
  FileText,
} from 'lucide-react'

import { getArticles, getCategories, getManagementArticles } from '../api/kb'
import { useAuth } from '../auth/useAuth'
import type { ArticleStatus, KbArticleSummary, KbCategory, PageResponse } from '../types/kb'
import { formatDate } from '../utils/formatters'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Badge } from '../components/ui/Badge'
import { Pagination } from '../components/ui/Pagination'
import { EmptyState } from '../components/ui/EmptyState'
import '../styles/kb.css'

export function KnowledgeBasePage() {
  const { accessToken, user } = useAuth()
  const navigate = useNavigate()

  const isStaff = user?.roles.some(
    (role) =>
      role === 'SUPPORT_ENGINEER' ||
      role === 'TEAM_LEAD' ||
      role === 'ADMIN',
  ) ?? false

  const [activeTab, setActiveTab] = useState<'published' | 'management'>('published')
  const [categories, setCategories] = useState<KbCategory[]>([])
  const [selectedCategoryId, setSelectedCategoryId] = useState<string>('')
  const [managementStatus, setManagementStatus] = useState<ArticleStatus | ''>('')
  const [searchInput, setSearchInput] = useState('')
  const [activeSearch, setActiveSearch] = useState('')

  const [page, setPage] = useState(0)
  const [articlePage, setArticlePage] = useState<PageResponse<KbArticleSummary> | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Load Categories
  useEffect(() => {
    let cancelled = false

    async function loadCategories() {
      if (!accessToken) return
      try {
        const data = await getCategories(accessToken)
        if (!cancelled) {
          setCategories(data)
        }
      } catch (err) {
        if (!cancelled) {
          console.error('Failed to load categories', err)
        }
      }
    }

    void loadCategories()
    return () => {
      cancelled = true
    }
  }, [accessToken])

  // Load Articles
  useEffect(() => {
    let cancelled = false

    async function loadArticles() {
      if (!accessToken) return
      setIsLoading(true)
      setError(null)

      try {
        let result: PageResponse<KbArticleSummary>
        if (activeTab === 'management' && isStaff) {
          result = await getManagementArticles(
            accessToken,
            managementStatus,
            selectedCategoryId || undefined,
            activeSearch || undefined,
            page,
            12,
          )
        } else {
          result = await getArticles(
            accessToken,
            selectedCategoryId || undefined,
            activeSearch || undefined,
            page,
            12,
          )
        }

        if (!cancelled) {
          setArticlePage(result)
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load knowledge base articles')
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    void loadArticles()
    return () => {
      cancelled = true
    }
  }, [accessToken, activeTab, isStaff, selectedCategoryId, managementStatus, activeSearch, page])

  function handleSearchSubmit(e: React.FormEvent) {
    e.preventDefault()
    setPage(0)
    setActiveSearch(searchInput.trim())
  }

  function handleCategorySelect(catId: string) {
    setSelectedCategoryId(catId)
    setPage(0)
  }

  return (
    <div className="kb-container horizon-kb-workspace">
      {/* Page Header */}
      <PageHeader
        title="Knowledge Base"
        subtitle="Find verified troubleshooting guides, standard operating procedures, and answers to common IT questions."
        actions={
          isStaff ? (
            <Button
              variant="primary"
              size="sm"
              onClick={() => navigate('/support/kb/articles/new')}
              leadingIcon={<Plus size={16} />}
            >
              New Article
            </Button>
          ) : undefined
        }
      />

      {/* Hero Search Section & Unified Discovery Console */}
      <section className="kb-hero horizon-card" style={{ marginBottom: '1.75rem', padding: '2.5rem 1.5rem 2rem', textAlign: 'center', background: 'var(--color-surface)' }}>
        <p className="eyebrow" style={{ color: 'var(--color-primary)', fontWeight: 'var(--font-weight-semibold)', margin: '0 0 0.5rem', fontSize: 'var(--font-size-xs)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
          Self-Service Support
        </p>
        <h1 style={{ fontSize: 'var(--font-size-3xl)', fontWeight: 'var(--font-weight-bold)', margin: '0 0 0.75rem', color: 'var(--color-text-primary)' }}>
          How can we help you today?
        </h1>
        <p className="kb-hero-subtitle" style={{ fontSize: 'var(--font-size-md)', color: 'var(--color-text-secondary)', maxWidth: '600px', margin: '0 auto 1.75rem' }}>
          Search across our documentation for setup instructions, fixes, and FAQs.
        </p>

        <form onSubmit={handleSearchSubmit} className="kb-search-box" style={{ maxWidth: '640px', margin: '0 auto', display: 'flex', gap: '0.5rem' }}>
          <div className="horizon-input-wrapper" style={{ flex: 1 }}>
            <Search size={18} className="horizon-input-icon horizon-input-icon--leading" />
            <input
              type="text"
              className="horizon-input has-leading-icon"
              placeholder="Search articles by keywords (e.g. VPN, Wi-Fi, monitor, password)..."
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
            />
          </div>

          <button type="submit" className="kb-search-btn horizon-btn horizon-btn--primary horizon-btn--md">
            Search
          </button>

          {activeSearch && (
            <button
              type="button"
              className="horizon-btn horizon-btn--secondary horizon-btn--md"
              onClick={() => {
                setSearchInput('')
                setActiveSearch('')
                setPage(0)
              }}
            >
              <RotateCcw size={14} />
              <span>Clear</span>
            </button>
          )}
        </form>

        {/* Integrated Category Filter Pills */}
        <div className="kb-categories-nav" style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'center', gap: '8px', marginTop: '1.5rem' }}>
          <button
            type="button"
            className={`kb-category-pill horizon-badge ${selectedCategoryId === '' ? 'horizon-badge--primary' : 'horizon-badge--neutral'}`}
            style={{ cursor: 'pointer', padding: '6px 14px', height: 'auto', fontSize: 'var(--font-size-xs)' }}
            onClick={() => handleCategorySelect('')}
          >
            All Categories
          </button>
          {categories.map((cat) => (
            <button
              key={cat.id}
              type="button"
              className={`kb-category-pill horizon-badge ${selectedCategoryId === cat.id ? 'horizon-badge--primary' : 'horizon-badge--neutral'}`}
              style={{ cursor: 'pointer', padding: '6px 14px', height: 'auto', fontSize: 'var(--font-size-xs)' }}
              onClick={() => handleCategorySelect(cat.id)}
            >
              {cat.name} {cat.articleCount > 0 ? `(${cat.articleCount})` : ''}
            </button>
          ))}
        </div>
      </section>

      {/* Staff Management Tab Bar */}
      {isStaff && (
        <div className="kb-management-bar horizon-card" style={{ padding: '0.75rem 1.25rem', marginBottom: '1.5rem', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '1rem', flexWrap: 'wrap' }}>
          <div className="horizon-view-tabs" role="tablist" aria-label="Knowledge Base views" style={{ margin: 0 }}>
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'published'}
              className={`horizon-view-tab ${activeTab === 'published' ? 'is-active' : ''}`}
              onClick={() => {
                setActiveTab('published')
                setPage(0)
              }}
            >
              <BookOpen size={15} />
              <span>Published Articles</span>
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === 'management'}
              className={`horizon-view-tab ${activeTab === 'management' ? 'is-active' : ''}`}
              onClick={() => {
                setActiveTab('management')
                setPage(0)
              }}
            >
              <FileText size={15} />
              <span>Staff Management</span>
            </button>
          </div>

          {activeTab === 'management' && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', fontWeight: 'var(--font-weight-medium)' }}>Status:</span>
              <div className="horizon-select-wrapper" style={{ width: 'auto' }}>
                <select
                  className="kb-editor-select horizon-select"
                  style={{ height: '36px', padding: '0 2rem 0 0.75rem', fontSize: 'var(--font-size-xs)', width: 'auto' }}
                  value={managementStatus}
                  onChange={(e) => {
                    setManagementStatus(e.target.value as ArticleStatus | '')
                    setPage(0)
                  }}
                >
                  <option value="">All Statuses</option>
                  <option value="DRAFT">Drafts</option>
                  <option value="PUBLISHED">Published</option>
                  <option value="ARCHIVED">Archived</option>
                </select>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Content Area */}
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: '4rem 1rem', color: 'var(--color-text-secondary)' }}>
          <p>Loading knowledge base articles...</p>
        </div>
      ) : error ? (
        <div className="error-banner horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ margin: '2rem 0' }}>
          <span>{error}</span>
        </div>
      ) : !articlePage || articlePage.content.length === 0 ? (
        <EmptyState
          title="No articles found"
          description={
            activeSearch
              ? `No articles matched your search query "${activeSearch}". Try broader keywords.`
              : 'There are currently no articles published in this category.'
          }
          action={
            isStaff ? (
              <Button
                variant="primary"
                size="md"
                onClick={() => navigate('/support/kb/articles/new')}
                leadingIcon={<Plus size={16} />}
              >
                Write First Article
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          {/* Article Cards Grid */}
          <div className="kb-articles-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: '1.25rem', marginBottom: '2rem' }}>
            {articlePage.content.map((art) => (
              <article
                key={art.id}
                className="kb-article-card horizon-card"
                style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between', padding: '1.25rem', cursor: 'pointer', transition: 'all var(--duration-fast) var(--ease-default)' }}
                onClick={() => navigate(`/kb/articles/${encodeURIComponent(art.slug)}`)}
              >
                <div>
                  <div className="kb-card-header" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
                    <Badge variant="primary" size="sm" className="kb-category-tag">
                      {art.categoryName}
                    </Badge>
                    {activeTab === 'management' && (
                      <Badge
                        variant={art.status === 'PUBLISHED' ? 'success' : art.status === 'DRAFT' ? 'warning' : 'neutral'}
                        size="sm"
                        className={`kb-status-badge ${art.status.toLowerCase()}`}
                      >
                        {art.status}
                      </Badge>
                    )}
                  </div>

                  <h2 style={{ fontSize: 'var(--font-size-md)', fontWeight: 'var(--font-weight-bold)', color: 'var(--color-text-primary)', margin: '0 0 0.5rem', lineHeight: 'var(--line-height-tight)' }}>
                    {art.title}
                  </h2>

                  <p className="kb-article-summary" style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', lineHeight: 'var(--line-height-base)', margin: '0 0 1rem', display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                    {art.summary}
                  </p>
                </div>

                <div className="kb-card-footer" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', paddingTop: '0.75rem', borderTop: '1px solid var(--color-divider)', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
                  <span>By {art.authorName?.trim() || 'IT Operations'}</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                      <Eye size={12} />
                      {art.viewCount}
                    </span>
                    {art.publishedAt && <span>{formatDate(art.publishedAt)}</span>}
                    {isStaff && (
                      <button
                        type="button"
                        style={{
                          background: 'none',
                          border: 'none',
                          color: 'var(--color-primary)',
                          cursor: 'pointer',
                          fontWeight: 'var(--font-weight-semibold)',
                          fontSize: 'var(--font-size-xs)',
                          padding: 0,
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '2px',
                        }}
                        onClick={(e) => {
                          e.stopPropagation()
                          navigate(`/support/kb/articles/${art.id}/edit`)
                        }}
                      >
                        <span>Edit</span>
                        <ExternalLink size={11} />
                      </button>
                    )}
                  </div>
                </div>
              </article>
            ))}
          </div>

          {/* Standard Pagination Component */}
          <Pagination
            page={articlePage.page}
            totalPages={articlePage.totalPages}
            totalElements={articlePage.totalElements}
            isFirst={articlePage.first}
            isLast={articlePage.last}
            onPageChange={(p) => setPage(p)}
            label="Knowledge Base pagination"
            entityName="articles"
          />
        </>
      )}
    </div>
  )
}
