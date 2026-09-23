package com.flowdesk.ticket.dto;

import com.flowdesk.sla.domain.SlaStatus;

import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SupportTicketSummaryResponse(

        UUID ticketId,
        String ticketNumber,
        TicketType type,
        String title,
        TicketPriority priority,
        TicketStatus status,

        OffsetDateTime createdAt,
        OffsetDateTime assignedAt,

        OffsetDateTime responseDueAt,
        OffsetDateTime resolutionDueAt,

        SlaStatus responseSlaStatus,
        SlaStatus resolutionSlaStatus

) {
}
