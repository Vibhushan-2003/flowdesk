import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  downloadBlob,
  exportOperationalReportCsv,
  exportTicketsCsv,
  getOperationalReport,
} from '../api/reports'
import { useAuth } from '../auth/useAuth'
import type { OperationalReport } from '../types/reports'
import './ReportsPage.css'

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

  function handleCustomApply(e: React.FormEvent) {
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

  // Calculate highest count for trend bar scaling
  const maxTrend = report
    ? Math.max(1, ...report.dailyTrends.map((t) => Math.max(t.created, t.resolved)))
    : 1

  return (
    <div className="reports-container">
      <nav style={{ marginBottom: '1rem' }} aria-label="Breadcrumb">
        <Link to="/dashboard" style={{ color: '#2563eb', textDecoration: 'none', fontSize: '0.875rem' }}>
          ← Back to Dashboard
        </Link>
      </nav>

      <div className="reports-header">
        <h1>Operational Reporting & Analytics</h1>
        <p>Real-time aggregated ticket volume, resolution duration, SLA compliance, workload distribution, and Knowledge Base deflection.</p>
      </div>

      {/* Filter Toolbar */}
      <div className="reports-filter-bar" role="region" aria-label="Report Date Range Filters">
        <div className="reports-preset-buttons">
          <button
            type="button"
            className={`reports-preset-btn ${activePreset === '7d' ? 'active' : ''}`}
            onClick={() => handlePresetChange('7d')}
          >
            Last 7 Days
          </button>
          <button
            type="button"
            className={`reports-preset-btn ${activePreset === '30d' ? 'active' : ''}`}
            onClick={() => handlePresetChange('30d')}
          >
            Last 30 Days
          </button>
          <button
            type="button"
            className={`reports-preset-btn ${activePreset === '90d' ? 'active' : ''}`}
            onClick={() => handlePresetChange('90d')}
          >
            Last 90 Days
          </button>
        </div>

        <form className="reports-custom-dates" onSubmit={handleCustomApply}>
          <label htmlFor="report-start-date">From</label>
          <input
            id="report-start-date"
            type="date"
            value={startDateInput}
            onChange={(e) => setStartDateInput(e.target.value)}
            required
          />
          <label htmlFor="report-end-date">To</label>
          <input
            id="report-end-date"
            type="date"
            value={endDateInput}
            onChange={(e) => setEndDateInput(e.target.value)}
            required
          />
          <button type="submit" className="reports-apply-btn">
            Apply
          </button>
        </form>
      </div>

      {error && (
        <div style={{ padding: '1rem', background: '#fee2e2', border: '1px solid #f87171', borderRadius: '0.375rem', marginBottom: '1.5rem', color: '#991b1b' }} role="alert">
          {error}
        </div>
      )}

      {isLoading && (
        <div style={{ textAlign: 'center', padding: '3rem 0', color: '#64748b' }} role="status">
          Loading operational metrics...
        </div>
      )}

      {!isLoading && report && (
        <>
          {/* KPI Summary Cards */}
          <div className="reports-kpi-grid">
            <div className="reports-kpi-card">
              <span className="reports-kpi-title">Created in Period</span>
              <span className="reports-kpi-value">{report.ticketVolume.totalCreated}</span>
              <p className="reports-kpi-subtext">Total tickets submitted</p>
            </div>
            <div className="reports-kpi-card">
              <span className="reports-kpi-title">Resolved in Period</span>
              <span className="reports-kpi-value">{report.ticketVolume.totalResolved}</span>
              <p className="reports-kpi-subtext">Completed or closed tickets</p>
            </div>
            <div className="reports-kpi-card">
              <span className="reports-kpi-title">Avg Resolution Time</span>
              <span className="reports-kpi-value">
                {report.resolutionMetrics.avgDurationHours != null
                  ? `${report.resolutionMetrics.avgDurationHours.toFixed(1)}h`
                  : 'N/A'}
              </span>
              <p className="reports-kpi-subtext">
                {report.resolutionMetrics.resolvedCount} resolved tickets
              </p>
            </div>
            <div className="reports-kpi-card">
              <span className="reports-kpi-title">SLA Compliance Rate</span>
              <span className="reports-kpi-value">
                {report.slaCompliance.complianceRatePercent != null
                  ? `${report.slaCompliance.complianceRatePercent.toFixed(1)}%`
                  : '100%'}
              </span>
              <p className="reports-kpi-subtext">
                {report.slaCompliance.compliantResolved} compliant of {report.slaCompliance.totalResolved} resolved
              </p>
            </div>
            <div className="reports-kpi-card">
              <span className="reports-kpi-title">KB Deflections</span>
              <span className="reports-kpi-value">{report.kbDeflection.totalDeflections}</span>
              <p className="reports-kpi-subtext">
                {report.kbDeflection.deflectionRatePercent.toFixed(1)}% self-service deflection rate
              </p>
            </div>
          </div>

          {/* Daily Trends Chart */}
          <div className="reports-section">
            <h2 className="reports-section-title">Daily Ticket Activity Trend</h2>
            {report.dailyTrends.length === 0 ? (
              <p className="reports-empty-state">No ticket activity recorded in this period.</p>
            ) : (
              <div className="reports-chart-container">
                <div className="reports-chart-legend">
                  <div className="reports-legend-item">
                    <span className="reports-legend-color created" />
                    <span>Created</span>
                  </div>
                  <div className="reports-legend-item">
                    <span className="reports-legend-color resolved" />
                    <span>Resolved</span>
                  </div>
                </div>
                <div className="reports-chart-bars">
                  {report.dailyTrends.map((trend) => (
                    <div key={trend.date} className="reports-bar-group">
                      <div className="reports-bars-wrapper">
                        <div
                          className="reports-bar created"
                          style={{ height: `${(trend.created / maxTrend) * 100}%` }}
                          title={`Created: ${trend.created}`}
                        />
                        <div
                          className="reports-bar resolved"
                          style={{ height: `${(trend.resolved / maxTrend) * 100}%` }}
                          title={`Resolved: ${trend.resolved}`}
                        />
                      </div>
                      <span className="reports-bar-label">{trend.date.slice(5)}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Breakdowns Grid */}
          <div className="reports-grid-2">
            {/* Status Breakdown */}
            <div className="reports-section">
              <h2 className="reports-section-title">Status Breakdown</h2>
              <table className="reports-table">
                <thead>
                  <tr>
                    <th>Status</th>
                    <th style={{ textAlign: 'right' }}>Count</th>
                  </tr>
                </thead>
                <tbody>
                  {Object.entries(report.volumeByStatus).map(([status, count]) => (
                    <tr key={status}>
                      <td><strong>{status.replace('_', ' ')}</strong></td>
                      <td style={{ textAlign: 'right' }}>{count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Priority Breakdown */}
            <div className="reports-section">
              <h2 className="reports-section-title">Priority Breakdown</h2>
              <table className="reports-table">
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
                        <span className={`reports-priority-badge ${priority.toLowerCase()}`}>
                          {priority}
                        </span>
                      </td>
                      <td style={{ textAlign: 'right' }}>{count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Support Engineer Workload */}
          <div className="reports-section">
            <h2 className="reports-section-title">Support Engineer Workload & Performance</h2>
            {report.engineerWorkloads.length === 0 ? (
              <p className="reports-empty-state">No assigned tickets found for this period.</p>
            ) : (
              <table className="reports-table">
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
                          <div style={{ fontWeight: 600 }}>{eng.engineerName}</div>
                          <div style={{ fontSize: '0.75rem', color: '#64748b' }}>{eng.engineerEmail}</div>
                        </td>
                        <td style={{ textAlign: 'right' }}>{eng.assignedInPeriod}</td>
                        <td style={{ textAlign: 'right' }}>{eng.resolvedInPeriod}</td>
                        <td style={{ textAlign: 'right' }}>{eng.activeAssignedTickets}</td>
                        <td style={{ textAlign: 'right' }}>
                          <span style={{ fontWeight: 600, color: eng.resolvedInPeriod > 0 ? '#16a34a' : '#64748b' }}>
                            {rate === 'N/A' ? 'N/A' : `${rate}%`}
                          </span>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            )}
          </div>

          {/* Top Deflected KB Articles */}
          <div className="reports-section">
            <h2 className="reports-section-title">Self-Service Knowledge Base Impact</h2>
            {report.kbDeflection.topArticles.length === 0 ? (
              <p className="reports-empty-state">No article deflection events recorded yet.</p>
            ) : (
              <table className="reports-table">
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
                      <td style={{ fontWeight: 500 }}>{art.title}</td>
                      <td>
                        <span style={{ fontSize: '0.75rem', padding: '0.125rem 0.5rem', background: '#f1f5f9', borderRadius: '0.25rem' }}>
                          {art.categoryName || 'General'}
                        </span>
                      </td>
                      <td style={{ textAlign: 'right' }}>{art.viewCount}</td>
                      <td style={{ textAlign: 'right', fontWeight: 600, color: '#2563eb' }}>
                        {art.deflectionCount}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          {/* CSV Export Actions */}
          <div className="reports-export-bar">
            <div>
              <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 600 }}>Export Data</h3>
              <p style={{ margin: '0.25rem 0 0 0', fontSize: '0.8125rem', color: '#64748b' }}>
                Download sanitized, RFC 4180 compliant CSV files with formula-injection protection.
              </p>
            </div>
            <div className="reports-export-buttons">
              <button
                type="button"
                className="reports-export-btn"
                disabled={isExportingSummary}
                onClick={handleExportSummary}
              >
                {isExportingSummary ? 'Exporting...' : 'Export Summary CSV'}
              </button>
              <button
                type="button"
                className="reports-export-btn"
                disabled={isExportingTickets}
                onClick={handleExportTickets}
              >
                {isExportingTickets ? 'Exporting...' : 'Export Raw Tickets CSV'}
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
