package com.flowdesk.dashboard.dto;

public record OperationsMetrics(
        long totalActiveTickets,
        long unassignedQueueTickets,
        long pendingApprovals,
        long slaBreaches
) {
}

