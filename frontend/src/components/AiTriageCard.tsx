import {
  useEffect,
  useState,
} from 'react'

import {
  AiTriageApiError,
  applyAiTriageRecommendation,
  generateAiTriageRecommendation,
  getAiTriageRecommendation,
} from '../api/aiTriage'

import type {
  AiTriageRecommendationResponse,
} from '../types/aiTriage'

import type {
  TicketPriority,
  TicketStatus,
} from '../types/ticket'

import './AiTriageCard.css'

interface AiTriageCardProps {
  accessToken: string | null
  ticketNumber: string
  currentPriority: TicketPriority
  ticketStatus: TicketStatus
  onApplied: () => Promise<void>
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
  ticketStatus,
  onApplied,
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
    isApplying,
    setIsApplying,
  ] =
    useState(false)

  const [
    isConfirmingApply,
    setIsConfirmingApply,
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
           * Fall through to the controlled
           * error handling below.
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

  async function handleApply() {
    if (
      !accessToken ||
      !recommendation
    ) {
      setError(
        'Your session is no longer valid',
      )

      return
    }

    setIsApplying(true)
    setError(null)

    try {
      const response =
        await applyAiTriageRecommendation(
          accessToken,
          ticketNumber,
        )

      /*
       * Immediately update the recommendation
       * state so the Apply action disappears even
       * before the parent ticket finishes reloading.
       */
      setRecommendation(
        (currentRecommendation) => {
          if (!currentRecommendation) {
            return currentRecommendation
          }

          return {
            ...currentRecommendation,
            applied: true,
            appliedByUserId:
              response.appliedByUserId,
            appliedAt:
              response.appliedAt,
          }
        },
      )

      setIsConfirmingApply(false)

      try {
        await onApplied()
      } catch {
        setError(
          'The AI recommendation was applied, but the ticket view could not refresh automatically. Reload the page to see the latest priority and SLA.',
        )
      }
    } catch (caughtError) {
      if (
        caughtError instanceof Error
      ) {
        setError(
          caughtError.message,
        )
      } else {
        setError(
          'Unable to apply the AI recommendation',
        )
      }
    } finally {
      setIsApplying(false)
    }
  }

  const canApply =
    ticketStatus === 'ASSIGNED'

  const priorityWouldChange =
    recommendation !== null &&
    recommendation.suggestedPriority !==
      currentPriority

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
              Human decision required
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
          Human-in-the-loop
        </strong>

        <p>
          FlowDesk never applies the AI
          recommendation automatically. A support
          engineer must explicitly approve it
          before work starts.
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

          {recommendation.applied ? (
            <div className="ai-triage-applied-message">
              <strong>
                Recommendation applied by a
                support engineer
              </strong>

              <p>
                The human decision was recorded
                {recommendation.appliedAt
                  ? ` on ${formatDate(
                      recommendation.appliedAt,
                    )}`
                  : ''}
                . The ticket priority is now{' '}
                <b>{currentPriority}</b>.
              </p>
            </div>
          ) : (
            <>
              <div className="ai-triage-safety-message">
                <strong>
                  No automatic change was made
                </strong>

                <p>
                  Ticket priority is currently{' '}
                  <b>{currentPriority}</b>.
                  Applying this recommendation
                  requires your explicit
                  confirmation.
                </p>
              </div>

              {!canApply && (
                <div className="ai-triage-locked-message">
                  <strong>
                    Recommendation can no longer
                    be applied
                  </strong>

                  <p>
                    Priority can only be changed
                    while the ticket is assigned
                    and before support work starts.
                  </p>
                </div>
              )}

              {canApply &&
                !isConfirmingApply && (
                  <div className="ai-triage-apply-row">
                    <div>
                      <strong>
                        Ready for your decision?
                      </strong>

                      <p>
                        Review the recommendation
                        before applying it.
                      </p>
                    </div>

                    <button
                      type="button"
                      className="ai-triage-apply-button"
                      disabled={isApplying}
                      onClick={() => {
                        setError(null)
                        setIsConfirmingApply(
                          true,
                        )
                      }}
                    >
                      Apply AI suggestion
                    </button>
                  </div>
                )}

              {canApply &&
                isConfirmingApply && (
                  <div className="ai-triage-confirmation">
                    <strong>
                      Confirm human decision
                    </strong>

                    {priorityWouldChange ? (
                      <p>
                        Change priority from{' '}
                        <b>
                          {currentPriority}
                        </b>{' '}
                        to{' '}
                        <b>
                          {
                            recommendation
                              .suggestedPriority
                          }
                        </b>
                        ? SLA deadlines will be
                        recalculated from the
                        original SLA start time.
                      </p>
                    ) : (
                      <p>
                        The AI suggestion already
                        matches the current{' '}
                        <b>
                          {currentPriority}
                        </b>{' '}
                        priority. Accepting it will
                        record your decision
                        without recalculating the
                        SLA.
                      </p>
                    )}

                    <div className="ai-triage-confirmation-actions">
                      <button
                        type="button"
                        className="ai-triage-cancel-button"
                        disabled={isApplying}
                        onClick={() =>
                          setIsConfirmingApply(
                            false,
                          )
                        }
                      >
                        Cancel
                      </button>

                      <button
                        type="button"
                        className="ai-triage-confirm-button"
                        disabled={isApplying}
                        onClick={() =>
                          void handleApply()
                        }
                      >
                        {isApplying
                          ? 'Applying...'
                          : 'Confirm and apply'}
                      </button>
                    </div>
                  </div>
                )}
            </>
          )}

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
            AI triage notice
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