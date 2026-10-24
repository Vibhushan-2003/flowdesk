package com.flowdesk.ticket.dto;

import com.flowdesk.sla.domain.SlaStatus;

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
        OffsetDateTime updatedAt,

        OffsetDateTime responseDueAt,
        OffsetDateTime resolutionDueAt,
        OffsetDateTime firstRespondedAt,
        OffsetDateTime resolvedAt,

        String resolutionSummary,

        SlaStatus responseSlaStatus,
        SlaStatus resolutionSlaStatus,
        String rejectionReason

) {
    public TicketResponse(
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
            OffsetDateTime updatedAt,
            OffsetDateTime responseDueAt,
            OffsetDateTime resolutionDueAt,
            OffsetDateTime firstRespondedAt,
            OffsetDateTime resolvedAt,
            String resolutionSummary,
            SlaStatus responseSlaStatus,
            SlaStatus resolutionSlaStatus
    ) {
        this(
                id,
                ticketNumber,
                type,
                title,
                description,
                priority,
                status,
                createdByUserId,
                createdByEmail,
                createdAt,
                updatedAt,
                responseDueAt,
                resolutionDueAt,
                firstRespondedAt,
                resolvedAt,
                resolutionSummary,
                responseSlaStatus,
                resolutionSlaStatus,
                null
        );
    }
}