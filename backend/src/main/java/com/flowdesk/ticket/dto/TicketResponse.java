package com.flowdesk.ticket.dto;

import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        String ticketNumber,
        TicketType type,
        String title,
        String description,
        TicketPriority priority,
        TicketStatus status,
        UUID createdByUserId,
        String createdByEmail,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}