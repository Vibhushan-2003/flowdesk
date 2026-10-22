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

import com.flowdesk.ticket.dto.ResolveTicketRequest;
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

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status request is required"
            );
        }

        /*
         * RESOLVED is intentionally not handled through
         * the generic status endpoint anymore.
         *
         * Resolving is now a richer business operation
         * that requires a human-approved resolution
         * summary.
         */
        if (request.status()
                == TicketStatus.RESOLVED) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use the resolve endpoint to resolve a ticket"
            );
        }

        Ticket ticket =
                assignment.getTicket();

        TicketStatus previousStatus =
                ticket.getStatus();

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

        ticketAssignmentRepository.flush();

        notifyTicketCreatorAboutStatusChange(
                assignment,
                previousStatus,
                newStatus
        );

        recordStatusChange(
                assignment,
                previousStatus,
                newStatus
        );

        return toResponse(
                assignment
        );
    }

    @Transactional
    public SupportTicketResponse
            resolveTicket(
                    UUID supportEngineerId,
                    String ticketNumber,
                    ResolveTicketRequest request
            ) {

        String normalizedTicketNumber =
                normalizeTicketNumber(
                        ticketNumber
                );

        /*
         * Lock and verify the active assignment.
         *
         * This ensures only the currently assigned
         * support engineer can resolve the ticket and
         * protects the transition from concurrent
         * assignment changes.
         */
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

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Resolution request is required"
            );
        }

        Ticket ticket =
                assignment.getTicket();

        TicketStatus previousStatus =
                ticket.getStatus();

        try {
            ticket.resolve(
                    request.resolutionSummary()
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

        /*
         * Resolution and assignment release happen in
         * the same database transaction.
         *
         * We must never leave a RESOLVED ticket with an
         * active support assignment.
         */
        assignment.release();

        ticketAssignmentRepository.flush();

        notifyTicketCreatorAboutStatusChange(
                assignment,
                previousStatus,
                ticket.getStatus()
        );

        /*
         * Do not put the resolution summary itself in
         * audit metadata.
         *
         * The summary is business data stored on the
         * ticket. The audit record only captures the
         * state transition.
         */
        recordStatusChange(
                assignment,
                previousStatus,
                ticket.getStatus()
        );

        return toResponse(
                assignment
        );
    }

    private void recordStatusChange(
            TicketAssignment assignment,
            TicketStatus previousStatus,
            TicketStatus newStatus
    ) {

        Ticket ticket =
                assignment.getTicket();

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
                ticket.getResolutionSummary(),
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