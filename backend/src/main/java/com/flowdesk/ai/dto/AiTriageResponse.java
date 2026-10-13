package com.flowdesk.ai.dto;

import com.flowdesk.ticket.domain.TicketPriority;

import java.math.BigDecimal;

import java.time.OffsetDateTime;

import java.util.UUID;

public record AiTriageResponse(

        UUID recommendationId,

        UUID ticketId,
        String ticketNumber,

        TicketPriority suggestedPriority,
        BigDecimal confidence,
        String reason,

        String provider,
        String model,
        String promptVersion,

        UUID generatedByUserId,

        OffsetDateTime createdAt

) {
}