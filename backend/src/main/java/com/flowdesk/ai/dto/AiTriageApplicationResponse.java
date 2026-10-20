package com.flowdesk.ai.dto;

import com.flowdesk.ticket.domain.TicketPriority;

import java.time.OffsetDateTime;

import java.util.UUID;

public record AiTriageApplicationResponse(

        UUID recommendationId,

        UUID ticketId,

        String ticketNumber,

        TicketPriority previousPriority,

        TicketPriority appliedPriority,

        OffsetDateTime previousResponseDueAt,

        OffsetDateTime responseDueAt,

        OffsetDateTime previousResolutionDueAt,

        OffsetDateTime resolutionDueAt,

        UUID appliedByUserId,

        OffsetDateTime appliedAt

) {
}