import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  CheckSquare,
  AlertCircle,
  CheckCircle2,
  XCircle,
  ShieldAlert,
  Clock,
  User,
  RefreshCw,
  X,
} from 'lucide-react'

import {
  approveApproval,
  getPendingApprovals,
  rejectApproval,
} from '../api/approvals'
import { useAuth } from '../auth/useAuth'
import type { Approval, ApprovalsPage } from '../types/approval'
import { formatDate, formatLabel } from '../utils/formatters'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { PriorityBadge } from '../components/ui/PriorityBadge'
import { Pagination } from '../components/ui/Pagination'
import { EmptyState } from '../components/ui/EmptyState'
import { Modal } from '../components/ui/Modal'

const PAGE_SIZE = 10

export function ApprovalInboxPage() {
  const { accessToken, user } = useAuth()
  const navigate = useNavigate()

  const [page, setPage] = useState(0)
  const [approvalPage, setApprovalPage] = useState<ApprovalsPage | null>(null)
  const [refreshVersion, setRefreshVersion] = useState(0)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  // Decision state
  const [activeApproval, setActiveApproval] = useState<Approval | null>(null)
  const [decisionType, setDecisionType] = useState<'approve' | 'reject' | null>(null)
  const [decisionNote, setDecisionNote] = useState('')
  const [decisionError, setDecisionError] = useState<string | null>(null)
  const [isDeciding, setIsDeciding] = useState(false)

  useEffect(() => {
    let cancelled = false

    async function loadApprovals() {
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
        const response = await getPendingApprovals(accessToken, page, PAGE_SIZE)
        if (!cancelled) {
          setApprovalPage(response)
        }
      } catch (caughtError) {
        if (cancelled) return
        setError(
          caughtError instanceof Error
            ? caughtError.message
            : 'Unable to load pending approvals.',
        )
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    void loadApprovals()

    return () => {
      cancelled = true
    }
  }, [accessToken, page, refreshVersion])

  function handleOpenDecisionModal(approval: Approval, type: 'approve' | 'reject') {
    setActiveApproval(approval)
    setDecisionType(type)
    setDecisionNote('')
    setDecisionError(null)
  }

  function handleCloseModal() {
    if (isDeciding) return
    setActiveApproval(null)
    setDecisionType(null)
    setDecisionNote('')
    setDecisionError(null)
  }

  async function handleConfirmDecision() {
    if (!accessToken || !activeApproval || !decisionType) return

    if (decisionType === 'reject' && !decisionNote.trim()) {
      setDecisionError('Rejection reason is required.')
      return
    }

    setIsDeciding(true)
    setDecisionError(null)

    try {
      if (decisionType === 'approve') {
        await approveApproval(accessToken, activeApproval.id, decisionNote)
        setSuccessMessage(`Ticket ${activeApproval.ticketNumber} approved successfully. It has now entered the support queue.`)
      } else {
        await rejectApproval(accessToken, activeApproval.id, decisionNote.trim())
        setSuccessMessage(`Ticket ${activeApproval.ticketNumber} rejected. The requester has been notified.`)
      }

      handleCloseModal()

      // Adjust page if needed or refresh
      if (approvalPage && approvalPage.content.length === 1 && page > 0) {
        setPage((p) => Math.max(0, p - 1))
      } else {
        setRefreshVersion((v) => v + 1)
      }
    } catch (caughtError) {
      setDecisionError(
        caughtError instanceof Error
          ? caughtError.message
          : `Unable to ${decisionType} this request. Please try again.`,
      )
    } finally {
      setIsDeciding(false)
    }
  }

  return (
    <div className="approvals-page horizon-page-container">
      {/* Page Header */}
      <PageHeader
        title="Approval Inbox"
        subtitle="Review and approve requests from team members."
        actions={
          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setRefreshVersion((v) => v + 1)}
              leadingIcon={<RefreshCw size={14} className={isLoading ? 'horizon-spin' : ''} />}
              disabled={isLoading}
            >
              Refresh
            </Button>
          </div>
        }
      />

      {/* Global Alerts */}
      {error && (
        <div className="horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ marginBottom: '1.5rem' }}>
          <AlertCircle size={18} className="horizon-auth-alert-icon" />
          <span>{error}</span>
        </div>
      )}

      {successMessage && (
        <div className="horizon-auth-alert horizon-auth-alert--success" role="status" style={{ marginBottom: '1.5rem' }}>
          <CheckCircle2 size={18} className="horizon-auth-alert-icon" />
          <span>{successMessage}</span>
        </div>
      )}

      {/* Loading state */}
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: '4rem 1rem', color: 'var(--color-text-secondary)' }}>
          <p>Loading pending approvals...</p>
        </div>
      ) : !approvalPage || approvalPage.content.length === 0 ? (
        <EmptyState
          icon={<CheckSquare size={36} />}
          title="All caught up!"
          description="There are currently no service requests pending management approval."
          action={
            <Button variant="secondary" size="sm" onClick={() => navigate('/dashboard')}>
              Return to Dashboard
            </Button>
          }
        />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {/* Header count indicator */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', paddingBottom: '0.5rem' }}>
            <span>
              <strong>{approvalPage.totalElements}</strong> {approvalPage.totalElements === 1 ? 'request' : 'requests'} awaiting decision
            </span>
            <span style={{ fontSize: 'var(--font-size-xs)' }}>
              Requires Team Lead or Admin approval
            </span>
          </div>

          {/* List of Approval Cards */}
          {approvalPage.content.map((approval) => {
            const isSelfRequest = user?.userId === approval.requestedByUserId

            return (
              <Card key={approval.id} padded className="horizon-approval-card">
                {/* Header row */}
                <div className="horizon-approval-header">
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
                    <span
                      style={{
                        fontFamily: 'var(--font-family-mono)',
                        fontWeight: 'var(--font-weight-bold)',
                        color: 'var(--color-primary)',
                        fontSize: 'var(--font-size-base)',
                      }}
                    >
                      {approval.ticketNumber}
                    </span>
                    <PriorityBadge priority={approval.priority} size="sm" />
                    <Badge variant="neutral" size="sm">
                      {formatLabel(approval.ticketType)}
                    </Badge>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                      <Clock size={12} />
                      Requested {formatDate(approval.requestedAt)}
                    </span>
                  </div>
                </div>

                {/* Title & Description */}
                <h3 style={{ margin: '0 0 0.5rem', fontSize: 'var(--font-size-lg)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)' }}>
                  {approval.ticketTitle}
                </h3>

                <p style={{ margin: '0 0 1rem', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', lineHeight: 'var(--line-height-relaxed)', whiteSpace: 'pre-wrap' }}>
                  {approval.ticketDescription}
                </p>

                {/* Requester Info & Self-Approval Notice */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem', padding: '0.75rem 1rem', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
                    <User size={14} />
                    <span>Requester: <strong style={{ color: 'var(--color-text-primary)' }}>{approval.requestedByEmail}</strong></span>
                  </div>

                  {isSelfRequest && (
                    <Badge variant="warning" size="sm" dot>
                      <ShieldAlert size={12} style={{ marginRight: '4px' }} />
                      You cannot approve your own request
                    </Badge>
                  )}
                </div>

                {/* Actions */}
                <div className="horizon-approval-actions">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={isSelfRequest}
                    onClick={() => handleOpenDecisionModal(approval, 'reject')}
                    leadingIcon={<XCircle size={14} style={{ color: 'var(--color-danger)' }} />}
                  >
                    Reject
                  </Button>
                  <Button
                    variant="primary"
                    size="sm"
                    disabled={isSelfRequest}
                    onClick={() => handleOpenDecisionModal(approval, 'approve')}
                    leadingIcon={<CheckCircle2 size={14} />}
                  >
                    Approve Request
                  </Button>
                </div>
              </Card>
            )
          })}

          {/* Pagination */}
          {approvalPage.totalPages > 1 && (
            <Pagination
              page={approvalPage.page}
              totalPages={approvalPage.totalPages}
              totalElements={approvalPage.totalElements}
              onPageChange={(p) => setPage(p)}
            />
          )}
        </div>
      )}

      {/* Decision Modal (Approve / Reject) */}
      <Modal
        isOpen={activeApproval !== null && decisionType !== null}
        onClose={handleCloseModal}
        title={
          decisionType === 'approve'
            ? `Approve ${activeApproval?.ticketNumber}`
            : `Reject ${activeApproval?.ticketNumber}`
        }
        subtitle={
          decisionType === 'approve'
            ? 'Approving this request will move it to the support queue for an engineer to fulfill.'
            : 'Rejecting this request will cancel it and notify the requester with your reason.'
        }
        footer={
          <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
            <Button variant="secondary" size="sm" onClick={handleCloseModal} disabled={isDeciding}>
              Cancel
            </Button>
            <Button
              variant={decisionType === 'approve' ? 'primary' : 'danger'}
              size="sm"
              onClick={() => void handleConfirmDecision()}
              isLoading={isDeciding}
              disabled={decisionType === 'reject' && !decisionNote.trim()}
              leadingIcon={decisionType === 'approve' ? <CheckCircle2 size={14} /> : <X size={14} />}
            >
              {decisionType === 'approve' ? 'Confirm Approval' : 'Confirm Rejection'}
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', padding: '0.5rem 0' }}>
          {decisionError && (
            <div className="horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ margin: 0, padding: '0.5rem' }}>
              <AlertCircle size={16} className="horizon-auth-alert-icon" />
              <span style={{ fontSize: 'var(--font-size-xs)' }}>{decisionError}</span>
            </div>
          )}

          {/* Context box */}
          <div style={{ padding: '0.75rem', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
            <p style={{ margin: '0 0 4px', fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-secondary)' }}>
              Ticket Subject:
            </p>
            <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-medium)', color: 'var(--color-text-primary)' }}>
              {activeApproval?.ticketTitle}
            </p>
          </div>

          {/* Decision note input */}
          <div className="horizon-form-field" style={{ margin: 0 }}>
            <label htmlFor="approval-decision-note" className="horizon-label" style={{ margin: '0 0 4px' }}>
              {decisionType === 'approve' ? (
                'Approval Note (Optional)'
              ) : (
                <span>
                  Rejection Reason <span className="horizon-field-required">*</span>
                </span>
              )}
            </label>
            <textarea
                id="approval-decision-note"
                className="horizon-textarea"
                style={{ minHeight: '100px' }}
                placeholder={
                  decisionType === 'approve'
                    ? 'Add any conditions, provisioning instructions, or notes for the support team...'
                    : 'Explain why this service request cannot be fulfilled at this time...'
                }
                value={decisionNote}
                onChange={(e) => setDecisionNote(e.target.value)}
                disabled={isDeciding}
                maxLength={2000}
                required={decisionType === 'reject'}
            />
            <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', marginTop: '4px', textAlign: 'right' }}>
              {decisionNote.length}/2000
            </span>
          </div>
        </div>
      </Modal>
    </div>
  )
}
