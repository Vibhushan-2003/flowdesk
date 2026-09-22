package com.flowdesk.ticket.service;

import com.flowdesk.notification.domain.NotificationType;
import com.flowdesk.notification.service.NotificationService;

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

    private final NotificationService
            notificationService;

    public TicketCommentService(
            TicketCommentRepository ticketCommentRepository,
            TicketRepository ticketRepository,
            TicketAssignmentRepository ticketAssignmentRepository,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.ticketCommentRepository =
                ticketCommentRepository;

        this.ticketRepository =
                ticketRepository;

        this.ticketAssignmentRepository =
                ticketAssignmentRepository;

        this.userRepository =
                userRepository;

        this.notificationService =
                notificationService;
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
     *
     * If the ticket currently has an active
     * support engineer, that engineer receives
     * a persistent notification.
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

        TicketCommentResponse response =
                createComment(
                        ticket,
                        author,
                        request
                );

        notifyAssignedEngineer(
                ticket,
                author
        );

        return response;
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
     *
     * The employee who created the ticket
     * receives a persistent notification.
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

        TicketCommentResponse response =
                createComment(
                        ticket,
                        author,
                        request
                );

        notifyTicketCreator(
                ticket,
                author
        );

        return response;
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

    private User findUser(
            UUID userId
    ) {

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
            getComments(
                    Ticket ticket
            ) {

        return ticketCommentRepository
                .findByTicket_IdOrderByCreatedAtAsc(
                        ticket.getId()
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private TicketCommentResponse
            createComment(
                    Ticket ticket,
                    User author,
                    CreateTicketCommentRequest request
            ) {

        validateRequest(request);

        TicketComment comment;

        try {
            comment =
                    new TicketComment(
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

    /*
     * Employee -> Support Engineer
     *
     * An employee may comment before anybody
     * has claimed the ticket. In that case
     * there is no notification recipient yet,
     * so no notification is created.
     */
    private void notifyAssignedEngineer(
            Ticket ticket,
            User employee
    ) {

        if (ticket.getId() == null) {
            return;
        }

        ticketAssignmentRepository
                .findByTicket_IdAndReleasedAtIsNull(
                        ticket.getId()
                )
                .ifPresent(
                        assignment -> {
                            User recipient =
                                    assignment
                                            .getAssignedToUser();

                            if (recipient == null
                                    || isSameUser(
                                            recipient,
                                            employee
                                    )) {
                                return;
                            }

                            notificationService
                                    .createNotification(
                                            recipient,
                                            employee,
                                            ticket,
                                            NotificationType
                                                    .TICKET_COMMENT_ADDED,
                                            "New requester reply",
                                            "The requester replied to "
                                                    + ticket.getTicketNumber()
                                    );
                        }
                );
    }

    /*
     * Support Engineer -> Employee
     */
    private void notifyTicketCreator(
            Ticket ticket,
            User supportEngineer
    ) {

        User recipient =
                ticket.getCreatedByUser();

        if (recipient == null
                || isSameUser(
                        recipient,
                        supportEngineer
                )) {
            return;
        }

        notificationService
                .createNotification(
                        recipient,
                        supportEngineer,
                        ticket,
                        NotificationType
                                .TICKET_COMMENT_ADDED,
                        "New support reply",
                        "A support engineer replied to "
                                + ticket.getTicketNumber()
                );
    }

    /*
     * Avoid creating a notification where
     * actor and recipient are the same user.
     */
    private boolean isSameUser(
            User first,
            User second
    ) {

        if (first == null
                || second == null
                || first.getId() == null
                || second.getId() == null) {

            return false;
        }

        return first
                .getId()
                .equals(
                        second.getId()
                );
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

        if (request.body()
                .trim()
                .length() > 4000) {

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

    private TicketCommentResponse
            toResponse(
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