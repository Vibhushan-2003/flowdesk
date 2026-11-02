import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  AlertCircle,
  Copy,
  Eye,
  RefreshCw,
  Search,
  ShieldAlert,
  ShieldCheck,
  User,
  Cpu,
  Check,
} from 'lucide-react'

import { getAuditEvents } from '../api/audit'
import { useAuth } from '../auth/useAuth'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { Modal } from '../components/ui/Modal'
import { Pagination } from '../components/ui/Pagination'
import { EmptyState } from '../components/ui/EmptyState'
import type {
  AuditAction,
  AuditEvent,
  AuditEventsPage,
} from '../types/audit'
import { formatDate, formatLabel } from '../utils/formatters'

const PAGE_SIZE = 20

const AUDIT_ACTIONS: AuditAction[] = [
  'TICKET_CREATED',
  'TICKET_CLAIMED',
  'TICKET_STATUS_CHANGED',
  'SLA_RESPONSE_BREACHED',
  'SLA_RESOLUTION_BREACHED',
  'APPROVAL_REQUESTED',
  'APPROVAL_APPROVED',
  'APPROVAL_REJECTED',
  'USER_CREATED',
  'USER_ROLES_UPDATED',
  'USER_ACTIVATED',
  'USER_DEACTIVATED',
  'AI_TRIAGE_GENERATED',
  'AI_TRIAGE_APPLIED',
  'AI_REPLY_DRAFT_GENERATED',
  'AI_RESOLUTION_SUMMARY_DRAFT_GENERATED',
  'AI_SUPPORT_COPILOT_USED',
  'USER_INVITED',
  'USER_INVITATION_RESENT',
  'USER_INVITATION_ACCEPTED',
]

function getActionBadgeVariant(action: AuditAction): 'primary' | 'success' | 'warning' | 'danger' | 'neutral' {
  if (action.startsWith('SLA_')) return 'danger'
  if (action.startsWith('APPROVAL_')) {
    return action === 'APPROVAL_APPROVED' ? 'success' : action === 'APPROVAL_REJECTED' ? 'danger' : 'warning'
  }
  if (action.startsWith('USER_')) {
    return action === 'USER_DEACTIVATED' ? 'danger' : action === 'USER_ACTIVATED' ? 'success' : 'primary'
  }
  if (action.startsWith('AI_')) return 'neutral'
  return 'primary'
}

function getActionLabel(action: AuditAction): string {
  switch (action) {
    case 'TICKET_CREATED': return 'Ticket Created'
    case 'TICKET_CLAIMED': return 'Ticket Claimed'
    case 'TICKET_STATUS_CHANGED': return 'Status Changed'
    case 'SLA_RESPONSE_BREACHED': return 'SLA Response Missed'
    case 'SLA_RESOLUTION_BREACHED': return 'SLA Resolution Missed'
    case 'APPROVAL_REQUESTED': return 'Approval Requested'
    case 'APPROVAL_APPROVED': return 'Approval Approved'
    case 'APPROVAL_REJECTED': return 'Approval Rejected'
    case 'USER_CREATED': return 'User Created'
    case 'USER_ROLES_UPDATED': return 'User Roles Updated'
    case 'USER_ACTIVATED': return 'User Activated'
    case 'USER_DEACTIVATED': return 'User Deactivated'
    case 'USER_INVITED': return 'User Invited'
    case 'USER_INVITATION_RESENT': return 'Invitation Resent'
    case 'USER_INVITATION_ACCEPTED': return 'Invitation Accepted'
    case 'AI_TRIAGE_GENERATED': return 'AI Triage Generated'
    case 'AI_TRIAGE_APPLIED': return 'AI Triage Applied'
    case 'AI_REPLY_DRAFT_GENERATED': return 'AI Reply Drafted'
    case 'AI_RESOLUTION_SUMMARY_DRAFT_GENERATED': return 'AI Resolution Summary'
    case 'AI_SUPPORT_COPILOT_USED': return 'AI Copilot Query'
    default: return formatLabel(action)
  }
}

