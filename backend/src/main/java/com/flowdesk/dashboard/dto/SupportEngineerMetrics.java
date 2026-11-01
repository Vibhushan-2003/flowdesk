package com.flowdesk.dashboard.dto;

public record SupportEngineerMetrics(
        long assignedActiveTickets,
        long unassignedQueueTickets,
        long assignedSlaBreaches
) {
}

