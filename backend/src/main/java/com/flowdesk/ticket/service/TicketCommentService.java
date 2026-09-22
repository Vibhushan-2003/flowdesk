package com.flowdesk.ticket.service;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketComment;
import com.flowdesk.ticket.domain.TicketStatus;

import com.flowdesk.ticket.dto.CreateTicketCommentRequest;
import com.flowdesk.ticket.dto.TicketCommentResponse;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketCommentRepository;
import com.flowdesk.ticket.repository.TicketRepository;

import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TicketCommentService {

    private final TicketCommentRepository
            ticketCommentRepository;

    private final TicketRepository
            ticketRepository;

    private final TicketAssignmentRepository
            ticketAssignmentRepository;

    private final UserRepository
            userRepository;

    public TicketCommentService(
            TicketCommentRepository ticketCommentRepository,
            TicketRepository ticketRepository,
            TicketAssignmentRepository ticketAssignmentRepository,
            UserRepository userRepository
    ) {
        this.ticketCommentRepository =
                ticketCommentRepository;

        this.ticketRepository =
                ticketRepository;

        this.ticketAssignmentRepository =
                ticketAssignmentRepository;

        this.userRepository =
                userRepository;
    }

    /*
     * EMPLOYEE
     *
     * Can read comments only on a ticket
     * created by that employee.
     */
    @Transactional(readOnly = true)
    public List<TicketCommentResponse>
            getEmployeeComments(
                    UUID employeeId,
                    String ticketNumber
            ) {

        Ticket ticket =
                findEmployeeOwnedTicket(
                        employeeId,
                        ticketNumber
                );

        return getComments(ticket);
    }

    /*
     * EMPLOYEE
     *
     * Can add a comment only to a ticket
     * created by that employee.
     */
    @Transactional
    public TicketCommentResponse
            addEmployeeComment(
                    UUID employeeId,
                    String ticketNumber,
                    CreateTicketCommentRequest request
            ) {

        Ticket ticket =
                findEmployeeOwnedTicket(
                        employeeId,
                        ticketNumber
                );

        ensureCommentingAllowed(ticket);

        User author =
                findUser(employeeId);

        return createComment(
                ticket,
                author,
                request
        );
    }

    /*
     * SUPPORT ENGINEER
     *
     * Can read comments only while they have
     * the active assignment for the ticket.
     */
    @Transactional(readOnly = true)
    public List<TicketCommentResponse>
            getSupportComments(
                    UUID supportEngineerId,
                    String ticketNumber
            ) {

        TicketAssignment assignment =
                findActiveSupportAssignment(
                        supportEngineerId,
                        ticketNumber
                );

        return getComments(
                assignment.getTicket()
        );
    }

    /*
     * SUPPORT ENGINEER
     *
     * Can add comments only while they have
     * the active assignment.
     */
    @Transactional
    public TicketCommentResponse
            addSupportComment(
                    UUID supportEngineerId,
                    String ticketNumber,
                    CreateTicketCommentRequest request
            ) {

        TicketAssignment assignment =
                findActiveSupportAssignment(
                        supportEngineerId,
                        ticketNumber
                );

        Ticket ticket =
                assignment.getTicket();

        ensureCommentingAllowed(ticket);

        User author =
                findUser(supportEngineerId);

        return createComment(
                ticket,
                author,
                request
        );
    }

    private Ticket findEmployeeOwnedTicket(
            UUID employeeId,
            String ticketNumber
    ) {

        String normalizedTicketNumber =
                normalizeTicketNumber(ticketNumber);

        return ticketRepository
                .findByTicketNumberAndCreatedByUser_Id(
                        normalizedTicketNumber,
                        employeeId
                )
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Ticket not found"
                                )
                );
    }

    private TicketAssignment
            findActiveSupportAssignment(
                    UUID supportEngineerId,
                    String ticketNumber
            ) {

        String normalizedTicketNumber =
                normalizeTicketNumber(ticketNumber);

        return ticketAssignmentRepository
                .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                        normalizedTicketNumber,
                        supportEngineerId
                )
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Ticket not found"
                                )
                );
    }

    private User findUser(UUID userId) {

        return userRepository
                .findById(userId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "User not found"
                                )
                );
    }

    private List<TicketCommentResponse>
            getComments(Ticket ticket) {

        return ticketCommentRepository
                .findByTicket_IdOrderByCreatedAtAsc(
                        ticket.getId()
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private TicketCommentResponse createComment(
            Ticket ticket,
            User author,
            CreateTicketCommentRequest request
    ) {

        validateRequest(request);

        TicketComment comment;

        try {
            comment = new TicketComment(
                    ticket,
                    author,
                    request.body()
            );
        }
        catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage()
            );
        }

        TicketComment savedComment =
                ticketCommentRepository
                        .saveAndFlush(comment);

        return toResponse(savedComment);
    }

    private void validateRequest(
            CreateTicketCommentRequest request
    ) {

        if (request == null
                || request.body() == null
                || request.body().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Comment body is required"
            );
        }

        if (request.body().trim().length() > 4000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Comment body must not exceed 4000 characters"
            );
        }
    }

    private void ensureCommentingAllowed(
            Ticket ticket
    ) {

        TicketStatus status =
                ticket.getStatus();

        if (status == TicketStatus.RESOLVED
                || status == TicketStatus.CLOSED
                || status == TicketStatus.CANCELLED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Comments cannot be added to a finished ticket"
            );
        }
    }

    private TicketCommentResponse toResponse(
            TicketComment comment
    ) {

        User author =
                comment.getAuthorUser();

        String authorName =
                (
                        author.getFirstName()
                                + " "
                                + author.getLastName()
                ).trim();

        return new TicketCommentResponse(
                comment.getId(),
                author.getId(),
                authorName,
                comment.getBody(),
                comment.getCreatedAt()
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
                .toUpperCase(Locale.ROOT);
    }
}