function getActionDescription(event: AuditEvent): string {
  const reference = event.targetReference ?? event.targetId
  switch (event.action) {
    case 'TICKET_CREATED':
      return `Ticket ${reference} was submitted.`
    case 'TICKET_CLAIMED':
      return `Ticket ${reference} was claimed into staff workload.`
    case 'TICKET_STATUS_CHANGED': {
      const fromStatus = (event.metadata['fromStatus'] as string) || 'UNKNOWN'
      const toStatus = (event.metadata['toStatus'] as string) || 'UNKNOWN'
      return `Ticket ${reference} transitioned from ${formatLabel(fromStatus)} to ${formatLabel(toStatus)}.`
    }
    case 'SLA_RESPONSE_BREACHED':
      return `Ticket ${reference} breached its first-response deadline.`
    case 'SLA_RESOLUTION_BREACHED':
      return `Ticket ${reference} breached its resolution deadline.`
    case 'APPROVAL_REQUESTED':
      return `Supervisory authorization requested for ticket ${reference}.`
    case 'APPROVAL_APPROVED':
      return `Service request ${reference} was approved.`
    case 'APPROVAL_REJECTED':
      return `Service request ${reference} was rejected.`
    case 'USER_CREATED':
      return `User account ${reference} was created.`
    case 'USER_ROLES_UPDATED':
      return `Roles were updated for ${reference}.`
    case 'USER_ACTIVATED':
      return `User account ${reference} was activated.`
    case 'USER_DEACTIVATED':
      return `User account ${reference} was deactivated.`
    case 'USER_INVITED':
      return `Invitation email dispatched to ${reference}.`
    case 'USER_INVITATION_RESENT':
      return `Invitation email re-sent to ${reference}.`
    case 'USER_INVITATION_ACCEPTED':
      return `Invitation accepted and account activated for ${reference}.`
    case 'AI_TRIAGE_GENERATED':
      return `AI triage recommendations generated for ticket ${reference}.`
    case 'AI_TRIAGE_APPLIED':
      return `AI triage recommendation applied to ticket ${reference}.`
    case 'AI_REPLY_DRAFT_GENERATED':
      return `AI reply draft generated for ticket ${reference}.`
    case 'AI_RESOLUTION_SUMMARY_DRAFT_GENERATED':
      return `AI resolution summary drafted for ticket ${reference}.`
    case 'AI_SUPPORT_COPILOT_USED':
      return `AI Copilot consulted for ticket ${reference}.`
    default:
      return `${formatLabel(event.action)} for ${reference}.`
  }
}

