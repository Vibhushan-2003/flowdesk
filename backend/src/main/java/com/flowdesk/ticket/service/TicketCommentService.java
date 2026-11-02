package com.flowdesk.ticket.service;

import com.flowdesk.notification.domain.NotificationType;
import com.flowdesk.notification.service.NotificationService;

import com.flowdesk.ticket.domain.CommentVisibility;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketComment;
import com.flowdesk.ticket.domain.TicketStatus;

import com.flowdesk.ticket.dto.CreateTicketCommentRequest;
import com.flowdesk.ticket.dto.TicketCommentResponse;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketCommentRepository;
import com.flowdesk.ticket.repository.TicketRepository;

import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
     *
     * CRITICAL SECURITY REQUIREMENT:
     * Employees can read PUBLIC comments ONLY.
     * Internal staff notes are strictly excluded.
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

        return ticketCommentRepository
                .findByTicket_IdAndVisibilityOrderByCreatedAtAsc(
                        ticket.getId(),
                        CommentVisibility.PUBLIC
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /*
     * EMPLOYEE
     *
     * Can add a comment only to a ticket
     * created by that employee.
     *
     * Employees cannot forge an INTERNAL visibility flag.
     * All employee comments are strictly PUBLIC.
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

        if (request != null && request.visibility() == CommentVisibility.INTERNAL) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Employees cannot create internal notes"
            );
        }

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
                        request,
                        CommentVisibility.PUBLIC
                );

        notifyAssignedEngineer(
                ticket,
                author
        );

        return response;
    }

    /*
     * SUPPORT STAFF (Support Engineer, Team Lead, Admin)
     *
     * Can read all comments (PUBLIC and INTERNAL) where
     * they have authorized ticket access.
     */
    @Transactional(readOnly = true)
    public List<TicketCommentResponse>
            getSupportComments(
                    UUID staffUserId,
                    String ticketNumber
            ) {

        String normalizedTicketNumber =
                normalizeTicketNumber(ticketNumber);

        Ticket ticket =
                ticketAssignmentRepository
                        .findAuthorizedSupportAssignments(
                                normalizedTicketNumber,
                                staffUserId
                        )
                        .stream()
                        .map(TicketAssignment::getTicket)
                        .findFirst()
                        .orElseGet(() -> {
                            User staffUser = userRepository.findById(staffUserId).orElse(null);
                            boolean isLeadOrAdmin = staffUser != null && staffUser.getRoles().stream()
                                    .anyMatch(r -> r.getCode() == RoleCode.ADMIN || r.getCode() == RoleCode.TEAM_LEAD);
                            if (isLeadOrAdmin) {
                                return ticketRepository.findByTicketNumber(normalizedTicketNumber)
                                        .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Ticket not found"
                                        ));
                            }
                            throw new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Ticket not found"
                            );
                        });

        return ticketCommentRepository
                .findByTicket_IdOrderByCreatedAtAsc(
                        ticket.getId()
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /*
     * SUPPORT STAFF (Support Engineer, Team Lead, Admin)
     *
     * Can add PUBLIC replies or INTERNAL staff notes where
     * they have authorized ticket access.
     *
     * PUBLIC replies notify the ticket creator.
     * INTERNAL notes do NOT notify the ticket creator.
     */
    @Transactional
    public TicketCommentResponse
            addSupportComment(
                    UUID staffUserId,
                    String ticketNumber,
                    CreateTicketCommentRequest request
            ) {

        String normalizedTicketNumber =
                normalizeTicketNumber(ticketNumber);

        Ticket ticket;
        User author;

        Optional<TicketAssignment> assignmentOpt =
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                normalizedTicketNumber,
                                staffUserId
                        );

        if (assignmentOpt.isPresent()) {
            ticket = assignmentOpt.get().getTicket();
            ensureCommentingAllowed(ticket);
            author = findUser(staffUserId);
        } else {
            User staffUser = userRepository.findById(staffUserId).orElse(null);
            boolean isLeadOrAdmin = staffUser != null && staffUser.getRoles().stream()
                    .anyMatch(r -> r.getCode() == RoleCode.ADMIN || r.getCode() == RoleCode.TEAM_LEAD);
            if (isLeadOrAdmin) {
                ticket = ticketRepository.findByTicketNumber(normalizedTicketNumber)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Ticket not found"
                        ));
                ensureCommentingAllowed(ticket);
                author = staffUser;
            } else {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Ticket not found"
                );
            }
        }

        CommentVisibility visibility =
                (request != null && request.visibility() != null)
                        ? request.visibility()
                        : CommentVisibility.PUBLIC;

        TicketCommentResponse response =
                createComment(
                        ticket,
                        author,
                        request,
                        visibility
                );

        if (visibility == CommentVisibility.PUBLIC) {
            notifyTicketCreator(
                    ticket,
                    author
            );
        }

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

    private TicketCommentResponse
            createComment(
                    Ticket ticket,
                    User author,
                    CreateTicketCommentRequest request,
                    CommentVisibility visibility
            ) {

        validateRequest(request);

        TicketComment comment;

        try {
            comment =
                    new TicketComment(
                            ticket,
                            author,
                            request.body(),
                            visibility
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
                comment.getCreatedAt(),
                comment.getVisibility()
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
