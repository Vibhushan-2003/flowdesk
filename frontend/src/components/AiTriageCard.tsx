import { useEffect, useState } from 'react'
import {
  Sparkles,
  CheckCircle2,
  AlertCircle,
  ShieldCheck,
  RefreshCw,
} from 'lucide-react'

import {
  AiTriageApiError,
  applyAiTriageRecommendation,
  generateAiTriageRecommendation,
  getAiTriageRecommendation,
} from '../api/aiTriage'
import type { AiTriageRecommendationResponse } from '../types/aiTriage'
import type { TicketPriority, TicketStatus } from '../types/ticket'
import { Button } from './ui/Button'
import { Badge } from './ui/Badge'
import { PriorityBadge } from './ui/PriorityBadge'
import { Modal } from './ui/Modal'
import { formatDate } from '../utils/formatters'

interface AiTriageCardProps {
  accessToken: string | null
  ticketNumber: string
  currentPriority: TicketPriority
  ticketStatus: TicketStatus
  onApplied: () => Promise<void>
}

function formatConfidence(confidence: number) {
  return `${Math.round(confidence * 100)}%`
}

export function AiTriageCard({
  accessToken,
  ticketNumber,
  currentPriority,
  ticketStatus,
  onApplied,
}: AiTriageCardProps) {
  const [recommendation, setRecommendation] = useState<AiTriageRecommendationResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isGenerating, setIsGenerating] = useState(false)
  const [isApplying, setIsApplying] = useState(false)
  const [isConfirmingApply, setIsConfirmingApply] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadRecommendation() {
      if (!accessToken) {
        if (!cancelled) {
          setError('Your session is no longer valid')
          setIsLoading(false)
        }
        return
      }

      setIsLoading(true)
      setError(null)

      try {
        const response = await getAiTriageRecommendation(accessToken, ticketNumber)
        if (!cancelled) {
          setRecommendation(response)
        }
      } catch (caughtError) {
        if (cancelled) return
        setError(
          caughtError instanceof Error ? caughtError.message : 'Unable to load AI triage recommendation',
        )
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
  }, [accessToken, ticketNumber])

  async function handleGenerate() {
    if (!accessToken) {
      setError('Your session is no longer valid')
      return
    }

    setIsGenerating(true)
    setError(null)

    try {
      const response = await generateAiTriageRecommendation(accessToken, ticketNumber)
      setRecommendation(response)
    } catch (caughtError) {
      if (caughtError instanceof AiTriageApiError && caughtError.status === 409) {
        try {
          const existing = await getAiTriageRecommendation(accessToken, ticketNumber)
          if (existing) {
            setRecommendation(existing)
            return
          }
        } catch {
          // Fall through
        }
      }

      setError(
        caughtError instanceof Error ? caughtError.message : 'Unable to generate AI triage recommendation',
      )
    } finally {
      setIsGenerating(false)
    }
  }

  async function handleApply() {
    if (!accessToken || !recommendation) {
      setError('Your session is no longer valid')
      return
    }

    setIsApplying(true)
    setError(null)

    try {
      const response = await applyAiTriageRecommendation(accessToken, ticketNumber)
      setRecommendation((curr) =>
        curr
          ? {
              ...curr,
              applied: true,
              appliedByUserId: response.appliedByUserId,
              appliedAt: response.appliedAt,
            }
          : curr,
      )

      setIsConfirmingApply(false)
      await onApplied()
    } catch (caughtError) {
      setError(
        caughtError instanceof Error ? caughtError.message : 'Unable to apply AI recommendation',
      )
    } finally {
      setIsApplying(false)
    }
  }

  const canApply = ticketStatus === 'ASSIGNED'
  const priorityWouldChange =
    recommendation !== null && recommendation.suggestedPriority !== currentPriority

  return (
    <div className="horizon-ai-triage-panel" style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      {/* Header Info */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <Sparkles size={16} style={{ color: 'var(--color-primary)' }} />
          <span style={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)' }}>
            AI Priority Advisory
          </span>
        </div>

        <Badge variant="neutral" size="sm">
          Human-in-the-Loop
        </Badge>
      </div>

      {/* Error state */}
      {error && (
        <div className="horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ margin: 0, padding: '0.5rem 0.75rem' }}>
          <AlertCircle size={16} className="horizon-auth-alert-icon" />
          <span style={{ fontSize: 'var(--font-size-xs)' }}>{error}</span>
        </div>
      )}

      {/* Loading state */}
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: '1.5rem', color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-xs)' }}>
          Checking existing AI recommendation...
        </div>
      ) : !recommendation ? (
        /* Unanalyzed Ticket */
        <div style={{ padding: '1rem', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)', textAlign: 'center' }}>
          <p style={{ margin: '0 0 0.75rem', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
            No AI triage recommendation has been generated yet for this ticket.
          </p>
          <Button
            variant="primary"
            size="sm"
            onClick={() => void handleGenerate()}
            isLoading={isGenerating}
            leadingIcon={<Sparkles size={14} />}
          >
            Generate AI Triage
          </Button>
        </div>
      ) : (
        /* Has Recommendation */
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          {/* Priority comparison */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0.75rem', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>Suggested:</span>
              <PriorityBadge priority={recommendation.suggestedPriority} size="sm" />
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              <span style={{ fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-primary)' }}>
                {formatConfidence(recommendation.confidence)}
              </span>
              <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>confidence</span>
            </div>
          </div>

          {/* Reasoning */}
          <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-primary)', lineHeight: 'var(--line-height-relaxed)', background: 'var(--color-canvas)', padding: '0.75rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
            <p style={{ margin: '0 0 4px', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-secondary)' }}>
              Analysis & Reasoning:
            </p>
            <p style={{ margin: 0 }}>{recommendation.reason}</p>
          </div>

          {/* Applied status or Apply action */}
          {recommendation.applied ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: 'var(--font-size-xs)', color: 'var(--color-success)', background: 'var(--color-success-subtle)', padding: '0.5rem 0.75rem', borderRadius: 'var(--radius-md)' }}>
              <CheckCircle2 size={16} />
              <span>Applied to ticket{recommendation.appliedAt ? ` on ${formatDate(recommendation.appliedAt)}` : ''}</span>
            </div>
          ) : (
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '0.5rem', marginTop: '4px' }}>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => void handleGenerate()}
                isLoading={isGenerating}
                leadingIcon={<RefreshCw size={12} />}
              >
                Regenerate
              </Button>

              {canApply ? (
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => setIsConfirmingApply(true)}
                  disabled={!priorityWouldChange || isApplying}
                  leadingIcon={<ShieldCheck size={14} />}
                >
                  {priorityWouldChange ? 'Apply Recommendation' : 'Priority Already Matches'}
                </Button>
              ) : (
                <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
                  Can only apply while status is ASSIGNED
                </span>
              )}
            </div>
          )}
        </div>
      )}

      {/* Confirmation Modal */}
      <Modal
        isOpen={isConfirmingApply}
        onClose={() => setIsConfirmingApply(false)}
        title="Apply AI Priority Recommendation?"
        subtitle={`This will change the ticket priority from ${currentPriority} to ${recommendation?.suggestedPriority}. SLA deadlines may be recalculated based on the new priority.`}
        footer={
          <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
            <Button variant="secondary" size="sm" onClick={() => setIsConfirmingApply(false)}>
              Cancel
            </Button>
            <Button variant="primary" size="sm" onClick={() => void handleApply()} isLoading={isApplying}>
              Confirm and Apply
            </Button>
          </div>
        }
      >
        <div style={{ padding: '0.5rem 0' }}>
          <p style={{ fontSize: 'var(--font-size-sm)', margin: '0 0 0.5rem' }}>
            <strong>Reasoning:</strong> {recommendation?.reason}
          </p>
        </div>
      </Modal>
    </div>
  )
}
