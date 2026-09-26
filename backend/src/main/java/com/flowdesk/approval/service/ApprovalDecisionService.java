package com.flowdesk.approval.service;

import com.flowdesk.approval.domain.ApprovalRequest;
import com.flowdesk.approval.domain.ApprovalStatus;

import com.flowdesk.approval.dto.ApprovalResponse;

import com.flowdesk.approval.repository.ApprovalRequestRepository;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;

import com.flowdesk.common.dto.PageResponse;

import com.flowdesk.sla.domain.SlaPolicy;
import com.flowdesk.sla.repository.SlaPolicyRepository;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketStatus;

import com.flowdesk.ticket.repository.TicketRepository;

import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;

import com.flowdesk.user.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ApprovalDecisionService {

    private static final int MAX_PAGE_SIZE =
            100;

    private static final Set<RoleCode>
            APPROVER_ROLES =
            Set.of(
                    RoleCode.TEAM_LEAD,
                    RoleCode.ADMIN
            );

    private final ApprovalRequestRepository
            approvalRequestRepository;

    private final TicketRepository
            ticketRepository;

    private final UserRepository
            userRepository;

    private final SlaPolicyRepository
            slaPolicyRepository;

    private final AuditService
            auditService;

    public ApprovalDecisionService(
            ApprovalRequestRepository approvalRequestRepository,
            TicketRepository ticketRepository,
            UserRepository userRepository,
            SlaPolicyRepository slaPolicyRepository,
            AuditService auditService
    ) {
        this.approvalRequestRepository =
                approvalRequestRepository;

        this.ticketRepository =
                ticketRepository;

        this.userRepository =
                userRepository;

        this.slaPolicyRepository =
                slaPolicyRepository;

        this.auditService =
                auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ApprovalResponse>
            getPendingApprovals(
                    UUID authenticatedUserId,
                    int page,
                    int size
            ) {

        getApprover(
                authenticatedUserId
        );

        validatePagination(
                page,
                size
        );

        Pageable pageable =
                PageRequest.of(
                        page,
                        size
                );

        Page<ApprovalRequest> approvalPage =
                approvalRequestRepository
                        .findByStatusOrderByRequestedAtAsc(
                                ApprovalStatus.PENDING,
                                pageable
                        );

        List<ApprovalResponse> content =
                approvalPage
                        .getContent()
                        .stream()
                        .map(
                                this::toResponse
                        )
                        .toList();

        return new PageResponse<>(
                content,
                approvalPage.getNumber(),
                approvalPage.getSize(),
                approvalPage.getTotalElements(),
                approvalPage.getTotalPages(),
                approvalPage.isFirst(),
                approvalPage.isLast()
        );
    }

    @Transactional
    public ApprovalResponse approve(
            UUID authenticatedUserId,
            UUID approvalId,
            String note
    ) {

        User approver =
                getApprover(
                        authenticatedUserId
                );

        ApprovalRequest approval =
                getPendingApprovalForDecision(
                        approvalId,
                        approver
                );

        Ticket ticket =
                approval.getTicket();

        if (
                ticket.getStatus()
                        != TicketStatus.PENDING_APPROVAL
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ticket is no longer pending approval"
            );
        }

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

        OffsetDateTime decisionTime =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );

        approval.approve(
                approver,
                note,
                decisionTime
        );

        TicketStatus previousTicketStatus =
                ticket.getStatus();

        ticket.approveServiceRequest();

        ticket.applySlaPolicy(
                slaPolicy,
                decisionTime
        );

        ticketRepository
                .saveAndFlush(
                        ticket
                );

        ApprovalRequest savedApproval =
                approvalRequestRepository
                        .saveAndFlush(
                                approval
                        );

        auditService.recordUserAction(
                approver,
                AuditAction.APPROVAL_APPROVED,
                AuditTargetType.APPROVAL,
                savedApproval.getId(),
                ticket.getTicketNumber(),
                Map.of(
                        "approvalStatus",
                        savedApproval
                                .getStatus()
                                .name(),
                        "fromTicketStatus",
                        previousTicketStatus
                                .name(),
                        "toTicketStatus",
                        ticket.getStatus()
                                .name(),
                        "ticketNumber",
                        ticket.getTicketNumber()
                )
        );

        return toResponse(
                savedApproval
        );
    }

    @Transactional
    public ApprovalResponse reject(
            UUID authenticatedUserId,
            UUID approvalId,
            String reason
    ) {

        User approver =
                getApprover(
                        authenticatedUserId
                );

        ApprovalRequest approval =
                getPendingApprovalForDecision(
                        approvalId,
                        approver
                );

        Ticket ticket =
                approval.getTicket();

        if (
                ticket.getStatus()
                        != TicketStatus.PENDING_APPROVAL
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ticket is no longer pending approval"
            );
        }

        OffsetDateTime decisionTime =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );

        approval.reject(
                approver,
                reason,
                decisionTime
        );

        TicketStatus previousTicketStatus =
                ticket.getStatus();

        ticket.rejectServiceRequest();

        ticketRepository
                .saveAndFlush(
                        ticket
                );

        ApprovalRequest savedApproval =
                approvalRequestRepository
                        .saveAndFlush(
                                approval
                        );

        auditService.recordUserAction(
                approver,
                AuditAction.APPROVAL_REJECTED,
                AuditTargetType.APPROVAL,
                savedApproval.getId(),
                ticket.getTicketNumber(),
                Map.of(
                        "approvalStatus",
                        savedApproval
                                .getStatus()
                                .name(),
                        "fromTicketStatus",
                        previousTicketStatus
                                .name(),
                        "toTicketStatus",
                        ticket.getStatus()
                                .name(),
                        "ticketNumber",
                        ticket.getTicketNumber()
                )
        );

        return toResponse(
                savedApproval
        );
    }

    private ApprovalRequest
            getPendingApprovalForDecision(
                    UUID approvalId,
                    User approver
            ) {

        ApprovalRequest approval =
                approvalRequestRepository
                        .findByIdForUpdate(
                                approvalId
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Approval request not found"
                                        )
                        );

        if (
                approval.getStatus()
                        != ApprovalStatus.PENDING
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Approval request has already been decided"
            );
        }

        UUID requesterId =
                approval
                        .getRequestedByUser()
                        .getId();

        if (
                requesterId != null
                        && requesterId.equals(
                                approver.getId()
                        )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Requester cannot decide their own approval request"
            );
        }

        return approval;
    }

    private User getApprover(
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

        boolean canApprove =
                user.getRoles()
                        .stream()
                        .anyMatch(
                                role ->
                                        APPROVER_ROLES
                                                .contains(
                                                        role.getCode()
                                                )
                        );

        if (!canApprove) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Team lead or admin role required"
            );
        }

        return user;
    }

    private void validatePagination(
            int page,
            int size
    ) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater"
            );
        }

        if (
                size < 1
                        || size > MAX_PAGE_SIZE
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 100"
            );
        }
    }

    private ApprovalResponse toResponse(
            ApprovalRequest approval
    ) {

        Ticket ticket =
                approval.getTicket();

        User decisionMaker =
                approval.getDecidedByUser();

        return new ApprovalResponse(
                approval.getId(),

                ticket.getId(),

                ticket.getTicketNumber(),

                ticket.getTitle(),

                ticket.getDescription(),

                ticket.getType(),

                ticket.getPriority(),

                ticket.getStatus(),

                approval
                        .getRequestedByUser()
                        .getId(),

                approval
                        .getRequestedByUser()
                        .getEmail(),

                approval.getStatus(),

                decisionMaker == null
                        ? null
                        : decisionMaker.getId(),

                decisionMaker == null
                        ? null
                        : decisionMaker.getEmail(),

                approval.getDecisionNote(),

                approval.getRequestedAt(),

                approval.getDecidedAt(),

                ticket.getResponseDueAt(),

                ticket.getResolutionDueAt()
        );
    }
}