package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiResolutionSummaryClientResult;

import com.flowdesk.ai.dto.AiResolutionSummaryResponse;

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
public class AiResolutionSummaryCompletionService {

    private final TicketAssignmentRepository
            ticketAssignmentRepository;

    private final AuditService
            auditService;

    public AiResolutionSummaryCompletionService(
            TicketAssignmentRepository ticketAssignmentRepository,
            AuditService auditService
    ) {
        this.ticketAssignmentRepository =
                ticketAssignmentRepository;

        this.auditService =
                auditService;
    }

    @Transactional
    public AiResolutionSummaryResponse
            completeGeneration(
                    UUID authenticatedUserId,
                    UUID ticketId,
                    AiResolutionSummaryClientResult result,
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
                "AI resolution summary result is required"
        );

        String normalizedPromptVersion =
                normalizeRequiredText(
                        promptVersion,
                        "Prompt version is required"
                );

        /*
         * The provider call happened outside this
         * transaction and may have taken several
         * seconds.
         *
         * Therefore the assignment/status from the
         * original context must not be trusted.
         *
         * Lock the current active assignment briefly
         * and validate the latest state.
         */
        TicketAssignment assignment =
                ticketAssignmentRepository
                        .findActiveAssignmentByTicketIdForUpdate(
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

        /*
         * A resolution-summary draft is valid only
         * while the ticket is still IN_PROGRESS.
         *
         * If its state changed while Gemini was
         * generating, discard the generated result.
         */
        if (ticket.getStatus()
                != TicketStatus.IN_PROGRESS) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "AI resolution summary draft is no longer valid because ticket status changed"
            );
        }

        OffsetDateTime generatedAt =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );

        /*
         * Audit only controlled metadata.
         *
         * Never store:
         * - generated draft
         * - ticket description
         * - conversation
         * - requester details
         * - prompt
         * - API key
         */
        auditService.recordUserAction(
                engineer,
                AuditAction
                        .AI_RESOLUTION_SUMMARY_DRAFT_GENERATED,
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
         * The AI draft itself is NOT persisted.
         *
         * The frontend will receive it as temporary
         * editable text.
         *
         * Only the later explicit /resolve operation
         * persists the human-approved summary.
         */
        return new AiResolutionSummaryResponse(
                ticket.getTicketNumber(),
                result.draft(),
                result.provider(),
                result.model(),
                generatedAt
        );
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