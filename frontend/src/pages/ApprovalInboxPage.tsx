import {
  type FormEvent,
  useEffect,
  useState,
} from 'react'

import {
  useNavigate,
} from 'react-router-dom'

import {
  approveApproval,
  getPendingApprovals,
  rejectApproval,
} from '../api/approvals'

import {
  useAuth,
} from '../auth/useAuth'

import {
  NotificationBell,
} from '../components/NotificationBell'

import type {
  Approval,
  ApprovalsPage,
} from '../types/approval'

const PAGE_SIZE = 10

export function ApprovalInboxPage() {
  const {
    accessToken,
    user,
    signOut,
  } = useAuth()

  const navigate =
    useNavigate()

  const [
    page,
    setPage,
  ] =
    useState(0)

  const [
    approvalPage,
    setApprovalPage,
  ] =
    useState<ApprovalsPage | null>(
      null,
    )

  const [
    refreshVersion,
    setRefreshVersion,
  ] =
    useState(0)

  const [
    isLoading,
    setIsLoading,
  ] =
    useState(true)

  const [
    decisionApprovalId,
    setDecisionApprovalId,
  ] =
    useState<string | null>(null)

  const [
    error,
    setError,
  ] =
    useState<string | null>(null)

  const [
    successMessage,
    setSuccessMessage,
  ] =
    useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadApprovals() {
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
          await getPendingApprovals(
            accessToken,
            page,
            PAGE_SIZE,
          )

        if (!cancelled) {
          setApprovalPage(
            response,
          )
        }
      } catch (caughtError) {
        if (cancelled) {
          return
        }

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
  }, [
    accessToken,
    page,
    refreshVersion,
  ])

  function handleSignOut() {
    signOut()

    navigate('/login')
  }

  function refreshAfterDecision(
    ticketNumber: string,
    decision: 'approved' | 'rejected',
  ) {
    setSuccessMessage(
      `${ticketNumber} ${decision} successfully.`,
    )

    if (
      approvalPage &&
      approvalPage.content.length === 1 &&
      page > 0
    ) {
      setPage(
        (currentPage) =>
          Math.max(
            0,
            currentPage - 1,
          ),
      )

      return
    }

    setRefreshVersion(
      (currentVersion) =>
        currentVersion + 1,
    )
  }

  async function handleApprove(
    approval: Approval,
    note: string,
  ) {
    if (!accessToken) {
      setError(
        'Your session is no longer valid',
      )

      return
    }

    setDecisionApprovalId(
      approval.id,
    )

    setError(null)
    setSuccessMessage(null)

    try {
      await approveApproval(
        accessToken,
        approval.id,
        note,
      )

      refreshAfterDecision(
        approval.ticketNumber,
        'approved',
      )
    } catch (caughtError) {
      setError(
        caughtError instanceof Error
          ? caughtError.message
          : 'Unable to approve this request.',
      )
    } finally {
      setDecisionApprovalId(
        null,
      )
    }
  }

  async function handleReject(
    approval: Approval,
    reason: string,
  ) {
    if (!accessToken) {
      setError(
        'Your session is no longer valid',
      )

      return
    }

    const normalizedReason =
      reason.trim()

    if (normalizedReason === '') {
      setError(
        'Rejection reason is required.',
      )

      return
    }

    setDecisionApprovalId(
      approval.id,
    )

    setError(null)
    setSuccessMessage(null)

    try {
      await rejectApproval(
        accessToken,
        approval.id,
        normalizedReason,
      )

      refreshAfterDecision(
        approval.ticketNumber,
        'rejected',
      )
    } catch (caughtError) {
      setError(
        caughtError instanceof Error
          ? caughtError.message
          : 'Unable to reject this request.',
      )
    } finally {
      setDecisionApprovalId(
        null,
      )
    }
  }

  function handlePreviousPage() {
    setPage(
      (currentPage) =>
        Math.max(
          0,
          currentPage - 1,
        ),
    )
  }

  function handleNextPage() {
    if (
      approvalPage?.last
    ) {
      return
    }

    setPage(
      (currentPage) =>
        currentPage + 1,
    )
  }

  return (
    <main className="dashboard-page">
      <header className="dashboard-header">
        <div>
          <p className="eyebrow">
            FlowDesk
          </p>

          <h1>
            Approval Inbox
          </h1>

          <p className="dashboard-subtitle">
            Review pending service requests
            before they enter the support
            workflow and begin SLA tracking.
          </p>
        </div>

        <div className="dashboard-header-actions">
          <NotificationBell />

          <button
            type="button"
            className="dashboard-secondary-button"
            onClick={() =>
              navigate(
                '/dashboard',
              )
            }
          >
            Dashboard
          </button>

          <button
            type="button"
            onClick={handleSignOut}
          >
            Sign out
          </button>
        </div>
      </header>

      {successMessage && (
        <p className="support-alert support-alert-success">
          {successMessage}
        </p>
      )}

      {error && (
        <p className="support-alert support-alert-error">
          {error}
        </p>
      )}

      {isLoading && (
        <section className="support-loading-state">
          <div className="support-loading-dot" />

          <p>
            Loading pending approvals...
          </p>
        </section>
      )}

      {!isLoading &&
        approvalPage &&
        approvalPage.content.length === 0 && (
          <section className="support-empty-state">
            <div className="support-empty-mark">
              ✓
            </div>

            <h2>
              Approval inbox is clear
            </h2>

            <p>
              There are currently no pending
              service requests waiting for a
              decision.
            </p>
          </section>
        )}

      {!isLoading &&
        approvalPage &&
        approvalPage.content.length > 0 && (
          <>
            <section className="user-card">
              <div className="account-card-heading">
                <div>
                  <p className="dashboard-card-label">
                    Pending decisions
                  </p>

                  <h2>
                    Service requests awaiting approval
                  </h2>
                </div>

                <span className="account-status">
                  {
                    approvalPage
                      .totalElements
                  }{' '}
                  pending
                </span>
              </div>

              <div className="dashboard-workspace-grid">
                {approvalPage.content.map(
                  (approval) => (
                    <ApprovalCard
                      key={
                        approval.id
                      }
                      approval={
                        approval
                      }
                      isOwnRequest={
                        approval
                          .requestedByUserId ===
                        user?.userId
                      }
                      isSubmitting={
                        decisionApprovalId ===
                        approval.id
                      }
                      onApprove={
                        handleApprove
                      }
                      onReject={
                        handleReject
                      }
                    />
                  ),
                )}
              </div>
            </section>

            {approvalPage.totalPages > 1 && (
              <nav
                className="pagination"
                aria-label="Approval inbox pagination"
              >
                <button
                  type="button"
                  disabled={
                    approvalPage.first
                  }
                  onClick={
                    handlePreviousPage
                  }
                >
                  Previous
                </button>

                <span>
                  Page{' '}
                  {
                    approvalPage.page +
                    1
                  }{' '}
                  of{' '}
                  {
                    approvalPage
                      .totalPages
                  }
                </span>

                <button
                  type="button"
                  disabled={
                    approvalPage.last
                  }
                  onClick={
                    handleNextPage
                  }
                >
                  Next
                </button>
              </nav>
            )}
          </>
        )}
    </main>
  )
}

