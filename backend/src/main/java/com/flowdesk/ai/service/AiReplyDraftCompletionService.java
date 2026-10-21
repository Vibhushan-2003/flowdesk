package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiReplyDraftClientResult;

import com.flowdesk.ai.dto.AiReplyDraftResponse;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;

import com.flowdesk.audit.service.AuditService;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketStatus;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;

import com.flowdesk.user.domain.User;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class AiReplyDraftCompletionService {

    private final TicketAssignmentRepository
            ticketAssignmentRepository;

    private final AuditService
            auditService;

    public AiReplyDraftCompletionService(
            TicketAssignmentRepository ticketAssignmentRepository,
            AuditService auditService
    ) {
        this.ticketAssignmentRepository =
                ticketAssignmentRepository;

        this.auditService =
                auditService;
    }

    @Transactional
    public AiReplyDraftResponse
            completeGeneration(
                    UUID authenticatedUserId,
                    UUID ticketId,
                    AiReplyDraftClientResult result,
                    String promptVersion
            ) {

        Objects.requireNonNull(
                authenticatedUserId,
                "Authenticated user id is required"
        );

        Objects.requireNonNull(
                ticketId,
                "Ticket id is required"
        );

        Objects.requireNonNull(
                result,
                "AI reply draft result is required"
        );

        String normalizedPromptVersion =
                normalizeRequiredText(
                        promptVersion,
                        "Prompt version is required"
                );

        /*
         * The AI call may have taken several seconds.
         *
         * Therefore we must not trust the assignment
         * check performed before the network call.
         *
         * Re-check the current active assignment now.
         */
        TicketAssignment assignment =
                ticketAssignmentRepository
                        .findByTicket_IdAndReleasedAtIsNull(
                                ticketId
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Ticket is no longer actively assigned"
                                        )
                        );

        User engineer =
                assignment.getAssignedToUser();

        if (engineer == null
                || engineer.getId() == null
                || !engineer.getId()
                        .equals(
                                authenticatedUserId
                        )) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Ticket is no longer actively assigned to you"
            );
        }

        Ticket ticket =
                assignment.getTicket();

        ensureDraftingAllowed(
                ticket
        );

        OffsetDateTime generatedAt =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );

        /*
         * Audit only controlled metadata.
         *
         * Deliberately DO NOT store:
         * - generated draft
         * - ticket title
         * - ticket description
         * - conversation
         * - prompt
         */
        auditService.recordUserAction(
                engineer,
                AuditAction.AI_REPLY_DRAFT_GENERATED,
                AuditTargetType.TICKET,
                ticket.getId(),
                ticket.getTicketNumber(),
                Map.of(
                        "provider",
                        result.provider(),

                        "model",
                        result.model(),

                        "promptVersion",
                        normalizedPromptVersion
                )
        );

        /*
         * The draft itself is NOT persisted.
         *
         * It exists only in this API response and later
         * in the editable frontend textarea.
         */
        return new AiReplyDraftResponse(
                ticket.getTicketNumber(),
                result.draft(),
                result.provider(),
                result.model(),
                generatedAt
        );
    }

    private void ensureDraftingAllowed(
            Ticket ticket
    ) {

        TicketStatus status =
                ticket.getStatus();

        if (status == TicketStatus.RESOLVED
                || status == TicketStatus.CLOSED
                || status == TicketStatus.CANCELLED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "AI reply drafts cannot be generated for a finished ticket"
            );
        }
    }

    private String normalizeRequiredText(
            String value,
            String requiredMessage
    ) {

        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    requiredMessage
            );
        }

        return value.trim();
    }
}