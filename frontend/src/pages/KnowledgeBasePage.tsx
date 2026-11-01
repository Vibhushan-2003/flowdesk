import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getArticles, getCategories, getManagementArticles } from '../api/kb'
import { useAuth } from '../auth/useAuth'
import type { ArticleStatus, KbArticleSummary, KbCategory, PageResponse } from '../types/kb'
import { formatDate } from '../utils/formatters'
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
    <main className="kb-container">
      {/* Top Navigation */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <button
          type="button"
          className="dashboard-secondary-button"
          onClick={() => navigate('/dashboard')}
        >
          ← Dashboard
        </button>

        {isStaff && (
          <button
            type="button"
            className="kb-search-btn"
            style={{ padding: '0.6rem 1.25rem', fontSize: '0.9rem' }}
            onClick={() => navigate('/support/kb/articles/new')}
          >
            + New Article
          </button>
        )}
      </div>

      {/* Hero Search */}
      <section className="kb-hero">
        <p className="eyebrow" style={{ color: '#2563eb', fontWeight: 600 }}>Self-Service Support</p>
        <h1>Knowledge Base</h1>
        <p className="kb-hero-subtitle">
          Find verified troubleshooting guides, standard operating procedures, and answers to common IT questions.
        </p>

        <form onSubmit={handleSearchSubmit} className="kb-search-box">
          <input
            type="text"
            className="kb-search-input"
            placeholder="Search articles by keywords (e.g. VPN, Wi-Fi, monitor, password)..."
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
          />
          <button type="submit" className="kb-search-btn">
            Search
          </button>
          {activeSearch && (
            <button
              type="button"
              className="dashboard-secondary-button"
              onClick={() => {
                setSearchInput('')
                setActiveSearch('')
                setPage(0)
              }}
            >
              Clear
            </button>
          )}
        </form>
      </section>

      {/* Staff Management Tab Bar */}
      {isStaff && (
        <div className="kb-management-bar">
          <div className="kb-tabs">
            <button
              type="button"
              className={`kb-tab-btn ${activeTab === 'published' ? 'active' : ''}`}
              onClick={() => {
                setActiveTab('published')
                setPage(0)
              }}
            >
              Published Library
            </button>
            <button
              type="button"
              className={`kb-tab-btn ${activeTab === 'management' ? 'active' : ''}`}
              onClick={() => {
                setActiveTab('management')
                setPage(0)
              }}
            >
              Staff Management (All Statuses)
            </button>
          </div>

          {activeTab === 'management' && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span style={{ fontSize: '0.85rem', color: '#64748b' }}>Status:</span>
              <select
                className="kb-editor-select"
                style={{ padding: '0.4rem 0.75rem', fontSize: '0.85rem' }}
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
          )}
        </div>
      )}

      {/* Category Pills */}
      <div className="kb-categories-nav">
        <button
          type="button"
          className={`kb-category-pill ${selectedCategoryId === '' ? 'active' : ''}`}
          onClick={() => handleCategorySelect('')}
        >
          All Categories
        </button>
        {categories.map((cat) => (
          <button
            key={cat.id}
            type="button"
            className={`kb-category-pill ${selectedCategoryId === cat.id ? 'active' : ''}`}
            onClick={() => handleCategorySelect(cat.id)}
          >
            {cat.name} {cat.articleCount > 0 ? `(${cat.articleCount})` : ''}
          </button>
        ))}
      </div>

      {/* Content Area */}
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: '3rem 1rem', color: '#64748b' }}>
          Loading knowledge base articles...
        </div>
      ) : error ? (
        <div className="error-banner" style={{ margin: '2rem 0' }}>
          {error}
        </div>
      ) : !articlePage || articlePage.content.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '4rem 1rem', background: '#f8fafc', borderRadius: '8px' }}>
          <h3 style={{ color: '#334155', marginBottom: '0.5rem' }}>No articles found</h3>
          <p style={{ color: '#64748b', maxWidth: '450px', margin: '0 auto 1.5rem' }}>
            {activeSearch
              ? `No articles matched your search query "${activeSearch}". Try broader keywords.`
              : 'There are currently no articles published in this category.'}
          </p>
          {isStaff && (
            <button
              type="button"
              className="kb-search-btn"
              onClick={() => navigate('/support/kb/articles/new')}
            >
              Write First Article
            </button>
          )}
        </div>
      ) : (
        <>
          <div className="kb-articles-grid">
            {articlePage.content.map((art) => (
              <div key={art.id} className="kb-article-card">
                <div className="kb-card-header">
                  <span className="kb-category-tag">{art.categoryName}</span>
                  {activeTab === 'management' && (
                    <span className={`kb-status-badge ${art.status.toLowerCase()}`}>
                      {art.status}
                    </span>
                  )}
                </div>

                <a
                  href={`/kb/articles/${encodeURIComponent(art.slug)}`}
                  style={{ textDecoration: 'none', color: 'inherit' }}
                  onClick={(e) => {
                    e.preventDefault()
                    navigate(`/kb/articles/${encodeURIComponent(art.slug)}`)
                  }}
                >
                  <h2>{art.title}</h2>
                </a>

                <p className="kb-article-summary">{art.summary}</p>

                <div className="kb-card-footer">
                  <span>By {art.authorName}</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                    <span>👁 {art.viewCount} views</span>
                    {art.publishedAt && <span>{formatDate(art.publishedAt)}</span>}
                    {isStaff && (
                      <button
                        type="button"
                        style={{
                          background: 'none',
                          border: 'none',
                          color: '#2563eb',
                          cursor: 'pointer',
                          fontWeight: 600,
                          fontSize: '0.8rem',
                          padding: 0,
                        }}
                        onClick={() => navigate(`/support/kb/articles/${art.id}/edit`)}
                      >
                        Edit ↗
                      </button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>

          {/* Pagination */}
          {articlePage.totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '1rem', marginTop: '2rem' }}>
              <button
                type="button"
                className="dashboard-secondary-button"
                disabled={articlePage.first}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Previous
              </button>
              <span style={{ fontSize: '0.9rem', color: '#64748b' }}>
                Page {articlePage.page + 1} of {articlePage.totalPages} ({articlePage.totalElements} total)
              </span>
              <button
                type="button"
                className="dashboard-secondary-button"
                disabled={articlePage.last}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </main>
  )
}
