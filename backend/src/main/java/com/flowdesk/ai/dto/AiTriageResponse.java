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

        OffsetDateTime createdAt,

        boolean applied,

        UUID appliedByUserId,

        OffsetDateTime appliedAt

) {

    /*
     * Backward-compatible constructor.
     *
     * Existing Day 25 tests and code that create an
     * unapplied recommendation response can continue
     * using the original constructor shape.
     */
    public AiTriageResponse(

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
        this(
                recommendationId,
                ticketId,
                ticketNumber,
                suggestedPriority,
                confidence,
                reason,
                provider,
                model,
                promptVersion,
                generatedByUserId,
                createdAt,
                false,
                null,
                null
        );
    }
}