export function AuditTrailPage() {
  const { accessToken } = useAuth()
  const navigate = useNavigate()

  const [page, setPage] = useState(0)
  const [selectedAction, setSelectedAction] = useState<AuditAction | ''>('')
  const [searchQuery, setSearchQuery] = useState('')
  const [appliedAction, setAppliedAction] = useState<AuditAction | undefined>(undefined)
  const [appliedSearch, setAppliedSearch] = useState<string | undefined>(undefined)

  const [auditPage, setAuditPage] = useState<AuditEventsPage | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Inspection modal
  const [inspectingEvent, setInspectingEvent] = useState<AuditEvent | null>(null)
  const [hasCopiedJson, setHasCopiedJson] = useState(false)

  function handleFilterSubmit(event: FormEvent) {
    event.preventDefault()
    setPage(0)
    setAppliedAction(selectedAction ? selectedAction : undefined)
    setAppliedSearch(searchQuery.trim() ? searchQuery.trim() : undefined)
  }

  function handleResetFilters() {
    setSelectedAction('')
    setSearchQuery('')
    setAppliedAction(undefined)
    setAppliedSearch(undefined)
    setPage(0)
  }

  useEffect(() => {
    let cancelled = false

    async function loadAuditEvents() {
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
        const response = await getAuditEvents(
          accessToken,
          page,
          PAGE_SIZE,
          {
            action: appliedAction,
            search: appliedSearch,
          },
        )

        if (!cancelled) {
          setAuditPage(response)
        }
      } catch (caughtError) {
        if (cancelled) return
        setError(
          caughtError instanceof Error
            ? caughtError.message
            : 'Unable to load audit history.',
        )
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    void loadAuditEvents()

    return () => {
      cancelled = true
    }
  }, [accessToken, page, appliedAction, appliedSearch])

  function handleCopyJson() {
    if (!inspectingEvent) return
    void navigator.clipboard.writeText(JSON.stringify(inspectingEvent, null, 2))
    setHasCopiedJson(true)
    setTimeout(() => setHasCopiedJson(false), 2000)
  }

  const hasActiveFilters = Boolean(appliedAction || appliedSearch)

  return (
    <div className="audit-trail-page horizon-page-container">
      {/* Page Header */}
      <PageHeader
        title="Security Audit Trail"
        subtitle="Review immutable business activity, operational state transitions, and compliance audit events."
        actions={
          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => navigate('/admin/attachments')}
              leadingIcon={<ShieldAlert size={14} />}
            >
              Attachment Security
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setPage(0)}
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

      {/* Filter and Search Form */}
      <Card padded style={{ marginBottom: '1.5rem' }}>
        <form
          className="horizon-filter-form"
          onSubmit={handleFilterSubmit}
          style={{ display: 'flex', gap: '1rem', alignItems: 'flex-end', flexWrap: 'wrap' }}
          aria-label="Audit filters"
        >
          <div style={{ flex: '1 1 240px' }}>
            <label
              htmlFor="audit-search-input"
              style={{ display: 'block', fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-medium)', color: 'var(--color-text-secondary)', marginBottom: '4px' }}
            >
              Search target or actor
            </label>
            <div className="horizon-input-wrapper">
              <Search size={14} className="horizon-input-icon" />
              <input
                id="audit-search-input"
                type="search"
                className="horizon-input"
                value={searchQuery}
                maxLength={100}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="e.g. FD-000028 or user@example.com"
              />
            </div>
          </div>

          <div style={{ flex: '1 1 200px' }}>
            <label
              htmlFor="audit-action-select"
              style={{ display: 'block', fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-medium)', color: 'var(--color-text-secondary)', marginBottom: '4px' }}
            >
              Action Filter
            </label>
            <select
              id="audit-action-select"
              className="horizon-select"
              value={selectedAction}
              onChange={(e) => setSelectedAction(e.target.value as AuditAction | '')}
            >
              <option value="">All actions</option>
              {AUDIT_ACTIONS.map((action) => (
                <option key={action} value={action}>
                  {getActionLabel(action)}
                </option>
              ))}
            </select>
          </div>

          <div style={{ display: 'flex', gap: '0.5rem', marginLeft: 'auto' }}>
            <Button variant="primary" size="sm" type="submit">
              Filter
            </Button>
            <Button variant="secondary" size="sm" type="button" onClick={handleResetFilters}>
              Clear
            </Button>
          </div>
        </form>
      </Card>

      {/* Main Audit Table Card */}
      <Card padded>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem', paddingBottom: '0.75rem', borderBottom: '1px solid var(--color-divider)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <ShieldCheck size={18} style={{ color: 'var(--color-primary)' }} />
            <h2 style={{ margin: 0, fontSize: 'var(--font-size-base)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)' }}>
              Audit Events Log
            </h2>
          </div>
          {auditPage && (
            <span style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
              {auditPage.totalElements} total {auditPage.totalElements === 1 ? 'event' : 'events'}
            </span>
          )}
        </div>

        {/* Loading state */}
        {isLoading && (
          <div style={{ textAlign: 'center', padding: '3.5rem 1rem', color: 'var(--color-text-secondary)' }}>
            <RefreshCw size={24} className="horizon-spin" style={{ margin: '0 auto 12px', display: 'block', color: 'var(--color-primary)' }} />
            <p>Loading audit history...</p>
          </div>
        )}

        {/* Empty state */}
        {!isLoading && auditPage && auditPage.content.length === 0 && (
          <EmptyState
            icon={<ShieldCheck size={36} />}
            title={hasActiveFilters ? 'No matching audit events' : 'No audit events recorded yet'}
            description={
              hasActiveFilters
                ? 'Try adjusting your search query or action filter.'
                : 'Business activity will appear here once auditable system actions occur.'
            }
            action={
              hasActiveFilters ? (
                <Button variant="secondary" size="sm" onClick={handleResetFilters}>
                  Clear filters
                </Button>
              ) : undefined
            }
          />
        )}

        {/* Desktop Table View (>=768px) */}
        {!isLoading && auditPage && auditPage.content.length > 0 && (
          <>
            <div className="desktop-ticket-table-view" style={{ overflowX: 'auto' }}>
              <table className="horizon-data-table-simple">
                <thead>
                  <tr>
                    <th>Timestamp</th>
                    <th>Action</th>
                    <th>Target Reference</th>
                    <th>Description</th>
                    <th>Actor</th>
                    <th style={{ textAlign: 'right' }}>Inspect</th>
                  </tr>
                </thead>
                <tbody>
                  {auditPage.content.map((event) => {
                    const isSystem = event.actorType === 'SYSTEM'
                    const actorDisplay = event.actorEmail || event.actorUserId || 'System Service'

                    return (
                      <tr key={event.id}>
                        <td style={{ whiteSpace: 'nowrap', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', fontFamily: 'var(--font-family-mono)' }}>
                          {formatDate(event.occurredAt)}
                        </td>
                        <td>
                          <Badge variant={getActionBadgeVariant(event.action)} size="sm">
                            {getActionLabel(event.action)}
                          </Badge>
                        </td>
                        <td>
                          <span style={{ fontFamily: 'var(--font-family-mono)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-primary)' }}>
                            {event.targetReference || event.targetId.slice(0, 8)}
                          </span>
                        </td>
                        <td style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-primary)', maxWidth: '320px', lineHeight: 1.3 }}>
                          {getActionDescription(event)}
                        </td>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                            {isSystem ? (
                              <Cpu size={14} style={{ color: 'var(--color-text-muted)' }} />
                            ) : (
                              <User size={14} style={{ color: 'var(--color-text-muted)' }} />
                            )}
                            <span style={{ fontSize: 'var(--font-size-xs)', color: isSystem ? 'var(--color-text-muted)' : 'var(--color-text-primary)' }}>
                              {actorDisplay}
                            </span>
                          </div>
                        </td>
                        <td style={{ textAlign: 'right' }}>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setInspectingEvent(event)}
                            leadingIcon={<Eye size={14} />}
                            aria-label={`Inspect event ${event.id}`}
                          >
                            Inspect
                          </Button>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>

            {/* Mobile Card View (<768px) */}
            <div className="mobile-ticket-card-view" style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {auditPage.content.map((event) => (
                <div
                  key={event.id}
                  style={{
                    padding: '1rem',
                    background: 'var(--color-surface-subtle)',
                    borderRadius: 'var(--radius-md)',
                    border: '1px solid var(--color-border)',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '0.5rem',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Badge variant={getActionBadgeVariant(event.action)} size="sm">
                      {getActionLabel(event.action)}
                    </Badge>
                    <span style={{ fontSize: '11px', color: 'var(--color-text-muted)', fontFamily: 'var(--font-family-mono)' }}>
                      {formatDate(event.occurredAt)}
                    </span>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span style={{ fontFamily: 'var(--font-family-mono)', fontWeight: 'var(--font-weight-bold)', color: 'var(--color-primary)' }}>
                      {event.targetReference || event.targetId.slice(0, 8)}
                    </span>
                  </div>

                  <p style={{ margin: 0, fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)', lineHeight: 1.4 }}>
                    {getActionDescription(event)}
                  </p>

                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '4px', paddingTop: '6px', borderTop: '1px solid var(--color-divider)' }}>
                    <span style={{ fontSize: '11px', color: 'var(--color-text-muted)' }}>
                      Actor: {event.actorEmail || (event.actorType === 'SYSTEM' ? 'System' : 'Unknown')}
                    </span>
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => setInspectingEvent(event)}
                      leadingIcon={<Eye size={12} />}
                    >
                      Inspect
                    </Button>
                  </div>
                </div>
              ))}
            </div>

            {/* Pagination */}
            {auditPage.totalPages > 1 && (
              <div style={{ marginTop: '1.5rem', display: 'flex', justifyContent: 'center' }}>
                <Pagination
                  page={page}
                  totalPages={auditPage.totalPages}
                  onPageChange={setPage}
                />
              </div>
            )}
          </>
        )}
      </Card>

      {/* Event Details & Metadata Modal */}
      <Modal
        isOpen={inspectingEvent !== null}
        onClose={() => setInspectingEvent(null)}
        title="Audit Event Inspection"
        subtitle={inspectingEvent ? `Event ID: ${inspectingEvent.id}` : undefined}
        footer={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%' }}>
            <Button
              variant="secondary"
              size="sm"
              onClick={handleCopyJson}
              leadingIcon={hasCopiedJson ? <Check size={14} /> : <Copy size={14} />}
            >
              {hasCopiedJson ? 'Copied to Clipboard' : 'Copy JSON'}
            </Button>
            <Button variant="primary" size="sm" onClick={() => setInspectingEvent(null)}>
              Close
            </Button>
          </div>
        }
      >
        {inspectingEvent && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div className="horizon-audit-metadata-grid">
              <div className="horizon-audit-metadata-item">
                <span className="horizon-audit-metadata-label">Action</span>
                <span className="horizon-audit-metadata-val">{inspectingEvent.action}</span>
              </div>
              <div className="horizon-audit-metadata-item">
                <span className="horizon-audit-metadata-label">Target Type</span>
                <span className="horizon-audit-metadata-val">{inspectingEvent.targetType}</span>
              </div>
              <div className="horizon-audit-metadata-item">
                <span className="horizon-audit-metadata-label">Target ID</span>
                <span className="horizon-audit-metadata-val">{inspectingEvent.targetId}</span>
              </div>
              <div className="horizon-audit-metadata-item">
                <span className="horizon-audit-metadata-label">Target Reference</span>
                <span className="horizon-audit-metadata-val">{inspectingEvent.targetReference || '—'}</span>
              </div>
              <div className="horizon-audit-metadata-item">
                <span className="horizon-audit-metadata-label">Actor</span>
                <span className="horizon-audit-metadata-val">{inspectingEvent.actorEmail || inspectingEvent.actorUserId || 'SYSTEM'}</span>
              </div>
              <div className="horizon-audit-metadata-item">
                <span className="horizon-audit-metadata-label">Actor Type</span>
                <span className="horizon-audit-metadata-val">{inspectingEvent.actorType}</span>
              </div>
            </div>

            <div>
              <span className="horizon-audit-metadata-label" style={{ display: 'block', marginBottom: '4px' }}>
                Event Payload & Metadata (JSON)
              </span>
              <pre className="horizon-audit-json-viewer">
                {JSON.stringify(inspectingEvent.metadata, null, 2)}
              </pre>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}
