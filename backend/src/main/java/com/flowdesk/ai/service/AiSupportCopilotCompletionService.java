package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiSupportCopilotClientResult;

import com.flowdesk.ai.dto.AiSupportCopilotResponse;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;

import com.flowdesk.audit.service.AuditService;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;

import com.flowdesk.user.domain.User;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class AiSupportCopilotCompletionService {

    private final TicketAssignmentRepository
            ticketAssignmentRepository;

    private final AuditService
            auditService;

    public AiSupportCopilotCompletionService(
            TicketAssignmentRepository ticketAssignmentRepository,
            AuditService auditService
    ) {
        this.ticketAssignmentRepository =
                ticketAssignmentRepository;

        this.auditService =
                auditService;
    }

    @Transactional
    public AiSupportCopilotResponse
            completeRun(
                    UUID authenticatedUserId,
                    UUID ticketId,
                    AiSupportCopilotClientResult result,
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
                "AI Copilot result is required"
        );

        String normalizedPromptVersion =
                normalizeRequiredText(
                        promptVersion,
                        "Prompt version is required"
                );

        /*
         * Final authorization check.
         *
         * The external AI run may have taken several
         * seconds and may have included multiple
         * read-only tool calls.
         *
         * Therefore we must not trust any earlier
         * assignment check.
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

        if (ticket == null
                || ticket.getId() == null) {

            throw new IllegalStateException(
                    "Assigned ticket is missing"
            );
        }

        OffsetDateTime generatedAt =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );

        /*
         * Convert enum values to controlled strings
         * before storing audit metadata.
         */
        List<String> toolsUsed =
                result
                        .toolsUsed()
                        .stream()
                        .map(Enum::name)
                        .toList();

        /*
         * Audit only controlled metadata.
         *
         * Never persist:
         * - engineer question
         * - AI answer
         * - tool results
         * - ticket description
         * - conversation
         * - prompt
         * - identities
         * - API credentials
         */
        auditService.recordUserAction(
                engineer,
                AuditAction.AI_SUPPORT_COPILOT_USED,
                AuditTargetType.TICKET,
                ticket.getId(),
                ticket.getTicketNumber(),
                Map.of(
                        "provider",
                        result.provider(),

                        "model",
                        result.model(),

                        "promptVersion",
                        normalizedPromptVersion,

                        "toolsUsed",
                        toolsUsed
                )
        );

        /*
         * The Copilot answer is temporary.
         *
         * It is returned to the frontend but is not
         * persisted as ticket history.
         */
        return new AiSupportCopilotResponse(
                ticket.getTicketNumber(),
                result.answer(),
                result.toolsUsed(),
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