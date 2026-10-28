
package com.flowdesk.ticket.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;

import com.flowdesk.common.dto.PageResponse;

import com.flowdesk.notification.domain.NotificationType;
import com.flowdesk.notification.service.NotificationService;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketPriority;
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

    private final TicketAssignmentRepository ticketAssignmentRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public SupportTicketService(
            TicketAssignmentRepository ticketAssignmentRepository,
            NotificationService notificationService,
            AuditService auditService
    ) {
        this.ticketAssignmentRepository = ticketAssignmentRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportTicketSummaryResponse>
            getMyAssignedTickets(
                    UUID supportEngineerId,
                    int page,
                    int size
            ) {

        PageRequest pageable = PageRequest.of(
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

        return toPageResponse(assignments);
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportTicketSummaryResponse>
            getMyAssignedTickets(
                    UUID supportEngineerId,
                    int page,
                    int size,
                    String search,
                    TicketStatus status,
                    TicketPriority priority,
                    String sort
            ) {

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid pagination"
            );
        }

        String normalizedSort =
                sort == null || sort.isBlank()
                        ? "newest"
                        : sort.trim().toLowerCase(Locale.ROOT);

        Sort.Direction direction = switch (normalizedSort) {
            case "newest" -> Sort.Direction.DESC;
            case "oldest" -> Sort.Direction.ASC;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Sort must be newest or oldest"
            );
        };

        String searchPattern = buildSearchPattern(search);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(direction, "assignedAt")
                        .and(Sort.by(direction, "id"))
        );

        Page<TicketAssignment> assignments =
                ticketAssignmentRepository.searchMyActiveAssignments(
                        supportEngineerId,
                        searchPattern,
                        status,
                        priority,
                        pageable
                );

        return toPageResponse(assignments);
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportTicketSummaryResponse>
            getMyTicketHistory(
                    UUID supportEngineerId,
                    int page,
                    int size
            ) {
        return getMyTicketHistory(
                supportEngineerId,
                page,
                size,
                null,
                null,
                null,
                "newest"
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportTicketSummaryResponse>
            getMyTicketHistory(
                    UUID supportEngineerId,
                    int page,
                    int size,
                    String search,
                    TicketStatus status,
                    TicketPriority priority,
                    String sort
            ) {

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid pagination"
            );
        }

        String normalizedSort =
                sort == null || sort.isBlank()
                        ? "newest"
                        : sort.trim().toLowerCase(Locale.ROOT);

        Sort.Direction direction = switch (normalizedSort) {
            case "newest" -> Sort.Direction.DESC;
            case "oldest" -> Sort.Direction.ASC;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Sort must be newest or oldest"
            );
        };

        String searchPattern = buildSearchPattern(search);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(direction, "ticket.resolvedAt")
                        .and(Sort.by(direction, "id"))
        );

        Page<TicketAssignment> assignments =
                ticketAssignmentRepository.searchMyHistoryAssignments(
                        supportEngineerId,
                        searchPattern,
                        status,
                        priority,
                        pageable
                );

        return toPageResponse(assignments);
    }

    private PageResponse<SupportTicketSummaryResponse>
            toPageResponse(
                    Page<TicketAssignment> assignments
            ) {

        Page<SupportTicketSummaryResponse> responsePage =
                assignments.map(this::toSummaryResponse);

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

    private String buildSearchPattern(String search) {

        if (search == null || search.isBlank()) {
            return null;
        }

        String normalized =
                search.trim().toLowerCase(Locale.ROOT);

        if (normalized.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Search must be 100 characters or fewer"
            );
        }

        return "%" + normalized
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_") + "%";
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse
            getMyAssignedTicket(
                    UUID supportEngineerId,
                    String ticketNumber
            ) {

        String normalizedTicketNumber =
                normalizeTicketNumber(ticketNumber);

        TicketAssignment assignment =
                ticketAssignmentRepository
                        .findAuthorizedSupportAssignments(
                                normalizedTicketNumber,
                                supportEngineerId
                        )
                        .stream()
                        .findFirst()
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Ticket not found"
                                )
                        );

        return toResponse(assignment);
    }

    @Transactional
    public SupportTicketResponse
            updateStatus(
                    UUID supportEngineerId,
                    String ticketNumber,
                    UpdateTicketStatusRequest request
            ) {

        String normalizedTicketNumber =
                normalizeTicketNumber(ticketNumber);

        TicketAssignment assignment =
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                normalizedTicketNumber,
                                supportEngineerId
                        )
                        .orElseThrow(
                                () -> new ResponseStatusException(
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
         * Resolving requires a human-approved resolution
         * summary, so the dedicated resolve endpoint is used.
         */
        if (request.status() == TicketStatus.RESOLVED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Use the resolve endpoint to resolve a ticket"
            );
        }

        Ticket ticket = assignment.getTicket();
        TicketStatus previousStatus = ticket.getStatus();

        try {
            ticket.transitionSupportStatus(request.status());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage()
            );
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }

        TicketStatus newStatus = ticket.getStatus();

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

        return toResponse(assignment);
    }

    @Transactional
    public SupportTicketResponse
            resolveTicket(
                    UUID supportEngineerId,
                    String ticketNumber,
                    ResolveTicketRequest request
            ) {

        String normalizedTicketNumber =
                normalizeTicketNumber(ticketNumber);

        /*
         * Lock and verify the active assignment.
         * Only the currently assigned engineer may resolve.
         */
        TicketAssignment assignment =
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                normalizedTicketNumber,
                                supportEngineerId
                        )
                        .orElseThrow(
                                () -> new ResponseStatusException(
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

        Ticket ticket = assignment.getTicket();
        TicketStatus previousStatus = ticket.getStatus();

        try {
            ticket.resolve(request.resolutionSummary());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage()
            );
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }

        /*
         * Resolution and assignment release happen in
         * the same database transaction.
         */
        assignment.release();

        ticketAssignmentRepository.flush();

        notifyTicketCreatorAboutStatusChange(
                assignment,
                previousStatus,
                ticket.getStatus()
        );

        /*
         * Keep the resolution summary out of audit metadata.
         * The audit record captures only the status transition.
         */
        recordStatusChange(
                assignment,
                previousStatus,
                ticket.getStatus()
        );

        return toResponse(assignment);
    }

    private void recordStatusChange(
            TicketAssignment assignment,
            TicketStatus previousStatus,
            TicketStatus newStatus
    ) {

        Ticket ticket = assignment.getTicket();

        auditService.recordUserAction(
                assignment.getAssignedToUser(),
                AuditAction.TICKET_STATUS_CHANGED,
                AuditTargetType.TICKET,
                ticket.getId(),
                ticket.getTicketNumber(),
                Map.of(
                        "fromStatus", previousStatus.name(),
                        "toStatus", newStatus.name()
                )
        );
    }

    private void notifyTicketCreatorAboutStatusChange(
            TicketAssignment assignment,
            TicketStatus previousStatus,
            TicketStatus newStatus
    ) {

        Ticket ticket = assignment.getTicket();
        User recipient = ticket.getCreatedByUser();
        User actor = assignment.getAssignedToUser();

        if (recipient == null) {
            return;
        }

        if (actor != null
                && actor.getId() != null
                && recipient.getId() != null
                && actor.getId().equals(recipient.getId())) {

            return;
        }

        if (newStatus == TicketStatus.IN_PROGRESS
                && previousStatus == TicketStatus.ASSIGNED) {

            notificationService.createNotification(
                    recipient,
                    actor,
                    ticket,
                    NotificationType.TICKET_STATUS_CHANGED,
                    "Support work started",
                    "A support engineer started working on "
                            + ticket.getTicketNumber()
            );

            return;
        }

        if (newStatus == TicketStatus.WAITING_FOR_USER) {

            notificationService.createNotification(
                    recipient,
                    actor,
                    ticket,
                    NotificationType.TICKET_STATUS_CHANGED,
                    "Waiting for your response",
                    "Support is waiting for your response on "
                            + ticket.getTicketNumber()
            );

            return;
        }

        if (newStatus == TicketStatus.IN_PROGRESS
                && previousStatus == TicketStatus.WAITING_FOR_USER) {

            notificationService.createNotification(
                    recipient,
                    actor,
                    ticket,
                    NotificationType.TICKET_STATUS_CHANGED,
                    "Work resumed",
                    "Support resumed work on "
                            + ticket.getTicketNumber()
            );

            return;
        }

        if (newStatus == TicketStatus.RESOLVED) {

            notificationService.createNotification(
                    recipient,
                    actor,
                    ticket,
                    NotificationType.TICKET_RESOLVED,
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

        Ticket ticket = assignment.getTicket();

        OffsetDateTime evaluatedAt =
                OffsetDateTime.now(ZoneOffset.UTC);

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
                ticket.evaluateResponseSla(evaluatedAt),
                ticket.evaluateResolutionSla(evaluatedAt)
        );
    }

    private SupportTicketResponse
            toResponse(
                    TicketAssignment assignment
            ) {

        Ticket ticket = assignment.getTicket();

        OffsetDateTime evaluatedAt =
                OffsetDateTime.now(ZoneOffset.UTC);

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
                ticket.evaluateResponseSla(evaluatedAt),
                ticket.evaluateResolutionSla(evaluatedAt)
        );
    }

    private String normalizeTicketNumber(
            String ticketNumber
    ) {

        if (ticketNumber == null || ticketNumber.isBlank()) {
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
