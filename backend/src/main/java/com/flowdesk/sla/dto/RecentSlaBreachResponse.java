package com.flowdesk.sla.dto;

import com.flowdesk.sla.domain.SlaEventType;
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;

import java.time.OffsetDateTime;

public record RecentSlaBreachResponse(
        String ticketNumber,
        SlaEventType eventType,
        TicketPriority priority,
        TicketStatus status,
        OffsetDateTime occurredAt
) {
}
