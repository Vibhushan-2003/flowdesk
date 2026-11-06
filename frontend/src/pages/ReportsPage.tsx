import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowLeft,
  BarChart3,
  CheckCircle2,
  Clock,
  Download,
  HelpCircle,
  RefreshCw,
  TrendingUp,
  AlertCircle,
  Layers,
  FileSpreadsheet,
  ShieldCheck,
  Users,
} from 'lucide-react'

import {
  downloadBlob,
  exportOperationalReportCsv,
  exportTicketsCsv,
  getOperationalReport,
} from '../api/reports'
import { useAuth } from '../auth/useAuth'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { PriorityBadge } from '../components/ui/PriorityBadge'
import { StatusBadge } from '../components/ui/StatusBadge'
import type { OperationalReport } from '../types/reports'
import type { TicketPriority, TicketStatus } from '../types/ticket'

function formatDate(date: Date): string {
  return date.toISOString().slice(0, 10)
}

function getPresetDates(days: number): { start: string; end: string } {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - days)
  return { start: formatDate(start), end: formatDate(end) }
}

export function ReportsPage() {
  const { accessToken } = useAuth()

  const defaultDates = getPresetDates(30)
  const [startDateInput, setStartDateInput] = useState(defaultDates.start)
  const [endDateInput, setEndDateInput] = useState(defaultDates.end)
  const [appliedDates, setAppliedDates] = useState<{ start: string; end: string }>(defaultDates)
  const [activePreset, setActivePreset] = useState<'7d' | '30d' | '90d' | 'custom'>('30d')

  const [report, setReport] = useState<OperationalReport | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [isExportingSummary, setIsExportingSummary] = useState(false)
  const [isExportingTickets, setIsExportingTickets] = useState(false)

  useEffect(() => {
    let cancelled = false

    async function fetchReport() {
      if (!accessToken) {
        if (!cancelled) {
          setError('Your session is no longer valid')
          setIsLoading(false)
        }
        return
      }

      setError(null)

      try {
        const data = await getOperationalReport(accessToken, appliedDates.start, appliedDates.end)
        if (!cancelled) {
          setReport(data)
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load operational report')
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }

    void fetchReport()

    return () => {
      cancelled = true
    }
  }, [accessToken, appliedDates])

  function handlePresetChange(preset: '7d' | '30d' | '90d') {
    setActivePreset(preset)
    const days = preset === '7d' ? 7 : preset === '30d' ? 30 : 90
    const dates = getPresetDates(days)
    setStartDateInput(dates.start)
    setEndDateInput(dates.end)
    setIsLoading(true)
    setAppliedDates(dates)
  }

  function handleCustomApply(e: FormEvent) {
    e.preventDefault()
    if (startDateInput > endDateInput) {
      setError('Start date cannot be after end date')
      return
    }
    setActivePreset('custom')
    setIsLoading(true)
    setAppliedDates({ start: startDateInput, end: endDateInput })
  }

  async function handleExportSummary() {
    if (!accessToken || !report) return
    setIsExportingSummary(true)
    try {
      const blob = await exportOperationalReportCsv(accessToken, appliedDates.start, appliedDates.end)
      downloadBlob(blob, `flowdesk-operational-report-${appliedDates.start}-to-${appliedDates.end}.csv`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to export summary CSV')
    } finally {
      setIsExportingSummary(false)
    }
  }

  async function handleExportTickets() {
    if (!accessToken || !report) return
    setIsExportingTickets(true)
    try {
      const blob = await exportTicketsCsv(accessToken, appliedDates.start, appliedDates.end, 1000)
      downloadBlob(blob, `flowdesk-tickets-export-${appliedDates.start}-to-${appliedDates.end}.csv`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to export tickets CSV')
    } finally {
      setIsExportingTickets(false)
    }
  }

  const maxTrend = report
    ? Math.max(1, ...report.dailyTrends.map((t) => Math.max(t.created, t.resolved)))
    : 1

  return (
    <div className="reports-container horizon-page-container">
      {/* Breadcrumb back link */}
      <nav style={{ marginBottom: '0.75rem' }} aria-label="Breadcrumb">
        <Link
          to="/dashboard"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            color: 'var(--color-primary)',
            textDecoration: 'none',
            fontSize: 'var(--font-size-xs)',
            fontWeight: 'var(--font-weight-medium)',
          }}
        >
          <ArrowLeft size={14} />
          <span>Back to Dashboard</span>
        </Link>
      </nav>

      {/* Page Header */}
      <PageHeader
        title="Operational Reporting & Analytics"
        subtitle="Track ticket volumes, resolution times, SLA compliance, and team workload."
        actions={
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setAppliedDates({ ...appliedDates })}
              leadingIcon={<RefreshCw size={14} className={isLoading ? 'horizon-spin' : ''} />}
              disabled={isLoading}
            >
              Refresh
            </Button>
          </div>
        }
      />

      {/* Filter Toolbar */}
      <div className="horizon-filter-toolbar reports-filter-bar" role="region" aria-label="Report Date Range Filters">
        <div className="horizon-filter-presets reports-preset-buttons">
          <button
            type="button"
            className={`horizon-preset-btn reports-preset-btn ${activePreset === '7d' ? 'is-active active' : ''}`}
            onClick={() => handlePresetChange('7d')}
          >
            Last 7 Days
          </button>
          <button
            type="button"
            className={`horizon-preset-btn reports-preset-btn ${activePreset === '30d' ? 'is-active active' : ''}`}
            onClick={() => handlePresetChange('30d')}
          >
            Last 30 Days
          </button>
          <button
            type="button"
            className={`horizon-preset-btn reports-preset-btn ${activePreset === '90d' ? 'is-active active' : ''}`}
            onClick={() => handlePresetChange('90d')}
          >
            Last 90 Days
          </button>
        </div>

        <form className="horizon-date-range-form reports-custom-dates" onSubmit={handleCustomApply}>
          <div className="horizon-date-field">
            <label htmlFor="report-start-date">From</label>
            <input
              id="report-start-date"
              type="date"
              className="horizon-date-input"
              value={startDateInput}
              onChange={(e) => setStartDateInput(e.target.value)}
              required
            />
          </div>

          <div className="horizon-date-field">
            <label htmlFor="report-end-date">To</label>
            <input
              id="report-end-date"
              type="date"
              className="horizon-date-input"
              value={endDateInput}
              onChange={(e) => setEndDateInput(e.target.value)}
              required
            />
          </div>

          <Button type="submit" variant="primary" size="sm" className="reports-apply-btn">
            Apply
          </Button>
        </form>
      </div>

      {/* Alerts */}
      {error && (
        <div className="horizon-auth-alert horizon-auth-alert--error" role="alert" style={{ marginBottom: '1.5rem' }}>
          <AlertCircle size={18} className="horizon-auth-alert-icon" />
          <span>{error}</span>
        </div>
      )}

      {/* Loading feedback */}
      {isLoading && (
        <div style={{ textAlign: 'center', padding: '4rem 0', color: 'var(--color-text-secondary)' }} role="status">
          <RefreshCw size={24} className="horizon-spin" style={{ margin: '0 auto 12px', display: 'block', color: 'var(--color-primary)' }} />
          <span>Loading operational metrics...</span>
        </div>
      )}

      {/* Report Content */}
      {!isLoading && report && (
        <>
          {/* KPI Summary Cards */}
          <div className="horizon-kpi-grid reports-kpi-grid">
            <div className="horizon-kpi-card reports-kpi-card">
              <div className="horizon-kpi-header">
                <span className="horizon-kpi-title reports-kpi-title">Created in Period</span>
                <span className="horizon-kpi-icon"><TrendingUp size={16} /></span>
              </div>
              <span className="horizon-kpi-value reports-kpi-value">{report.ticketVolume.totalCreated}</span>
              <p className="horizon-kpi-subtext reports-kpi-subtext">Total tickets submitted</p>
            </div>

            <div className="horizon-kpi-card reports-kpi-card">
              <div className="horizon-kpi-header">
                <span className="horizon-kpi-title reports-kpi-title">Resolved in Period</span>
                <span className="horizon-kpi-icon"><CheckCircle2 size={16} /></span>
              </div>
              <span className="horizon-kpi-value reports-kpi-value">{report.ticketVolume.totalResolved}</span>
              <p className="horizon-kpi-subtext reports-kpi-subtext">Completed or closed tickets</p>
            </div>

            <div className="horizon-kpi-card reports-kpi-card">
              <div className="horizon-kpi-header">
                <span className="horizon-kpi-title reports-kpi-title">Avg Resolution Time</span>
                <span className="horizon-kpi-icon"><Clock size={16} /></span>
              </div>
              <span className="horizon-kpi-value reports-kpi-value">
                {report.resolutionMetrics.avgDurationHours != null
                  ? `${report.resolutionMetrics.avgDurationHours.toFixed(1)}h`
                  : 'N/A'}
              </span>
              <p className="horizon-kpi-subtext reports-kpi-subtext">
                {report.resolutionMetrics.resolvedCount} resolved tickets
              </p>
            </div>

            <div className="horizon-kpi-card reports-kpi-card">
              <div className="horizon-kpi-header">
                <span className="horizon-kpi-title reports-kpi-title">SLA Compliance Rate</span>
                <span className="horizon-kpi-icon"><ShieldCheck size={16} /></span>
              </div>
              <span className="horizon-kpi-value reports-kpi-value">
                {report.slaCompliance.complianceRatePercent != null
                  ? `${report.slaCompliance.complianceRatePercent.toFixed(1)}%`
                  : '100%'}
              </span>
              <p className="horizon-kpi-subtext reports-kpi-subtext">
                {report.slaCompliance.compliantResolved} compliant of {report.slaCompliance.totalResolved} resolved
              </p>
            </div>

            <div className="horizon-kpi-card reports-kpi-card">
              <div className="horizon-kpi-header">
                <span className="horizon-kpi-title reports-kpi-title">KB Deflections</span>
                <span className="horizon-kpi-icon"><HelpCircle size={16} /></span>
              </div>
              <span className="horizon-kpi-value reports-kpi-value">{report.kbDeflection.totalDeflections}</span>
              <p className="horizon-kpi-subtext reports-kpi-subtext">
                {report.kbDeflection.deflectionRatePercent.toFixed(1)}% self-service deflection rate
              </p>
            </div>
          </div>

          {/* Daily Trends Chart */}
          <div className="horizon-chart-card reports-section">
            <div className="horizon-chart-header">
              <h2 className="horizon-chart-title reports-section-title">Daily Ticket Activity Trend</h2>
              <div className="horizon-chart-legend reports-chart-legend">
                <div className="horizon-legend-item reports-legend-item">
                  <span className="horizon-legend-color horizon-legend-color--created reports-legend-color created" />
                  <span>Created</span>
                </div>
                <div className="horizon-legend-item reports-legend-item">
                  <span className="horizon-legend-color horizon-legend-color--resolved reports-legend-color resolved" />
                  <span>Resolved</span>
                </div>
              </div>
            </div>

            {report.dailyTrends.length === 0 ? (
              <p className="reports-empty-state" style={{ color: 'var(--color-text-muted)', fontSize: 'var(--font-size-sm)', fontStyle: 'italic', margin: '2rem 0', textAlign: 'center' }}>
                No ticket activity recorded in this period.
              </p>
            ) : (
              <div className="horizon-chart-bars-wrap reports-chart-bars">
                {report.dailyTrends.map((trend) => (
                  <div key={trend.date} className="horizon-chart-bar-group reports-bar-group">
                    <div className="horizon-bars-pair reports-bars-wrapper">
                      <div
                        className="horizon-bar horizon-bar--created reports-bar created"
                        style={{ height: `${Math.max(4, (trend.created / maxTrend) * 100)}%` }}
                        title={`Created: ${trend.created}`}
                      />
                      <div
                        className="horizon-bar horizon-bar--resolved reports-bar resolved"
                        style={{ height: `${Math.max(4, (trend.resolved / maxTrend) * 100)}%` }}
                        title={`Resolved: ${trend.resolved}`}
                      />
                    </div>
                    <span className="horizon-bar-date reports-bar-label">{trend.date.slice(5)}</span>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Breakdowns Grid */}
          <div className="horizon-grid-2col reports-grid-2">
            {/* Status Breakdown */}
            <Card padded className="reports-section">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '1rem' }}>
                <Layers size={16} style={{ color: 'var(--color-primary)' }} />
                <h2 className="reports-section-title" style={{ margin: 0, fontSize: 'var(--font-size-base)', fontWeight: 'var(--font-weight-semibold)' }}>
                  Status Breakdown
                </h2>
              </div>
              <table className="horizon-data-table-simple reports-table">
                <thead>
                  <tr>
                    <th>Status</th>
                    <th style={{ textAlign: 'right' }}>Count</th>
                  </tr>
                </thead>
                <tbody>
                  {Object.entries(report.volumeByStatus).map(([status, count]) => (
                    <tr key={status}>
                      <td>
                        <StatusBadge status={status as TicketStatus} size="sm" />
                      </td>
                      <td style={{ textAlign: 'right', fontWeight: 'var(--font-weight-semibold)', fontFamily: 'var(--font-family-mono)' }}>
                        {count}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>

            {/* Priority Breakdown */}
            <Card padded className="reports-section">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '1rem' }}>
                <BarChart3 size={16} style={{ color: 'var(--color-warning)' }} />
                <h2 className="reports-section-title" style={{ margin: 0, fontSize: 'var(--font-size-base)', fontWeight: 'var(--font-weight-semibold)' }}>
                  Priority Breakdown
                </h2>
              </div>
              <table className="horizon-data-table-simple reports-table">
                <thead>
                  <tr>
                    <th>Priority</th>
                    <th style={{ textAlign: 'right' }}>Count</th>
                  </tr>
                </thead>
                <tbody>
                  {Object.entries(report.volumeByPriority).map(([priority, count]) => (
                    <tr key={priority}>
                      <td>
                        <PriorityBadge priority={priority as TicketPriority} size="sm" />
                      </td>
                      <td style={{ textAlign: 'right', fontWeight: 'var(--font-weight-semibold)', fontFamily: 'var(--font-family-mono)' }}>
                        {count}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
          </div>

          {/* Support Engineer Workload */}
          <Card padded style={{ marginBottom: '1.5rem' }} className="reports-section">
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '1rem' }}>
              <Users size={18} style={{ color: 'var(--color-primary)' }} />
              <h2 className="reports-section-title" style={{ margin: 0, fontSize: 'var(--font-size-base)', fontWeight: 'var(--font-weight-semibold)' }}>
                Engineer Workload
              </h2>
            </div>

            {report.engineerWorkloads.length === 0 ? (
              <p className="reports-empty-state" style={{ color: 'var(--color-text-muted)', fontSize: 'var(--font-size-sm)', fontStyle: 'italic', margin: '1rem 0' }}>
                No assigned tickets found for this period.
              </p>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table className="horizon-data-table-simple reports-table">
                  <thead>
                    <tr>
                      <th>Support Engineer</th>
                      <th style={{ textAlign: 'right' }}>Assigned</th>
                      <th style={{ textAlign: 'right' }}>Resolved</th>
                      <th style={{ textAlign: 'right' }}>Active Backlog</th>
                      <th style={{ textAlign: 'right' }}>Resolution Rate</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.engineerWorkloads.map((eng) => {
                      const rate = eng.assignedInPeriod > 0
                        ? ((eng.resolvedInPeriod / eng.assignedInPeriod) * 100).toFixed(1)
                        : 'N/A'
                      return (
                        <tr key={eng.engineerId}>
                          <td>
                            <div style={{ fontWeight: 'var(--font-weight-semibold)', color: 'var(--color-text-primary)' }}>
                              {eng.engineerName}
                            </div>
                            <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)', fontFamily: 'var(--font-family-mono)' }}>
                              {eng.engineerEmail}
                            </div>
                          </td>
                          <td style={{ textAlign: 'right', fontFamily: 'var(--font-family-mono)' }}>
                            {eng.assignedInPeriod}
                          </td>
                          <td style={{ textAlign: 'right', fontFamily: 'var(--font-family-mono)' }}>
                            {eng.resolvedInPeriod}
                          </td>
                          <td style={{ textAlign: 'right', fontFamily: 'var(--font-family-mono)' }}>
                            {eng.activeAssignedTickets}
                          </td>
                          <td style={{ textAlign: 'right' }}>
                            <Badge variant={eng.resolvedInPeriod > 0 ? 'success' : 'neutral'} size="sm">
                              {rate === 'N/A' ? 'N/A' : `${rate}%`}
                            </Badge>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </Card>

          {/* Top Deflected KB Articles */}
          <Card padded style={{ marginBottom: '1.5rem' }} className="reports-section">
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '1rem' }}>
              <HelpCircle size={18} style={{ color: 'var(--color-primary)' }} />
              <h2 className="reports-section-title" style={{ margin: 0, fontSize: 'var(--font-size-base)', fontWeight: 'var(--font-weight-semibold)' }}>
                Help Article Deflections
              </h2>
            </div>

            {report.kbDeflection.topArticles.length === 0 ? (
              <p className="reports-empty-state" style={{ color: 'var(--color-text-muted)', fontSize: 'var(--font-size-sm)', fontStyle: 'italic', margin: '1rem 0' }}>
                No article deflection events recorded yet.
              </p>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table className="horizon-data-table-simple reports-table">
                  <thead>
                    <tr>
                      <th>Article Title</th>
                      <th>Category</th>
                      <th style={{ textAlign: 'right' }}>Views</th>
                      <th style={{ textAlign: 'right' }}>Deflections</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.kbDeflection.topArticles.map((art) => (
                      <tr key={art.id}>
                        <td style={{ fontWeight: 'var(--font-weight-medium)', color: 'var(--color-text-primary)' }}>
                          {art.title}
                        </td>
                        <td>
                          <Badge variant="neutral" size="sm">
                            {art.categoryName || 'General'}
                          </Badge>
                        </td>
                        <td style={{ textAlign: 'right', fontFamily: 'var(--font-family-mono)', color: 'var(--color-text-secondary)' }}>
                          {art.viewCount}
                        </td>
                        <td style={{ textAlign: 'right', fontWeight: 'var(--font-weight-bold)', color: 'var(--color-primary)', fontFamily: 'var(--font-family-mono)' }}>
                          {art.deflectionCount}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>

          {/* CSV Export Actions */}
          <div className="horizon-export-bar reports-export-bar">
            <div className="horizon-export-info">
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <FileSpreadsheet size={18} style={{ color: 'var(--color-primary)' }} />
                <h3>Export Data</h3>
              </div>
              <p>
                Download report summaries or raw ticket details as spreadsheet-ready CSV files.
              </p>
            </div>
            <div className="horizon-export-buttons reports-export-buttons">
              <Button
                variant="secondary"
                size="md"
                className="reports-export-btn"
                disabled={isExportingSummary}
                isLoading={isExportingSummary}
                onClick={handleExportSummary}
                leadingIcon={<Download size={15} />}
              >
                Export Summary CSV
              </Button>
              <Button
                variant="primary"
                size="md"
                className="reports-export-btn"
                disabled={isExportingTickets}
                isLoading={isExportingTickets}
                onClick={handleExportTickets}
                leadingIcon={<Download size={15} />}
              >
                Export Raw Tickets CSV
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
