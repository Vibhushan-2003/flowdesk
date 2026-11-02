import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getDeflectionSuggestions, recordArticleDeflection } from '../../api/kb'
import { useAuth } from '../../auth/useAuth'
import type { KbArticleDeflection } from '../../types/kb'
import type { TicketType } from '../../types/ticket'

interface TicketDeflectionCardProps {
  title: string
  ticketType?: TicketType
  description?: string
  onDeflected?: () => void
  onSolved?: () => void
}

export function TicketDeflectionCard({
  title,
  ticketType = 'INCIDENT',
  description = '',
  onDeflected,
  onSolved,
}: TicketDeflectionCardProps) {
  const { accessToken } = useAuth()
  const navigate = useNavigate()
  const [suggestions, setSuggestions] = useState<KbArticleDeflection[]>([])
  const [solved, setSolved] = useState(false)
  const [isSubmittingDeflection, setIsSubmittingDeflection] = useState(false)

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
      <div
        className="kb-deflection-card"
        style={{ backgroundColor: 'var(--color-success-subtle)', borderColor: 'var(--color-success-border)' }}
        role="region"
        aria-label="Issue resolved confirmation"
      >
        <div className="kb-deflection-header" style={{ color: 'var(--color-success-text)' }}>
          <span>🎉 Great! We are glad a Knowledge Base guide solved your issue.</span>
        </div>
        <p style={{ margin: '0.25rem 0 0.5rem 0', fontSize: '0.875rem', color: 'var(--color-success-text)' }}>
          No ticket was submitted. You can safely return to the dashboard.
        </p>
        <button
          type="button"
          className="kb-deflection-solved-btn"
          style={{ backgroundColor: 'var(--color-success)', color: '#ffffff', marginTop: '0.5rem' }}
          onClick={() => {
            sessionStorage.removeItem('flowdesk_ticket_draft')
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
    <aside className="kb-deflection-card" aria-label="Suggested Knowledge Base Solutions">
      <div className="kb-deflection-header">
        <span>💡 Suggested Solutions Before Submitting</span>
      </div>
      <p className="kb-deflection-subtitle">
        We found verified knowledge base articles matching your issue. One of these might solve your problem immediately:
      </p>

      <div className="kb-deflection-list">
        {suggestions.map((article) => (
          <div key={article.id} className="kb-deflection-item">
            <div className="kb-deflection-info">
              <div>
                <span className="kb-category-tag" style={{ display: 'inline-block', marginBottom: '0.35rem' }}>
                  {article.categoryName}
                </span>
              </div>
              <h4>{article.title}</h4>
              <p>{article.summary}</p>
            </div>
            <div className="kb-deflection-actions">
              <button
                type="button"
                className="kb-deflection-view-btn"
                onClick={() => {
                  navigate(`/kb/articles/${encodeURIComponent(article.slug)}`, {
                    state: {
                      fromTicket: true,
                      ticketDraft: {
                        type: ticketType,
                        title,
                        description,
                      },
                    },
                  })
                }}
              >
                Read Guide
              </button>
              <button
                type="button"
                className="kb-deflection-solved-btn"
                disabled={isSubmittingDeflection}
                onClick={() => {
                  if (isSubmittingDeflection) return
                  setIsSubmittingDeflection(true)
                  sessionStorage.removeItem('flowdesk_ticket_draft')
                  if (accessToken && article.id) {
                    void recordArticleDeflection(accessToken, article.id).catch(() => {})
                  }
                  setSolved(true)
                  if (onSolved) {
                    onSolved()
                  }
                }}
              >
                {isSubmittingDeflection ? 'Resolving...' : 'Solved My Issue'}
              </button>
            </div>
          </div>
        ))}
      </div>
    </aside>
  )
}
