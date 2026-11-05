import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  AlertCircle,
  FileCheck,
  RefreshCw,
  Shield,
  ShieldAlert,
  ShieldCheck,
  Users,
} from 'lucide-react'

import { rescanPendingAttachments } from '../api/attachment'
import { useAuth } from '../auth/useAuth'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { Modal } from '../components/ui/Modal'
import type { AttachmentRescanReport } from '../types/attachment'

export function AttachmentSecurityPage() {
  const { accessToken } = useAuth()
  const navigate = useNavigate()

  const [batchSize, setBatchSize] = useState(50)
  const [isConfirming, setIsConfirming] = useState(false)
  const [isRescanning, setIsRescanning] = useState(false)
  const [report, setReport] = useState<AttachmentRescanReport | null>(null)
  const [error, setError] = useState<string | null>(null)

  function handleBatchSizeChange(value: number) {
    if (Number.isNaN(value)) {
      setBatchSize(1)
      return
    }
    const bounded = Math.max(1, Math.min(100, value))
    setBatchSize(bounded)
  }

  function handleTriggerClick(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setIsConfirming(true)
  }

  async function handleConfirmRescan() {
    if (!accessToken) {
      setError('Your session is no longer valid')
      setIsConfirming(false)
      return
    }

    setIsConfirming(false)
    setIsRescanning(true)
    setError(null)

    try {
      const result = await rescanPendingAttachments(accessToken, batchSize)
      setReport(result)
    } catch (caughtError) {
      setError(
        caughtError instanceof Error
          ? caughtError.message
          : 'Failed to complete attachment rescan.',
      )
    } finally {
      setIsRescanning(false)
    }
  }

  return (
    <div className="attachment-security-page horizon-page-container">
      {/* Page Header */}
      <PageHeader
        title="Attachment Security & Malware Quarantine"
        subtitle="Administrative security maintenance to audit historical attachment scan states and trigger bounded batch rescans with ClamAV."
        actions={
          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => navigate('/audit')}
              leadingIcon={<ShieldCheck size={14} />}
            >
              Audit Trail
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => navigate('/admin/users')}
              leadingIcon={<Users size={14} />}
            >
              User Management
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

      {/* Explanation Architecture Card */}
      <Card padded style={{ marginBottom: '1.5rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '1rem', borderBottom: '1px solid var(--color-divider)', paddingBottom: '0.75rem' }}>
          <Shield size={18} style={{ color: 'var(--color-primary)' }} />
          <div>
            <h2 style={{ margin: 0, fontSize: 'var(--font-size-base)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)' }}>
              Malware Scanner Lifecycle & Rescan Architecture
            </h2>
            <p style={{ margin: '2px 0 0', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
              How FlowDesk protects users from malicious payloads using ClamAV stream analysis.
            </p>
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '1rem', fontSize: 'var(--font-size-xs)', lineHeight: 'var(--line-height-relaxed)' }}>
          <div style={{ padding: '1rem', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)', marginBottom: '4px' }}>
              <Badge variant="warning" size="sm">PENDING_SCAN</Badge>
              <span>Initial Quarantine</span>
            </div>
            <p style={{ margin: 0, color: 'var(--color-text-secondary)' }}>
              Every uploaded file enters quarantine in an unreleased state. Direct customer and staff downloads are blocked until ClamAV stream inspection concludes.
            </p>
          </div>

          <div style={{ padding: '1rem', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)', marginBottom: '4px' }}>
              <Badge variant="success" size="sm">CLEAN</Badge>
              <span>Verified Safe</span>
            </div>
            <p style={{ margin: 0, color: 'var(--color-text-secondary)' }}>
              Files with zero signature detections and valid headers are released for secure download by authorized ticket participants.
            </p>
          </div>

          <div style={{ padding: '1rem', background: 'var(--color-surface-subtle)', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)', marginBottom: '4px' }}>
              <Badge variant="danger" size="sm">INFECTED</Badge>
              <span>Permanent Isolation</span>
            </div>
            <p style={{ margin: 0, color: 'var(--color-text-secondary)' }}>
              Malware detections trigger immutable quarantine. Infected payloads cannot be downloaded, with quarantine badges displayed in conversation threads.
            </p>
          </div>
        </div>
      </Card>

      {/* Trigger Control Card */}
      <Card padded style={{ marginBottom: '1.5rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '1rem', borderBottom: '1px solid var(--color-divider)', paddingBottom: '0.75rem' }}>
          <ShieldAlert size={18} style={{ color: 'var(--color-warning)' }} />
          <div>
            <h2 style={{ margin: 0, fontSize: 'var(--font-size-base)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)' }}>
              Batch Rescan Routine
            </h2>
            <p style={{ margin: '2px 0 0', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
              Process historical or uninspected attachments using PostgreSQL row-level locks with FOR UPDATE SKIP LOCKED.
            </p>
          </div>
        </div>

        <form onSubmit={handleTriggerClick} style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          <div style={{ maxWidth: '320px' }}>
            <label
              htmlFor="rescan-batch-size"
              style={{ display: 'block', fontSize: 'var(--font-size-xs)', fontWeight: 'var(--font-weight-medium)', color: 'var(--color-text-primary)', marginBottom: '6px' }}
            >
              Batch Size (Bounded 1–100) *
            </label>
            <div className="horizon-input-wrapper">
              <input
                id="rescan-batch-size"
                type="number"
                min={1}
                max={100}
                className="horizon-input"
                value={batchSize}
                onChange={(e) => handleBatchSizeChange(parseInt(e.target.value, 10))}
                required
                disabled={isRescanning}
              />
            </div>
            <span style={{ display: 'block', fontSize: '11px', color: 'var(--color-text-muted)', marginTop: '4px' }}>
              Specifies the maximum number of pending attachments scanned in this run. Uses PostgreSQL <code>FOR UPDATE SKIP LOCKED</code> to prevent duplicate concurrent processing.
            </span>
          </div>

          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
            <Button
              type="submit"
              variant="primary"
              size="md"
              disabled={isRescanning}
              isLoading={isRescanning}
              leadingIcon={<RefreshCw size={14} className={isRescanning ? 'horizon-spin' : ''} />}
            >
              {isRescanning ? 'Scanning Attachments...' : 'Trigger Batch Rescan'}
            </Button>
          </div>
        </form>
      </Card>

      {/* Completion Report Section */}
      {report && (
        <Card padded>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', borderBottom: '1px solid var(--color-divider)', paddingBottom: '0.75rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <FileCheck size={18} style={{ color: 'var(--color-success)' }} />
              <div>
                <h2 style={{ margin: 0, fontSize: 'var(--font-size-base)', fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)' }}>
                  Batch Rescan Results
                </h2>
                <p style={{ margin: '2px 0 0', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
                  {report.message}
                </p>
              </div>
            </div>
            <Badge variant={report.infectedCount > 0 ? 'danger' : 'success'} size="md">
              {report.scannedCount} Scanned
            </Badge>
          </div>

          <div className="horizon-rescan-metrics-grid">
            <div className="horizon-rescan-metric-pill">
              <span className="horizon-rescan-metric-value">{report.totalPending}</span>
              <span className="horizon-rescan-metric-label">Total Pending</span>
            </div>

            <div className="horizon-rescan-metric-pill">
              <span className="horizon-rescan-metric-value" style={{ color: 'var(--color-primary)' }}>
                {report.scannedCount}
              </span>
              <span className="horizon-rescan-metric-label">Scanned in Batch</span>
            </div>

            <div className="horizon-rescan-metric-pill">
              <span className="horizon-rescan-metric-value" style={{ color: 'var(--color-success)' }}>
                {report.cleanCount}
              </span>
              <span className="horizon-rescan-metric-label">Verified Clean</span>
            </div>

            <div className="horizon-rescan-metric-pill">
              <span className="horizon-rescan-metric-value" style={{ color: report.infectedCount > 0 ? 'var(--color-danger)' : 'inherit' }}>
                {report.infectedCount}
              </span>
              <span className="horizon-rescan-metric-label">Infected Quarantined</span>
            </div>

            <div className="horizon-rescan-metric-pill">
              <span className="horizon-rescan-metric-value" style={{ color: report.failedCount > 0 ? 'var(--color-warning)' : 'inherit' }}>
                {report.failedCount}
              </span>
              <span className="horizon-rescan-metric-label">Scan Failures</span>
            </div>
          </div>
        </Card>
      )}

      {/* Confirmation Modal */}
      <Modal
        isOpen={isConfirming}
        onClose={() => setIsConfirming(false)}
        title="Confirm Attachment Rescan"
        subtitle="Administrative Operation"
        footer={
          <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
            <Button variant="secondary" size="sm" onClick={() => setIsConfirming(false)}>
              Cancel
            </Button>
            <Button variant="primary" size="sm" onClick={() => void handleConfirmRescan()}>
              Confirm & Start Rescan
            </Button>
          </div>
        }
      >
        <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', lineHeight: 'var(--line-height-relaxed)', color: 'var(--color-text-primary)' }}>
          Are you sure you want to trigger a batch rescan for up to <strong>{batchSize}</strong> pending attachments?
          This will stream files through ClamAV and update attachment security classifications in PostgreSQL.
        </p>
      </Modal>
    </div>
  )
}
