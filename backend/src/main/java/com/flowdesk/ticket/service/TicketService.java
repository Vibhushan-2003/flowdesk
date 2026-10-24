package com.flowdesk.ticket.service;

import com.flowdesk.approval.domain.ApprovalRequest;
import com.flowdesk.approval.domain.ApprovalStatus;
import com.flowdesk.approval.repository.ApprovalRequestRepository;
import com.flowdesk.approval.service.ApprovalNotificationService;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;

import com.flowdesk.common.dto.PageResponse;

import com.flowdesk.sla.domain.SlaPolicy;
import com.flowdesk.sla.repository.SlaPolicyRepository;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;

import com.flowdesk.ticket.dto.ClaimTicketResponse;
import com.flowdesk.ticket.dto.CreateTicketRequest;
import com.flowdesk.ticket.dto.TicketResponse;
import com.flowdesk.ticket.dto.TicketSummaryResponse;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketRepository;

import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;

import jakarta.transaction.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository
            ticketRepository;

    private final TicketAssignmentRepository
            ticketAssignmentRepository;

    private final UserRepository
            userRepository;

    private final SlaPolicyRepository
            slaPolicyRepository;

    private final ApprovalRequestRepository
            approvalRequestRepository;

    private final ApprovalNotificationService
            approvalNotificationService;

    private final AuditService
            auditService;

    public TicketService(
            TicketRepository ticketRepository,
            TicketAssignmentRepository ticketAssignmentRepository,
            UserRepository userRepository,
            SlaPolicyRepository slaPolicyRepository,
            ApprovalRequestRepository approvalRequestRepository,
            ApprovalNotificationService approvalNotificationService,
            AuditService auditService
    ) {
        this.ticketRepository =
                ticketRepository;

        this.ticketAssignmentRepository =
                ticketAssignmentRepository;

        this.userRepository =
                userRepository;

        this.slaPolicyRepository =
                slaPolicyRepository;

        this.approvalRequestRepository =
                approvalRequestRepository;

        this.approvalNotificationService =
                approvalNotificationService;

        this.auditService =
                auditService;
    }

    @Transactional
    public TicketResponse createTicket(
            UUID authenticatedUserId,
            CreateTicketRequest request
    ) {
        User creator =
                userRepository
                        .findById(
                                authenticatedUserId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Authenticated user no longer exists"
                                        )
                        );

        long sequenceValue =
                ticketRepository
                        .getNextTicketNumberSequenceValue();

        String ticketNumber =
                "FD-%06d".formatted(
                        sequenceValue
                );

        Ticket ticket =
                new Ticket(
                        ticketNumber,
                        request.type(),
                        request.title().trim(),
                        request.description().trim(),
                        creator
                );

        if (ticket.getType()
                == TicketType.INCIDENT) {

            applyActiveSlaPolicy(
                    ticket,
                    OffsetDateTime.now(
                            ZoneOffset.UTC
                    )
            );
        }

        Ticket savedTicket =
                ticketRepository
                        .saveAndFlush(
                                ticket
                        );

        auditService.recordUserAction(
                creator,
                AuditAction.TICKET_CREATED,
                AuditTargetType.TICKET,
                savedTicket.getId(),
                savedTicket.getTicketNumber(),
                Map.of(
                        "type",
                        savedTicket.getType().name(),
                        "priority",
                        savedTicket.getPriority().name(),
                        "status",
                        savedTicket.getStatus().name()
                )
        );

        if (savedTicket.getType()
                == TicketType.SERVICE_REQUEST) {

            createApprovalRequest(
                    savedTicket,
                    creator
            );
        }

        return toResponse(
                savedTicket
        );
    }

    private void createApprovalRequest(
            Ticket ticket,
            User requester
    ) {
        ApprovalRequest approvalRequest =
                new ApprovalRequest(
                        ticket,
                        requester,
                        OffsetDateTime.now(
                                ZoneOffset.UTC
                        )
                );

        ApprovalRequest savedApproval =
                approvalRequestRepository
                        .saveAndFlush(
                                approvalRequest
                        );

        auditService.recordUserAction(
                requester,
                AuditAction.APPROVAL_REQUESTED,
                AuditTargetType.APPROVAL,
                savedApproval.getId(),
                ticket.getTicketNumber(),
                Map.of(
                        "approvalStatus",
                        savedApproval
                                .getStatus()
                                .name(),
                        "ticketStatus",
                        ticket
                                .getStatus()
                                .name(),
                        "ticketNumber",
                        ticket
                                .getTicketNumber()
                )
        );

        approvalNotificationService
                .notifyApprovalRequested(
                        ticket,
                        requester
                );
    }

    private void applyActiveSlaPolicy(
            Ticket ticket,
            OffsetDateTime slaStartedAt
    ) {
        SlaPolicy slaPolicy =
                slaPolicyRepository
                        .findByPriorityAndActiveTrue(
                                ticket.getPriority()
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "No active SLA policy configured for priority "
                                                        + ticket.getPriority()
                                        )
                        );

        ticket.applySlaPolicy(
                slaPolicy,
                slaStartedAt
        );
    }

    @Transactional
    public PageResponse<TicketSummaryResponse>
            getMyTickets(
                    UUID authenticatedUserId,
                    int page,
                    int size
            ) {

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                        )
                );

        Page<Ticket> ticketPage =
                ticketRepository
                        .findByCreatedByUser_Id(
                                authenticatedUserId,
                                pageable
                        );

        return toPageResponse(
                ticketPage
        );
    }

    // Day 31: Optional database-level requester search and filtering.
    // The original three-argument method above remains available to existing callers.
    @Transactional
    public PageResponse<TicketSummaryResponse> getMyTickets(
            UUID authenticatedUserId,
            int page,
            int size,
            String search,
            TicketStatus status,
            TicketPriority priority,
            String sort
    ) {
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater"
            );
        }
        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 100"
            );
        }

        String normalizedSort = sort == null || sort.isBlank()
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

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(direction, "createdAt")
                        .and(Sort.by(direction, "id"))
        );

        Page<Ticket> tickets = ticketRepository.searchMyTickets(
                authenticatedUserId,
                buildSearchPattern(search),
                status,
                priority,
                pageable
        );

        return toPageResponse(tickets);
    }

    @Transactional
    public TicketResponse getMyTicket(
            UUID authenticatedUserId,
            String ticketNumber
    ) {
        String normalizedTicketNumber =
                normalizeTicketNumber(
                        ticketNumber
                );

        Ticket ticket =
                ticketRepository
                        .findByTicketNumberAndCreatedByUser_Id(
                                normalizedTicketNumber,
                                authenticatedUserId
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Ticket not found"
                                        )
                        );

        return toResponse(
                ticket
        );
    }

    @Transactional
    public PageResponse<TicketSummaryResponse>
            getSupportQueue(
                    int page,
                    int size
            ) {

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                Sort.Direction.ASC,
                                "createdAt"
                        )
                );

        Page<Ticket> ticketPage =
                ticketRepository
                        .findByStatus(
                                TicketStatus.OPEN,
                                pageable
                        );

        return toPageResponse(
                ticketPage
        );
    }

    @Transactional
    public ClaimTicketResponse claimTicket(
            UUID authenticatedUserId,
            String ticketNumber
    ) {
        User engineer =
                getSupportEngineer(
                        authenticatedUserId
                );

        String normalizedTicketNumber =
                normalizeTicketNumber(
                        ticketNumber
                );

        Ticket ticket =
                ticketRepository
                        .findByTicketNumberForUpdate(
                                normalizedTicketNumber
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Ticket not found"
                                        )
                        );

        if (ticket.getStatus()
                != TicketStatus.OPEN) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ticket is no longer available to claim"
            );
        }

        TicketStatus previousStatus =
                ticket.getStatus();

        ticket.markAssigned();

        TicketAssignment assignment =
                new TicketAssignment(
                        ticket,
                        engineer
                );

        TicketAssignment savedAssignment =
                ticketAssignmentRepository
                        .saveAndFlush(
                                assignment
                        );

        auditService.recordUserAction(
                engineer,
                AuditAction.TICKET_CLAIMED,
                AuditTargetType.TICKET,
                ticket.getId(),
                ticket.getTicketNumber(),
                Map.of(
                        "fromStatus",
                        previousStatus.name(),
                        "toStatus",
                        ticket.getStatus().name()
                )
        );

        return new ClaimTicketResponse(
                savedAssignment.getId(),
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getStatus(),
                engineer.getId(),
                engineer.getEmail(),
                savedAssignment.getAssignedAt()
        );
    }

    private User getSupportEngineer(
            UUID authenticatedUserId
    ) {
        User user =
                userRepository
                        .findById(
                                authenticatedUserId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Authenticated user no longer exists"
                                        )
                        );

        boolean isSupportEngineer =
                user.getRoles()
                        .stream()
                        .anyMatch(
                                role ->
                                        role.getCode()
                                                == RoleCode.SUPPORT_ENGINEER
                        );

        if (!isSupportEngineer) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Support engineer role required"
            );
        }

        return user;
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

    private String buildSearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        String normalized = search.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Search must be 100 characters or fewer"
            );
        }

        // '!' is the escape character specified in TicketRepository's JPQL.
        String escaped = normalized
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");

        return "%" + escaped + "%";
    }

    private PageResponse<TicketSummaryResponse>
            toPageResponse(
                    Page<Ticket> ticketPage
            ) {

        List<TicketSummaryResponse> content =
                ticketPage
                        .getContent()
                        .stream()
                        .map(
                                this::toSummaryResponse
                        )
                        .toList();

        return new PageResponse<>(
                content,
                ticketPage.getNumber(),
                ticketPage.getSize(),
                ticketPage.getTotalElements(),
                ticketPage.getTotalPages(),
                ticketPage.isFirst(),
                ticketPage.isLast()
        );
    }

    private TicketResponse toResponse(
            Ticket ticket
    ) {
        OffsetDateTime evaluatedAt =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );

        String rejectionReason = null;
        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            rejectionReason = approvalRequestRepository
                    .findByTicket_Id(ticket.getId())
                    .filter(approval -> approval.getStatus() == ApprovalStatus.REJECTED)
                    .map(ApprovalRequest::getDecisionNote)
                    .orElse(null);
        }

        return new TicketResponse(
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
                ),
                rejectionReason
        );
    }

    private TicketSummaryResponse toSummaryResponse(
            Ticket ticket
    ) {
        OffsetDateTime evaluatedAt =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );

        return new TicketSummaryResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getType(),
                ticket.getTitle(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
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
}