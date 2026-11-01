export interface EmployeeMetrics {
  openTickets: number
  resolvedTickets: number
  ticketsAwaitingResponse: number
  pendingApprovals: number | null
}

export interface SupportEngineerMetrics {
  assignedActiveTickets: number
  unassignedQueueTickets: number
  assignedSlaBreaches: number
}

export interface OperationsMetrics {
  totalActiveTickets: number
  unassignedQueueTickets: number
  pendingApprovals: number
  slaBreaches: number
}

export interface DashboardMetricsResponse {
  employee: EmployeeMetrics
  support: SupportEngineerMetrics | null
  operations: OperationsMetrics | null
}

