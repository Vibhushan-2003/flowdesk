package com.flowdesk.ai.tool;

import com.flowdesk.sla.domain.SlaStatus;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketComment;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketCommentRepository;

import com.flowdesk.user.domain.User;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.time.temporal.ChronoUnit;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class AiSupportCopilotToolService {

    private final TicketAssignmentRepository
            ticketAssignmentRepository;

    private final TicketCommentRepository
            ticketCommentRepository;

    public AiSupportCopilotToolService(
            TicketAssignmentRepository ticketAssignmentRepository,
            TicketCommentRepository ticketCommentRepository
    ) {
        this.ticketAssignmentRepository =
                ticketAssignmentRepository;

        this.ticketCommentRepository =
                ticketCommentRepository;
    }

    /*
     * READ-ONLY TOOL 1
     *
     * Returns only the minimum ticket information
     * required by the AI for reasoning.
     *
     * It deliberately excludes:
     * - ticket UUID
     * - requester identity
     * - support engineer identity
     * - assignment id
     * - audit data
     * - SLA policy internals
     */
    @Transactional(readOnly = true)
    public AiSupportCopilotTicketOverview
            getTicketOverview(
                    UUID authenticatedUserId,
                    String ticketNumber
            ) {

        TicketAssignment assignment =
                findActiveAssignment(
                        authenticatedUserId,
                        ticketNumber
                );

        Ticket ticket =
                assignment.getTicket();

        return new AiSupportCopilotTicketOverview(
                ticket.getType(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getTitle(),
                ticket.getDescription()
        );
    }

    /*
     * READ-ONLY TOOL 2
     *
     * Returns the public ticket conversation
     * without exposing user identity.
     *
     * Authors are reduced to only:
     * - REQUESTER
     * - SUPPORT
     */
    @Transactional(readOnly = true)
    public List<AiSupportCopilotConversationMessage>
            getPublicConversation(
                    UUID authenticatedUserId,
                    String ticketNumber
            ) {

        TicketAssignment assignment =
                findActiveAssignment(
                        authenticatedUserId,
                        ticketNumber
                );

        Ticket ticket =
                assignment.getTicket();

        User requester =
                ticket.getCreatedByUser();

        if (requester == null
                || requester.getId() == null) {

            throw new IllegalStateException(
                    "Ticket requester is missing"
            );
        }

        UUID requesterId =
                requester.getId();

        return ticketCommentRepository
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
    }

    /*
     * READ-ONLY TOOL 3
     *
     * Returns derived SLA information rather than
     * exposing the SLA entity or internal policy data.
     */
    @Transactional(readOnly = true)
    public AiSupportCopilotSlaSnapshot
            getSlaSnapshot(
                    UUID authenticatedUserId,
                    String ticketNumber
            ) {

        TicketAssignment assignment =
                findActiveAssignment(
                        authenticatedUserId,
                        ticketNumber
                );

        Ticket ticket =
                assignment.getTicket();

        OffsetDateTime evaluatedAt =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );

        SlaStatus responseStatus =
                ticket.evaluateResponseSla(
                        evaluatedAt
                );

        SlaStatus resolutionStatus =
                ticket.evaluateResolutionSla(
                        evaluatedAt
                );

        Long responseMinutesUntilDue =
                calculateMinutesUntilDue(
                        responseStatus,
                        evaluatedAt,
                        ticket.getResponseDueAt()
                );

        Long resolutionMinutesUntilDue =
                calculateMinutesUntilDue(
                        resolutionStatus,
                        evaluatedAt,
                        ticket.getResolutionDueAt()
                );

        return new AiSupportCopilotSlaSnapshot(
                responseStatus,
                resolutionStatus,
                responseMinutesUntilDue,
                resolutionMinutesUntilDue
        );
    }

    /*
     * Authorization is enforced every time a tool
     * executes.
     *
     * This matters because an AI request may take
     * several seconds.
     *
     * If the assignment changes while Gemini is
     * reasoning, a later tool call must not continue
     * reading the ticket for the previous engineer.
     */
    private TicketAssignment
            findActiveAssignment(
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

        return ticketAssignmentRepository
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
    }

    private AiSupportCopilotConversationMessage
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

        AiSupportCopilotConversationMessage.Role role =
                author.getId()
                        .equals(
                                requesterId
                        )
                        ? AiSupportCopilotConversationMessage
                                .Role.REQUESTER
                        : AiSupportCopilotConversationMessage
                                .Role.SUPPORT;

        return new AiSupportCopilotConversationMessage(
                role,
                comment.getBody()
        );
    }

    /*
     * Positive value:
     *     minutes remain.
     *
     * Negative value:
     *     deadline has been exceeded.
     *
     * null:
     *     SLA is already satisfied or the deadline
     *     is not meaningful/known.
     */
    private Long calculateMinutesUntilDue(
            SlaStatus status,
            OffsetDateTime evaluatedAt,
            OffsetDateTime dueAt
    ) {

        if (dueAt == null
                || status == SlaStatus.MET
                || status == SlaStatus.UNKNOWN) {

            return null;
        }

        return ChronoUnit.MINUTES.between(
                evaluatedAt,
                dueAt
        );
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