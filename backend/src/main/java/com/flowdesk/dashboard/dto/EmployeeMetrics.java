package com.flowdesk.dashboard.dto;

public record EmployeeMetrics(
        long openTickets,
        long resolvedTickets,
        long ticketsAwaitingResponse,
        Long pendingApprovals
) {
}

