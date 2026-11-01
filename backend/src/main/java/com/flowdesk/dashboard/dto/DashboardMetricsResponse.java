package com.flowdesk.dashboard.dto;

public record DashboardMetricsResponse(
        EmployeeMetrics employee,
        SupportEngineerMetrics support,
        OperationsMetrics operations
) {
}

