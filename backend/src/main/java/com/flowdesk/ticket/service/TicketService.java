package com.flowdesk.ticket.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;

import com.flowdesk.common.dto.PageResponse;

import com.flowdesk.sla.domain.SlaPolicy;
import com.flowdesk.sla.repository.SlaPolicyRepository;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketStatus;

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

    private final AuditService
            auditService;

    public TicketService(
            TicketRepository ticketRepository,
            TicketAssignmentRepository ticketAssignmentRepository,
            UserRepository userRepository,
            SlaPolicyRepository slaPolicyRepository,
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
                OffsetDateTime.now(
                        ZoneOffset.UTC
                )
        );

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

        return toResponse(
                savedTicket
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
                ticket.evaluateResponseSla(
                        evaluatedAt
                ),
                ticket.evaluateResolutionSla(
                        evaluatedAt
                )
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