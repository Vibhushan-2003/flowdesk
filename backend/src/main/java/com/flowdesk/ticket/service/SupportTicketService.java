package com.flowdesk.ticket.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;

import com.flowdesk.common.dto.PageResponse;

import com.flowdesk.notification.domain.NotificationType;
import com.flowdesk.notification.service.NotificationService;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketStatus;

import com.flowdesk.ticket.dto.SupportTicketResponse;
import com.flowdesk.ticket.dto.SupportTicketSummaryResponse;
import com.flowdesk.ticket.dto.UpdateTicketStatusRequest;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;

import com.flowdesk.user.domain.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class SupportTicketService {

    private final TicketAssignmentRepository
            ticketAssignmentRepository;

    private final NotificationService
            notificationService;

    private final AuditService
            auditService;

    public SupportTicketService(
            TicketAssignmentRepository ticketAssignmentRepository,
            NotificationService notificationService,
            AuditService auditService
    ) {
        this.ticketAssignmentRepository =
                ticketAssignmentRepository;

        this.notificationService =
                notificationService;

        this.auditService =
                auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportTicketSummaryResponse>
            getMyAssignedTickets(
                    UUID supportEngineerId,
                    int page,
                    int size
            ) {

        PageRequest pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                Sort.Direction.DESC,
                                "assignedAt"
                        )
                );

        Page<TicketAssignment> assignments =
                ticketAssignmentRepository
                        .findByAssignedToUser_IdAndReleasedAtIsNull(
                                supportEngineerId,
                                pageable
                        );

        Page<SupportTicketSummaryResponse> responsePage =
                assignments.map(
                        this::toSummaryResponse
                );

        return new PageResponse<>(
                responsePage.getContent(),
                responsePage.getNumber(),
                responsePage.getSize(),
                responsePage.getTotalElements(),
                responsePage.getTotalPages(),
                responsePage.isFirst(),
                responsePage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse
            getMyAssignedTicket(
                    UUID supportEngineerId,
                    String ticketNumber
            ) {

        String normalizedTicketNumber =
                normalizeTicketNumber(
                        ticketNumber
                );

        TicketAssignment assignment =
                ticketAssignmentRepository
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

        return toResponse(
                assignment
        );
    }

    @Transactional
    public SupportTicketResponse
            updateStatus(
                    UUID supportEngineerId,
                    String ticketNumber,
                    UpdateTicketStatusRequest request
            ) {

        String normalizedTicketNumber =
                normalizeTicketNumber(
                        ticketNumber
                );

        TicketAssignment assignment =
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
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

        Ticket ticket =
                assignment.getTicket();

        /*
         * Capture the previous state before applying
         * the transition.
         *
         * We need this for both notifications and
         * the immutable audit record.
         */
        TicketStatus previousStatus =
                ticket.getStatus();

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status request is required"
            );
        }

        try {
            ticket.transitionSupportStatus(
                    request.status()
            );
        }
        catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage()
            );
        }
        catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }

        TicketStatus newStatus =
                ticket.getStatus();

        /*
         * When resolved, release the engineer's
         * active assignment exactly as before.
         */
        if (newStatus == TicketStatus.RESOLVED) {
            assignment.release();
        }

        /*
         * Flush the ticket / assignment mutation
         * before recording dependent records.
         */
        ticketAssignmentRepository.flush();

        /*
         * Keep the existing employee notification
         * behavior.
         *
         * This is still part of the same transaction.
         */
        notifyTicketCreatorAboutStatusChange(
                assignment,
                previousStatus,
                newStatus
        );

        /*
         * Record the successful business transition.
         *
         * The assigned support engineer is the actor.
         * Because AuditService uses the default REQUIRED
         * transaction propagation, this audit insert joins
         * this same transaction.
         */
        auditService.recordUserAction(
                assignment.getAssignedToUser(),
                AuditAction.TICKET_STATUS_CHANGED,
                AuditTargetType.TICKET,
                ticket.getId(),
                ticket.getTicketNumber(),
                Map.of(
                        "fromStatus",
                        previousStatus.name(),
                        "toStatus",
                        newStatus.name()
                )
        );

        return toResponse(
                assignment
        );
    }

    private void notifyTicketCreatorAboutStatusChange(
            TicketAssignment assignment,
            TicketStatus previousStatus,
            TicketStatus newStatus
    ) {

        Ticket ticket =
                assignment.getTicket();

        User recipient =
                ticket.getCreatedByUser();

        User actor =
                assignment.getAssignedToUser();

        if (recipient == null) {
            return;
        }

        /*
         * Defensive protection against creating
         * a notification where actor and recipient
         * are accidentally the same user.
         */
        if (actor != null
                && actor.getId() != null
                && recipient.getId() != null
                && actor.getId()
                        .equals(
                                recipient.getId()
                        )) {

            return;
        }

        if (newStatus == TicketStatus.IN_PROGRESS
                && previousStatus
                        == TicketStatus.ASSIGNED) {

            notificationService
                    .createNotification(
                            recipient,
                            actor,
                            ticket,
                            NotificationType
                                    .TICKET_STATUS_CHANGED,
                            "Support work started",
                            "A support engineer started working on "
                                    + ticket.getTicketNumber()
                    );

            return;
        }

        if (newStatus
                == TicketStatus.WAITING_FOR_USER) {

            notificationService
                    .createNotification(
                            recipient,
                            actor,
                            ticket,
                            NotificationType
                                    .TICKET_STATUS_CHANGED,
                            "Waiting for your response",
                            "Support is waiting for your response on "
                                    + ticket.getTicketNumber()
                    );

            return;
        }

        if (newStatus == TicketStatus.IN_PROGRESS
                && previousStatus
                        == TicketStatus.WAITING_FOR_USER) {

            notificationService
                    .createNotification(
                            recipient,
                            actor,
                            ticket,
                            NotificationType
                                    .TICKET_STATUS_CHANGED,
                            "Work resumed",
                            "Support resumed work on "
                                    + ticket.getTicketNumber()
                    );

            return;
        }

        if (newStatus
                == TicketStatus.RESOLVED) {

            notificationService
                    .createNotification(
                            recipient,
                            actor,
                            ticket,
                            NotificationType
                                    .TICKET_RESOLVED,
                            "Ticket resolved",
                            ticket.getTicketNumber()
                                    + " has been resolved"
                    );
        }
    }

    private SupportTicketSummaryResponse
            toSummaryResponse(
                    TicketAssignment assignment
            ) {

        Ticket ticket =
                assignment.getTicket();

        OffsetDateTime evaluatedAt =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );

        return new SupportTicketSummaryResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getType(),
                ticket.getTitle(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                assignment.getAssignedAt(),
                ticket.getResponseDueAt(),
                ticket.getResolutionDueAt(),
                ticket.evaluateResponseSla(
                        evaluatedAt
                ),
                ticket.evaluateResolutionSla(
                        evaluatedAt
                )
        );
    }

    private SupportTicketResponse
            toResponse(
                    TicketAssignment assignment
            ) {

        Ticket ticket =
                assignment.getTicket();

        OffsetDateTime evaluatedAt =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );

        return new SupportTicketResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getType(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getCreatedByUser().getId(),
                ticket.getCreatedByUser().getEmail(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                assignment.getAssignedAt(),
                ticket.getResponseDueAt(),
                ticket.getResolutionDueAt(),
                ticket.getFirstRespondedAt(),
                ticket.getResolvedAt(),
                ticket.evaluateResponseSla(
                        evaluatedAt
                ),
                ticket.evaluateResolutionSla(
                        evaluatedAt
                )
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