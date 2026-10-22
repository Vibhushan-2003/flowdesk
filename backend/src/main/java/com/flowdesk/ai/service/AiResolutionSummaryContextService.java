package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiResolutionSummaryConversationMessage;
import com.flowdesk.ai.client.AiResolutionSummaryMessageRole;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketComment;
import com.flowdesk.ticket.domain.TicketStatus;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketCommentRepository;

import com.flowdesk.user.domain.User;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class AiResolutionSummaryContextService {

    private final TicketAssignmentRepository
            ticketAssignmentRepository;

    private final TicketCommentRepository
            ticketCommentRepository;

    public AiResolutionSummaryContextService(
            TicketAssignmentRepository ticketAssignmentRepository,
            TicketCommentRepository ticketCommentRepository
    ) {
        this.ticketAssignmentRepository =
                ticketAssignmentRepository;

        this.ticketCommentRepository =
                ticketCommentRepository;
    }

    @Transactional(readOnly = true)
    public AiResolutionSummaryGenerationContext
            prepareGeneration(
                    UUID authenticatedUserId,
                    String ticketNumber
            ) {

        Objects.requireNonNull(
                authenticatedUserId,
                "Authenticated user id is required"
        );

        String normalizedTicketNumber =
                normalizeTicketNumber(
                        ticketNumber
                );

        /*
         * Only the currently assigned support engineer
         * may generate a resolution-summary draft.
         *
         * A different engineer receives 404 rather than
         * revealing whether the ticket exists.
         */
        TicketAssignment assignment =
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                normalizedTicketNumber,
                                authenticatedUserId
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Ticket not found or it is no longer assigned to you"
                                        )
                        );

        Ticket ticket =
                assignment.getTicket();

        ensureGenerationAllowed(
                ticket
        );

        User requester =
                ticket.getCreatedByUser();

        if (requester == null
                || requester.getId() == null) {

            throw new IllegalStateException(
                    "Ticket requester is missing"
            );
        }

        /*
         * The requester UUID is used only locally to
         * classify conversation roles.
         *
         * It is not placed in the AI-facing context.
         */
        UUID requesterId =
                requester.getId();

        List<AiResolutionSummaryConversationMessage>
                conversation =
                ticketCommentRepository
                        .findByTicket_IdOrderByCreatedAtAsc(
                                ticket.getId()
                        )
                        .stream()
                        .map(
                                comment ->
                                        toConversationMessage(
                                                comment,
                                                requesterId
                                        )
                        )
                        .toList();

        /*
         * Deliberately excluded:
         *
         * - requester email
         * - requester UUID
         * - support engineer UUID
         * - assignment id
         * - SLA timestamps
         * - resolvedAt
         * - audit log
         * - AI triage metadata
         * - JWT
         * - API key
         *
         * ticketId remains internal only for the
         * post-provider concurrency re-check.
         */
        return new AiResolutionSummaryGenerationContext(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getType(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getTitle(),
                ticket.getDescription(),
                conversation
        );
    }

    private AiResolutionSummaryConversationMessage
            toConversationMessage(
                    TicketComment comment,
                    UUID requesterId
            ) {

        User author =
                comment.getAuthorUser();

        if (author == null
                || author.getId() == null) {

            throw new IllegalStateException(
                    "Comment author is missing"
            );
        }

        AiResolutionSummaryMessageRole role =
                author.getId()
                        .equals(
                                requesterId
                        )
                        ? AiResolutionSummaryMessageRole.REQUESTER
                        : AiResolutionSummaryMessageRole.SUPPORT;

        return new AiResolutionSummaryConversationMessage(
                role,
                comment.getBody()
        );
    }

    private void ensureGenerationAllowed(
            Ticket ticket
    ) {

        if (ticket.getStatus()
                != TicketStatus.IN_PROGRESS) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "AI resolution summary drafts can only be generated while ticket is in progress"
            );
        }
    }

    private String normalizeTicketNumber(
            String ticketNumber
    ) {

        if (ticketNumber == null
                || ticketNumber.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ticket number is required"
            );
        }

        return ticketNumber
                .trim()
                .toUpperCase(
                        Locale.ROOT
                );
    }
}