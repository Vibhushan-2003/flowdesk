import {
  useEffect,
  useState,
} from 'react'

import {
  AiTriageApiError,
  generateAiTriageRecommendation,
  getAiTriageRecommendation,
} from '../api/aiTriage'

import type {
  AiTriageRecommendationResponse,
} from '../types/aiTriage'

import type {
  TicketPriority,
} from '../types/ticket'

import './AiTriageCard.css'

interface AiTriageCardProps {
  accessToken: string | null
  ticketNumber: string
  currentPriority: TicketPriority
}

function formatConfidence(
  confidence: number,
) {
  return `${Math.round(
    confidence * 100,
  )}%`
}

function formatDate(
  value: string,
) {
  return new Date(value).toLocaleString()
}

function getPriorityClass(
  priority: TicketPriority,
) {
  return `ai-triage-priority ai-triage-priority-${priority.toLowerCase()}`
}

export function AiTriageCard({
  accessToken,
  ticketNumber,
  currentPriority,
}: AiTriageCardProps) {
  const [
    recommendation,
    setRecommendation,
  ] =
    useState<AiTriageRecommendationResponse | null>(
      null,
    )

  const [
    isLoading,
    setIsLoading,
  ] =
    useState(true)

  const [
    isGenerating,
    setIsGenerating,
  ] =
    useState(false)

  const [
    error,
    setError,
  ] =
    useState<string | null>(
      null,
    )

  useEffect(() => {
    let cancelled = false

    async function loadRecommendation() {
      if (!accessToken) {
        if (!cancelled) {
          setError(
            'Your session is no longer valid',
          )

          setIsLoading(false)
        }

        return
      }

      setIsLoading(true)
      setError(null)

      try {
        const response =
          await getAiTriageRecommendation(
            accessToken,
            ticketNumber,
          )

        if (!cancelled) {
          setRecommendation(
            response,
          )
        }
      } catch (caughtError) {
        if (cancelled) {
          return
        }

        if (
          caughtError instanceof Error
        ) {
          setError(
            caughtError.message,
          )
        } else {
          setError(
            'Unable to load AI triage',
          )
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    void loadRecommendation()

    return () => {
      cancelled = true
    }
  }, [
    accessToken,
    ticketNumber,
  ])

  async function handleGenerate() {
    if (!accessToken) {
      setError(
        'Your session is no longer valid',
      )

      return
    }

    setIsGenerating(true)
    setError(null)

    try {
      const response =
        await generateAiTriageRecommendation(
          accessToken,
          ticketNumber,
        )

      setRecommendation(
        response,
      )
    } catch (caughtError) {
      /*
       * A 409 may happen if another request
       * generated the recommendation first.
       *
       * Instead of showing a confusing error,
       * retrieve the recommendation that now
       * exists.
       */
      if (
        caughtError instanceof AiTriageApiError &&
        caughtError.status === 409
      ) {
        try {
          const existing =
            await getAiTriageRecommendation(
              accessToken,
              ticketNumber,
            )

          if (existing) {
            setRecommendation(
              existing,
            )

            return
          }
        } catch {
          /*
           * Fall through to the normal
           * controlled error below.
           */
        }
      }

      if (
        caughtError instanceof Error
      ) {
        setError(
          caughtError.message,
        )
      } else {
        setError(
          'Unable to generate AI triage',
        )
      }
    } finally {
      setIsGenerating(false)
    }
  }

  return (
    <section
      className="ai-triage-card"
      aria-labelledby="ai-triage-heading"
    >
      <header className="ai-triage-header">
        <div>
          <div className="ai-triage-label-row">
            <span className="ai-triage-badge">
              AI advisory
            </span>

            <span className="ai-triage-advisory-label">
              Decision support only
            </span>
          </div>

          <h3 id="ai-triage-heading">
            AI-assisted triage
          </h3>

          <p>
            Get an AI recommendation based on the
            ticket type, title and description.
          </p>
        </div>
      </header>

      <div className="ai-triage-notice">
        <strong>
          Advisory only
        </strong>

        <p>
          FlowDesk will not automatically change
          this ticket&apos;s priority or SLA.
          The support engineer remains responsible
          for the final decision.
        </p>
      </div>

      {isLoading && (
        <div
          className="ai-triage-loading"
          aria-live="polite"
        >
          Checking for an existing AI
          recommendation...
        </div>
      )}

      {!isLoading &&
        !recommendation && (
          <div className="ai-triage-empty">
            <div>
              <strong>
                No recommendation generated yet
              </strong>

              <p>
                Generate a one-time AI triage
                recommendation for this ticket.
              </p>
            </div>

            <button
              type="button"
              className="ai-triage-generate-button"
              disabled={
                isGenerating
              }
              onClick={() =>
                void handleGenerate()
              }
            >
              {isGenerating
                ? 'Generating...'
                : 'Generate AI triage'}
            </button>
          </div>
        )}

      {recommendation && (
        <div className="ai-triage-result">
          <div className="ai-triage-result-grid">
            <div>
              <span>
                Current priority
              </span>

              <strong
                className={getPriorityClass(
                  currentPriority,
                )}
              >
                {currentPriority}
              </strong>
            </div>

            <div>
              <span>
                AI suggestion
              </span>

              <strong
                className={getPriorityClass(
                  recommendation
                    .suggestedPriority,
                )}
              >
                {
                  recommendation
                    .suggestedPriority
                }
              </strong>
            </div>

            <div>
              <span>
                Confidence
              </span>

              <strong className="ai-triage-confidence">
                {formatConfidence(
                  recommendation.confidence,
                )}
              </strong>
            </div>
          </div>

          <div className="ai-triage-reason">
            <span>
              Why AI suggested this
            </span>

            <p>
              {recommendation.reason}
            </p>
          </div>

          <div className="ai-triage-safety-message">
            <strong>
              No automatic change was made
            </strong>

            <p>
              Ticket priority remains{' '}
              <b>{currentPriority}</b>.
              This recommendation is stored for
              reference only.
            </p>
          </div>

          <footer className="ai-triage-meta">
            <span>
              {recommendation.provider}
            </span>

            <span aria-hidden="true">
              •
            </span>

            <span>
              {recommendation.model}
            </span>

            <span aria-hidden="true">
              •
            </span>

            <span>
              {formatDate(
                recommendation.createdAt,
              )}
            </span>
          </footer>
        </div>
      )}

      {error && (
        <div
          className="ai-triage-error"
          role="alert"
        >
          <strong>
            AI triage unavailable
          </strong>

          <p>
            {error}
          </p>

          {!recommendation && (
            <button
              type="button"
              disabled={
                isGenerating
              }
              onClick={() =>
                void handleGenerate()
              }
            >
              Try again
            </button>
          )}
        </div>
      )}
    </section>
  )
}