package com.flowdesk.approval.dto;

import com.flowdesk.approval.domain.ApprovalStatus;

import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;

import java.time.OffsetDateTime;

import java.util.UUID;

public record ApprovalResponse(

        UUID id,

        UUID ticketId,

        String ticketNumber,

        TicketType ticketType,

        TicketPriority priority,

        TicketStatus ticketStatus,

        UUID requestedByUserId,

        String requestedByEmail,

        ApprovalStatus status,

        UUID decidedByUserId,

        String decidedByEmail,

        String decisionNote,

        OffsetDateTime requestedAt,

        OffsetDateTime decidedAt,

        OffsetDateTime responseDueAt,

        OffsetDateTime resolutionDueAt
) {
}