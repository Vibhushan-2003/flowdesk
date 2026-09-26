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
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;

import com.flowdesk.ticket.repository.TicketRepository;

import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;

import com.flowdesk.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import org.springframework.http.HttpStatus;

import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalDecisionServiceTest {

    @Mock
    private ApprovalRequestRepository
            approvalRequestRepository;

    @Mock
    private TicketRepository
            ticketRepository;

    @Mock
    private UserRepository
            userRepository;

    @Mock
    private SlaPolicyRepository
            slaPolicyRepository;

    @Mock
    private AuditService
            auditService;

    @Mock
    private SlaPolicy mediumSlaPolicy;

    private ApprovalDecisionService service;

    @BeforeEach
    void setUp() {
        service =
                new ApprovalDecisionService(
                        approvalRequestRepository,
                        ticketRepository,
                        userRepository,
                        slaPolicyRepository,
                        auditService
                );
    }

    @Test
    void teamLeadCanApproveAndStartSla() {
        UUID approverId =
                UUID.randomUUID();

        UUID requesterId =
                UUID.randomUUID();

        UUID approvalId =
                UUID.randomUUID();

        User requester =
                user(
                        requesterId,
                        "requester@example.com"
                );

        User approver =
                approver(
                        approverId,
                        "lead@example.com",
                        RoleCode.TEAM_LEAD
                );

        Ticket ticket =
                serviceRequest(
                        requester,
                        "FD-000030"
                );

        ApprovalRequest approval =
                new ApprovalRequest(
                        ticket,
                        requester,
                        OffsetDateTime.now(
                                ZoneOffset.UTC
                        ).minusHours(1)
                );

        when(
                userRepository.findById(
                        approverId
                )
        ).thenReturn(
                Optional.of(
                        approver
                )
        );

        when(
                approvalRequestRepository
                        .findByIdForUpdate(
                                approvalId
                        )
        ).thenReturn(
                Optional.of(
                        approval
                )
        );

        configureMediumSlaPolicy();

        when(
                ticketRepository.saveAndFlush(
                        ticket
                )
        ).thenReturn(
                ticket
        );

        when(
                approvalRequestRepository
                        .saveAndFlush(
                                approval
                        )
        ).thenReturn(
                approval
        );

        ApprovalResponse response =
                service.approve(
                        approverId,
                        approvalId,
                        "  Approved  "
                );

        assertEquals(
                ApprovalStatus.APPROVED,
                response.status()
        );

        assertEquals(
                TicketStatus.OPEN,
                response.ticketStatus()
        );

        assertEquals(
                "Approved",
                response.decisionNote()
        );

        assertEquals(
                approverId,
                response.decidedByUserId()
        );

        assertSame(
                mediumSlaPolicy,
                ticket.getSlaPolicy()
        );

        assertNotNull(
                ticket.getResponseDueAt()
        );

        assertNotNull(
                ticket.getResolutionDueAt()
        );

        assertEquals(
                1380L,
                Duration.between(
                        ticket.getResponseDueAt(),
                        ticket.getResolutionDueAt()
                ).toMinutes()
        );

        verify(auditService)
                .recordUserAction(
                        eq(approver),
                        eq(
                                AuditAction
                                        .APPROVAL_APPROVED
                        ),
                        eq(
                                AuditTargetType
                                        .APPROVAL
                        ),
                        nullable(UUID.class),
                        eq("FD-000030"),
                        anyMap()
                );
    }

    @Test
    void adminCanRejectWithoutStartingSla() {
        UUID approverId =
                UUID.randomUUID();

        UUID requesterId =
                UUID.randomUUID();

        UUID approvalId =
                UUID.randomUUID();

        User requester =
                user(
                        requesterId,
                        "requester@example.com"
                );

        User admin =
                approver(
                        approverId,
                        "admin@example.com",
                        RoleCode.ADMIN
                );

        Ticket ticket =
                serviceRequest(
                        requester,
                        "FD-000031"
                );

        ApprovalRequest approval =
                new ApprovalRequest(
                        ticket,
                        requester,
                        OffsetDateTime.now(
                                ZoneOffset.UTC
                        ).minusHours(1)
                );

        when(
                userRepository.findById(
                        approverId
                )
        ).thenReturn(
                Optional.of(
                        admin
                )
        );

        when(
                approvalRequestRepository
                        .findByIdForUpdate(
                                approvalId
                        )
        ).thenReturn(
                Optional.of(
                        approval
                )
        );

        when(
                ticketRepository.saveAndFlush(
                        ticket
                )
        ).thenReturn(
                ticket
        );

        when(
                approvalRequestRepository
                        .saveAndFlush(
                                approval
                        )
        ).thenReturn(
                approval
        );

        ApprovalResponse response =
                service.reject(
                        approverId,
                        approvalId,
                        "  Budget not approved  "
                );

        assertEquals(
                ApprovalStatus.REJECTED,
                response.status()
        );

        assertEquals(
                TicketStatus.CANCELLED,
                response.ticketStatus()
        );

        assertEquals(
                "Budget not approved",
                response.decisionNote()
        );

        assertNull(
                ticket.getSlaPolicy()
        );

        assertNull(
                ticket.getResponseDueAt()
        );

        assertNull(
                ticket.getResolutionDueAt()
        );

        verifyNoInteractions(
                slaPolicyRepository
        );

        verify(auditService)
                .recordUserAction(
                        eq(admin),
                        eq(
                                AuditAction
                                        .APPROVAL_REJECTED
                        ),
                        eq(
                                AuditTargetType
                                        .APPROVAL
                        ),
                        nullable(UUID.class),
                        eq("FD-000031"),
                        anyMap()
                );
    }

    @Test
    void employeeCannotApprove() {
        UUID employeeId =
                UUID.randomUUID();

        User employee =
                approver(
                        employeeId,
                        "employee@example.com",
                        RoleCode.EMPLOYEE
                );

        when(
                userRepository.findById(
                        employeeId
                )
        ).thenReturn(
                Optional.of(
                        employee
                )
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.approve(
                                        employeeId,
                                        UUID.randomUUID(),
                                        null
                                )
                );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                approvalRequestRepository
        );

        verifyNoInteractions(
                slaPolicyRepository
        );

        verifyNoInteractions(
                auditService
        );
    }

    @Test
    void requesterCannotApproveOwnRequest() {
        UUID userId =
                UUID.randomUUID();

        UUID approvalId =
                UUID.randomUUID();

        User requesterAndLead =
                approver(
                        userId,
                        "lead@example.com",
                        RoleCode.TEAM_LEAD
                );

        Ticket ticket =
                serviceRequest(
                        requesterAndLead,
                        "FD-000032"
                );

        ApprovalRequest approval =
                new ApprovalRequest(
                        ticket,
                        requesterAndLead,
                        OffsetDateTime.now(
                                ZoneOffset.UTC
                        ).minusHours(1)
                );

        when(
                userRepository.findById(
                        userId
                )
        ).thenReturn(
                Optional.of(
                        requesterAndLead
                )
        );

        when(
                approvalRequestRepository
                        .findByIdForUpdate(
                                approvalId
                        )
        ).thenReturn(
                Optional.of(
                        approval
                )
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.approve(
                                        userId,
                                        approvalId,
                                        null
                                )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                ApprovalStatus.PENDING,
                approval.getStatus()
        );

        assertEquals(
                TicketStatus.PENDING_APPROVAL,
                ticket.getStatus()
        );

        verifyNoInteractions(
                slaPolicyRepository
        );

        verifyNoInteractions(
                auditService
        );
    }

    @Test
    void alreadyDecidedApprovalReturnsConflict() {
        UUID requesterId =
                UUID.randomUUID();

        UUID firstApproverId =
                UUID.randomUUID();

        UUID secondApproverId =
                UUID.randomUUID();

        UUID approvalId =
                UUID.randomUUID();

        User requester =
                user(
                        requesterId,
                        "requester@example.com"
                );

        User firstApprover =
                user(
                        firstApproverId,
                        "first@example.com"
                );

        User secondApprover =
                approver(
                        secondApproverId,
                        "second@example.com",
                        RoleCode.ADMIN
                );

        Ticket ticket =
                serviceRequest(
                        requester,
                        "FD-000033"
                );

        OffsetDateTime requestedAt =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                ).minusHours(2);

        ApprovalRequest approval =
                new ApprovalRequest(
                        ticket,
                        requester,
                        requestedAt
                );

        approval.approve(
                firstApprover,
                null,
                requestedAt.plusMinutes(10)
        );

        when(
                userRepository.findById(
                        secondApproverId
                )
        ).thenReturn(
                Optional.of(
                        secondApprover
                )
        );

        when(
                approvalRequestRepository
                        .findByIdForUpdate(
                                approvalId
                        )
        ).thenReturn(
                Optional.of(
                        approval
                )
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.reject(
                                        secondApproverId,
                                        approvalId,
                                        "Reject instead"
                                )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                ApprovalStatus.APPROVED,
                approval.getStatus()
        );

        verify(
                ticketRepository,
                never()
        ).saveAndFlush(
                any(Ticket.class)
        );

        verifyNoInteractions(
                slaPolicyRepository
        );
    }

    @Test
    void pendingApprovalsAreReturnedOldestFirst() {
        UUID approverId =
                UUID.randomUUID();

        User teamLead =
                approver(
                        approverId,
                        "lead@example.com",
                        RoleCode.TEAM_LEAD
                );

        User requester =
                user(
                        UUID.randomUUID(),
                        "requester@example.com"
                );

        Ticket ticket =
                serviceRequest(
                        requester,
                        "FD-000034"
                );

        ApprovalRequest approval =
                new ApprovalRequest(
                        ticket,
                        requester,
                        OffsetDateTime.parse(
                                "2026-09-26T08:00:00Z"
                        )
                );

        when(
                userRepository.findById(
                        approverId
                )
        ).thenReturn(
                Optional.of(
                        teamLead
                )
        );

        when(
                approvalRequestRepository
                        .findByStatusOrderByRequestedAtAsc(
                                eq(
                                        ApprovalStatus.PENDING
                                ),
                                any()
                        )
        ).thenReturn(
                new PageImpl<>(
                        List.of(
                                approval
                        ),
                        PageRequest.of(
                                0,
                                10
                        ),
                        1
                )
        );

        PageResponse<ApprovalResponse> response =
                service.getPendingApprovals(
                        approverId,
                        0,
                        10
                );

        assertEquals(
                1,
                response.content().size()
        );

        assertEquals(
                ApprovalStatus.PENDING,
                response.content()
                        .getFirst()
                        .status()
        );

        assertEquals(
                TicketStatus.PENDING_APPROVAL,
                response.content()
                        .getFirst()
                        .ticketStatus()
        );

        assertEquals(
                "FD-000034",
                response.content()
                        .getFirst()
                        .ticketNumber()
        );

        assertNull(
                response.content()
                        .getFirst()
                        .responseDueAt()
        );

        assertNull(
                response.content()
                        .getFirst()
                        .resolutionDueAt()
        );
    }

    private Ticket serviceRequest(
            User requester,
            String ticketNumber
    ) {
        return new Ticket(
                ticketNumber,
                TicketType.SERVICE_REQUEST,
                "Software access request",
                "Access is required for work.",
                requester
        );
    }

    private User user(
        UUID id,
        String email
) {
    User user =
            org.mockito.Mockito.mock(
                    User.class
            );

    org.mockito.Mockito
            .lenient()
            .when(
                    user.getId()
            )
            .thenReturn(
                    id
            );

    org.mockito.Mockito
            .lenient()
            .when(
                    user.getEmail()
            )
            .thenReturn(
                    email
            );

    return user;
}

    private User approver(
            UUID id,
            String email,
            RoleCode roleCode
    ) {
        User user =
                user(
                        id,
                        email
                );

        Role role =
                org.mockito.Mockito.mock(
                        Role.class
                );

        when(role.getCode())
                .thenReturn(
                        roleCode
                );

        when(user.getRoles())
                .thenReturn(
                        Set.of(
                                role
                        )
                );

        return user;
    }

    private void configureMediumSlaPolicy() {
        when(
                slaPolicyRepository
                        .findByPriorityAndActiveTrue(
                                TicketPriority.MEDIUM
                        )
        ).thenReturn(
                Optional.of(
                        mediumSlaPolicy
                )
        );

        when(
                mediumSlaPolicy.isActive()
        ).thenReturn(
                true
        );

        when(
                mediumSlaPolicy.getPriority()
        ).thenReturn(
                TicketPriority.MEDIUM
        );

        when(
                mediumSlaPolicy.getResponseMinutes()
        ).thenReturn(
                60
        );

        when(
                mediumSlaPolicy.getResolutionMinutes()
        ).thenReturn(
                1440
        );
    }
}