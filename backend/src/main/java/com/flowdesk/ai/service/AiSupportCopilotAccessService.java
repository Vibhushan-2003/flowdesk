package com.flowdesk.ai.service;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class AiSupportCopilotAccessService {

    private final TicketAssignmentRepository
            ticketAssignmentRepository;

    public AiSupportCopilotAccessService(
            TicketAssignmentRepository ticketAssignmentRepository
    ) {
        this.ticketAssignmentRepository =
                ticketAssignmentRepository;
    }

    /*
     * Initial authorization check before calling
     * the external AI provider.
     *
     * This prevents an unauthorized request from
     * consuming an AI call at all.
     */
    @Transactional(readOnly = true)
    public AiSupportCopilotRunContext
            prepareRun(
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

        if (ticket == null
                || ticket.getId() == null) {

            throw new IllegalStateException(
                    "Assigned ticket is missing"
            );
        }

        return new AiSupportCopilotRunContext(
                ticket.getId(),
                normalizedTicketNumber
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