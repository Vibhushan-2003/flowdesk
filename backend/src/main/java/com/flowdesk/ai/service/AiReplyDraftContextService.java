package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiReplyDraftConversationMessage;
import com.flowdesk.ai.client.AiReplyDraftMessageRole;

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
public class AiReplyDraftContextService {

    private final TicketAssignmentRepository
            ticketAssignmentRepository;

    private final TicketCommentRepository
            ticketCommentRepository;

    public AiReplyDraftContextService(
            TicketAssignmentRepository ticketAssignmentRepository,
            TicketCommentRepository ticketCommentRepository
    ) {
        this.ticketAssignmentRepository =
                ticketAssignmentRepository;

        this.ticketCommentRepository =
                ticketCommentRepository;
    }

    @Transactional(readOnly = true)
    public AiReplyDraftGenerationContext
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

        ensureDraftingAllowed(
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

        UUID requesterId =
                requester.getId();

        List<AiReplyDraftConversationMessage>
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

        return new AiReplyDraftGenerationContext(
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

    private AiReplyDraftConversationMessage
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

        AiReplyDraftMessageRole role =
                author.getId()
                        .equals(
                                requesterId
                        )
                        ? AiReplyDraftMessageRole.REQUESTER
                        : AiReplyDraftMessageRole.SUPPORT;

        return new AiReplyDraftConversationMessage(
                role,
                comment.getBody()
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