interface ApprovalCardProps {
  approval: Approval
  isOwnRequest: boolean
  isSubmitting: boolean
  onApprove: (
    approval: Approval,
    note: string,
  ) => Promise<void>
  onReject: (
    approval: Approval,
    reason: string,
  ) => Promise<void>
}

function ApprovalCard({
  approval,
  isOwnRequest,
  isSubmitting,
  onApprove,
  onReject,
}: ApprovalCardProps) {
  const [
    approvalNote,
    setApprovalNote,
  ] =
    useState('')

  const [
    rejectionReason,
    setRejectionReason,
  ] =
    useState('')

  function handleApproveSubmit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    void onApprove(
      approval,
      approvalNote,
    )
  }

  function handleRejectSubmit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    void onReject(
      approval,
      rejectionReason,
    )
  }

  return (
    <article className="dashboard-workspace-card">
      <p className="dashboard-card-label">
        {approval.ticketNumber}
      </p>

      <h2>
        {approval.ticketTitle}
      </h2>

      <p>
        {approval.ticketDescription}
      </p>

      <div className="account-details-grid">
        <div>
          <span>
            Requester
          </span>

          <strong>
            {approval.requestedByEmail}
          </strong>
        </div>

        <div>
          <span>
            Priority
          </span>

          <strong>
            {formatLabel(
              approval.priority,
            )}
          </strong>
        </div>

        <div>
          <span>
            Requested
          </span>

          <strong>
            {formatDate(
              approval.requestedAt,
            )}
          </strong>
        </div>
      </div>

      {isOwnRequest ? (
        <p className="support-alert support-alert-error">
          You requested this service.
          Another Team Lead or Admin must
          decide it.
        </p>
      ) : (
        <>
          <form
            className="ticket-form"
            onSubmit={
              handleApproveSubmit
            }
          >
            <label>
              Approval note

              <textarea
                value={
                  approvalNote
                }
                maxLength={500}
                disabled={
                  isSubmitting
                }
                placeholder="Optional note for this approval"
                onChange={(
                  event,
                ) =>
                  setApprovalNote(
                    event.target.value,
                  )
                }
              />
            </label>

            <button
              type="submit"
              disabled={
                isSubmitting
              }
            >
              {isSubmitting
                ? 'Processing...'
                : 'Approve request'}
            </button>
          </form>

          <form
            className="ticket-form"
            onSubmit={
              handleRejectSubmit
            }
          >
            <label>
              Rejection reason

              <textarea
                value={
                  rejectionReason
                }
                maxLength={500}
                required
                disabled={
                  isSubmitting
                }
                placeholder="Required reason for rejection"
                onChange={(
                  event,
                ) =>
                  setRejectionReason(
                    event.target.value,
                  )
                }
              />
            </label>

            <button
              type="submit"
              disabled={
                isSubmitting ||
                rejectionReason
                  .trim() === ''
              }
            >
              {isSubmitting
                ? 'Processing...'
                : 'Reject request'}
            </button>
          </form>
        </>
      )}
    </article>
  )
}

function formatLabel(
  value: string,
) {
  return value
    .replaceAll(
      '_',
      ' ',
    )
    .toLowerCase()
    .replace(
      /\b\w/g,
      (character) =>
        character.toUpperCase(),
    )
}

function formatDate(
  value: string,
) {
  const date =
    new Date(
      value,
    )

  if (
    Number.isNaN(
      date.getTime(),
    )
  ) {
    return value
  }

  return date.toLocaleString()
}