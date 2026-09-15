package com.flowdesk.ticket.dto;

import com.flowdesk.ticket.domain.TicketStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ClaimTicketResponse(
        UUID assignmentId,
        UUID ticketId,
        String ticketNumber,
        TicketStatus status,
        UUID assignedToUserId,
        String assignedToEmail,
        OffsetDateTime assignedAt
) {
}