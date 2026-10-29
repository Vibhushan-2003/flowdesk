import { useEffect, useState } from 'react'
import { getDeflectionSuggestions } from '../../api/kb'
import { useAuth } from '../../auth/useAuth'
import type { KbArticleDeflection } from '../../types/kb'

interface TicketDeflectionCardProps {
  title: string
  onDeflected?: () => void
}

export function TicketDeflectionCard({ title, onDeflected }: TicketDeflectionCardProps) {
  const { accessToken } = useAuth()
  const [suggestions, setSuggestions] = useState<KbArticleDeflection[]>([])
  const [solved, setSolved] = useState(false)

  useEffect(() => {
    let cancelled = false
    const query = title.trim()

    const timer = setTimeout(async () => {
      if (!accessToken || query.length < 3) {
        if (!cancelled) {
          setSuggestions([])
        }
        return
      }

      try {
        const results = await getDeflectionSuggestions(accessToken, query)
        if (!cancelled) {
          setSuggestions(results)
        }
      } catch {
        if (!cancelled) {
          setSuggestions([])
        }
      }
    }, query.length < 3 ? 0 : 350)

    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [accessToken, title])

  if (solved) {
    return (
      <div className="kb-deflection-card" style={{ backgroundColor: '#ecfdf5', borderColor: '#a7f3d0' }}>
        <div className="kb-deflection-header" style={{ color: '#065f46' }}>
          <span>🎉 Great! We are glad a Knowledge Base guide solved your issue.</span>
        </div>
        <p style={{ margin: '0.25rem 0 0.5rem 0', fontSize: '0.85rem', color: '#047857' }}>
          No ticket was submitted. You can safely return to the dashboard.
        </p>
        <button
          type="button"
          className="kb-deflection-view-btn"
          style={{ backgroundColor: '#059669', marginTop: '0.5rem' }}
          onClick={() => {
            if (onDeflected) {
              onDeflected()
            }
          }}
        >
          Return to Dashboard
        </button>
      </div>
    )
  }

  if (suggestions.length === 0) {
    return null
  }

  return (
    <div className="kb-deflection-card">
      <div className="kb-deflection-header">
        <span>💡 Suggested Solutions Before Submitting</span>
      </div>
      <p style={{ margin: 0, fontSize: '0.85rem', color: '#1e40af' }}>
        We found verified knowledge base articles matching your issue. One of these might solve your problem immediately:
      </p>

      <div className="kb-deflection-list">
        {suggestions.map((article) => (
          <div key={article.id} className="kb-deflection-item">
            <div className="kb-deflection-info">
              <span className="kb-category-tag" style={{ marginBottom: '0.25rem', display: 'inline-block' }}>
                {article.categoryName}
              </span>
              <h4>{article.title}</h4>
              <p>{article.summary}</p>
            </div>
            <div className="kb-deflection-actions">
              <a
                href={`/kb/articles/${encodeURIComponent(article.slug)}`}
                target="_blank"
                rel="noopener noreferrer"
                className="kb-deflection-view-btn"
              >
                Read Guide ↗
              </a>
              <button
                type="button"
                className="kb-deflection-solved-btn"
                onClick={() => setSolved(true)}
              >
                Solved My Issue
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
