package com.flowdesk.ticket.service;

import com.flowdesk.approval.domain.ApprovalRequest;
import com.flowdesk.approval.domain.ApprovalStatus;
import com.flowdesk.approval.repository.ApprovalRequestRepository;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;

import com.flowdesk.sla.repository.SlaPolicyRepository;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;

import com.flowdesk.ticket.dto.CreateTicketRequest;
import com.flowdesk.ticket.dto.TicketResponse;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketRepository;

import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceApprovalCreationTest {

    @Mock
    private TicketRepository
            ticketRepository;

    @Mock
    private TicketAssignmentRepository
            ticketAssignmentRepository;

    @Mock
    private UserRepository
            userRepository;

    @Mock
    private SlaPolicyRepository
            slaPolicyRepository;

    @Mock
    private ApprovalRequestRepository
            approvalRequestRepository;

    @Mock
    private AuditService
            auditService;

    @Mock
    private User creator;

    private TicketService ticketService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        ticketService =
                new TicketService(
                        ticketRepository,
                        ticketAssignmentRepository,
                        userRepository,
                        slaPolicyRepository,
                        approvalRequestRepository,
                        auditService
                );

        userId =
                UUID.randomUUID();
    }

    @Test
    void serviceRequestCreatesPendingApprovalWithoutStartingSla() {
        CreateTicketRequest request =
                new CreateTicketRequest(
                        TicketType.SERVICE_REQUEST,
                        "  Request GitHub access  ",
                        "  Access is required for development work.  "
                );

        when(
                userRepository.findById(
                        userId
                )
        ).thenReturn(
                Optional.of(
                        creator
                )
        );

        when(creator.getId())
                .thenReturn(
                        userId
                );

        when(creator.getEmail())
                .thenReturn(
                        "employee@example.com"
                );

        when(
                ticketRepository
                        .getNextTicketNumberSequenceValue()
        ).thenReturn(
                21L
        );

        when(
                ticketRepository.saveAndFlush(
                        any(Ticket.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        when(
                approvalRequestRepository.saveAndFlush(
                        any(ApprovalRequest.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        TicketResponse response =
                ticketService.createTicket(
                        userId,
                        request
                );

        assertEquals(
                "FD-000021",
                response.ticketNumber()
        );

        assertEquals(
                TicketType.SERVICE_REQUEST,
                response.type()
        );

        assertEquals(
                TicketStatus.PENDING_APPROVAL,
                response.status()
        );

        assertNull(
                response.responseDueAt()
        );

        assertNull(
                response.resolutionDueAt()
        );

        ArgumentCaptor<Ticket> ticketCaptor =
                ArgumentCaptor.forClass(
                        Ticket.class
                );

        verify(ticketRepository)
                .saveAndFlush(
                        ticketCaptor.capture()
                );

        Ticket savedTicket =
                ticketCaptor.getValue();

        assertEquals(
                TicketStatus.PENDING_APPROVAL,
                savedTicket.getStatus()
        );

        assertNull(
                savedTicket.getSlaPolicy()
        );

        assertNull(
                savedTicket.getResponseDueAt()
        );

        assertNull(
                savedTicket.getResolutionDueAt()
        );

        ArgumentCaptor<ApprovalRequest>
                approvalCaptor =
                ArgumentCaptor.forClass(
                        ApprovalRequest.class
                );

        verify(
                approvalRequestRepository
        ).saveAndFlush(
                approvalCaptor.capture()
        );

        ApprovalRequest savedApproval =
                approvalCaptor.getValue();

        assertEquals(
                ApprovalStatus.PENDING,
                savedApproval.getStatus()
        );

        assertSame(
                savedTicket,
                savedApproval.getTicket()
        );

        assertSame(
                creator,
                savedApproval.getRequestedByUser()
        );

        verifyNoInteractions(
                slaPolicyRepository
        );

        verify(auditService)
                .recordUserAction(
                        eq(creator),
                        eq(
                                AuditAction
                                        .TICKET_CREATED
                        ),
                        eq(
                                AuditTargetType
                                        .TICKET
                        ),
                        nullable(UUID.class),
                        eq("FD-000021"),
                        anyMap()
                );

        verify(auditService)
                .recordUserAction(
                        eq(creator),
                        eq(
                                AuditAction
                                        .APPROVAL_REQUESTED
                        ),
                        eq(
                                AuditTargetType
                                        .APPROVAL
                        ),
                        nullable(UUID.class),
                        eq("FD-000021"),
                        anyMap()
                );
    }
}