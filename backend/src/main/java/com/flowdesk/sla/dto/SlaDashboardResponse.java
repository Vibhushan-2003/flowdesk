package com.flowdesk.sla.dto;

import com.flowdesk.ticket.domain.TicketPriority;

import java.util.List;
import java.util.Map;

public record SlaDashboardResponse(
        long activeTickets,
        long responseBreached,
        long resolutionBreached,
        Double responseCompliancePercent,
        Double resolutionCompliancePercent,
        Map<TicketPriority, Long> breachedByPriority,
        List<RecentSlaBreachResponse> recentBreaches
) {
}
