package com.flowdesk.ticket.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;

import com.flowdesk.common.dto.PageResponse;

import com.flowdesk.notification.domain.NotificationType;
import com.flowdesk.notification.service.NotificationService;

import com.flowdesk.sla.domain.SlaPolicy;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;

import com.flowdesk.ticket.dto.ResolveTicketRequest;
import com.flowdesk.ticket.dto.SupportTicketResponse;
import com.flowdesk.ticket.dto.SupportTicketSummaryResponse;
import com.flowdesk.ticket.dto.UpdateTicketStatusRequest;
import com.flowdesk.ticket.dto.UpdateTicketPriorityRequest;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.repository.UserRepository;
import com.flowdesk.sla.repository.SlaPolicyRepository;
import com.flowdesk.sla.repository.SlaEventRepository;
import java.util.Set;


import com.flowdesk.ticket.repository.TicketAssignmentRepository;

import com.flowdesk.user.domain.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.http.HttpStatus;

import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTest {

    @Mock
    private TicketAssignmentRepository
            ticketAssignmentRepository;

    @Mock
    private NotificationService
            notificationService;

    @Mock
    private AuditService
            auditService;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SlaPolicyRepository slaPolicyRepository;

    @Mock
    private SlaEventRepository slaEventRepository;

    @InjectMocks
    private SupportTicketService
            supportTicketService;

    @Test
    void getMyAssignedTicketsReturnsActiveAssignments() {

        UUID supportEngineerId =
                UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();

        when(
                ticketAssignmentRepository
                        .findByAssignedToUser_IdAndReleasedAtIsNull(
                                eq(supportEngineerId),
                                org.mockito.ArgumentMatchers
                                        .any(Pageable.class)
                        )
        ).thenReturn(
                new PageImpl<>(
                        List.of(
                                assignment
                        ),
                        PageRequest.of(
                                0,
                                10
                        ),
                        1
                )
        );

        PageResponse<SupportTicketSummaryResponse> response =
                supportTicketService
                        .getMyAssignedTickets(
                                supportEngineerId,
                                0,
                                10
                        );

        assertNotNull(
                response
        );

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(
                        Pageable.class
                );

        verify(
                ticketAssignmentRepository
        ).findByAssignedToUser_IdAndReleasedAtIsNull(
                eq(supportEngineerId),
                pageableCaptor.capture()
        );

        Pageable pageable =
                pageableCaptor.getValue();

        assertEquals(
                0,
                pageable.getPageNumber()
        );

        assertEquals(
                10,
                pageable.getPageSize()
        );

        Sort.Order assignedAtOrder =
                pageable.getSort()
                        .getOrderFor(
                                "assignedAt"
                        );

        assertNotNull(
                assignedAtOrder
        );

        assertEquals(
                Sort.Direction.DESC,
                assignedAtOrder
                        .getDirection()
        );
    }

    @Test
    void getMyTicketHistoryReturnsAuthorizedHistory() {

        UUID supportEngineerId =
                UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();
        Ticket ticket = assignment.getTicket();
        ticket.transitionSupportStatus(TicketStatus.IN_PROGRESS);
        ticket.resolve("Fixed");
        assignment.release();

        when(
                ticketAssignmentRepository
                        .searchMyHistoryAssignments(
                                eq(supportEngineerId),
                                eq("%printer%"),
                                eq(TicketStatus.RESOLVED),
                                eq(TicketPriority.MEDIUM),
                                org.mockito.ArgumentMatchers.any(Pageable.class)
                        )
        ).thenReturn(
                new PageImpl<>(
                        List.of(assignment),
                        PageRequest.of(0, 10),
                        1
                )
        );

        PageResponse<SupportTicketSummaryResponse> response =
                supportTicketService.getMyTicketHistory(
                        supportEngineerId,
                        0,
                        10,
                        "  printer  ",
                        TicketStatus.RESOLVED,
                        TicketPriority.MEDIUM,
                        "newest"
                );

        assertEquals(
                1,
                response.totalElements()
        );
        assertEquals(
                "FD-TEST-001",
                response.content().get(0).ticketNumber()
        );
        assertEquals(
                TicketStatus.RESOLVED,
                response.content().get(0).status()
        );
    }

    @Test
    void getMyTicketHistoryRejectsInvalidPaginationAndSort() {

        UUID supportEngineerId =
                UUID.randomUUID();

        assertThrows(
                ResponseStatusException.class,
                () -> supportTicketService.getMyTicketHistory(
                        supportEngineerId,
                        -1,
                        10,
                        null,
                        null,
                        null,
                        "newest"
                )
        );

        assertThrows(
                ResponseStatusException.class,
                () -> supportTicketService.getMyTicketHistory(
                        supportEngineerId,
                        0,
                        101,
                        null,
                        null,
                        null,
                        "newest"
                )
        );

        assertThrows(
                ResponseStatusException.class,
                () -> supportTicketService.getMyTicketHistory(
                        supportEngineerId,
                        0,
                        10,
                        null,
                        null,
                        null,
                        "invalid_sort"
                )
        );
    }

    @Test
    void getMyAssignedTicketNormalizesTicketNumber() {

        UUID supportEngineerId =
                UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();

        when(
                ticketAssignmentRepository
                        .findAuthorizedSupportAssignments(
                                "FD-TEST-001",
                                supportEngineerId
                        )
        ).thenReturn(
                List.of(
                        assignment
                )
        );

        SupportTicketResponse response =
                supportTicketService
                        .getMyAssignedTicket(
                                supportEngineerId,
                                "  fd-test-001  "
                        );

        assertEquals(
                "FD-TEST-001",
                response.ticketNumber()
        );

        assertEquals(
                TicketStatus.ASSIGNED,
                response.status()
        );

        verify(
                ticketAssignmentRepository
        ).findAuthorizedSupportAssignments(
                "FD-TEST-001",
                supportEngineerId
        );
    }

    @Test
    void engineerCannotViewTicketWithoutAuthorization() {

        UUID supportEngineerId =
                UUID.randomUUID();

        when(
                ticketAssignmentRepository
                        .findAuthorizedSupportAssignments(
                                "FD-TEST-001",
                                supportEngineerId
                        )
        ).thenReturn(
                List.of()
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                supportTicketService
                                        .getMyAssignedTicket(
                                                supportEngineerId,
                                                "FD-TEST-001"
                                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }

    @Test
    void resolvingEngineerCanViewResolvedTicketDetails() {

        UUID supportEngineerId =
                UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();
        Ticket ticket = assignment.getTicket();
        ticket.transitionSupportStatus(TicketStatus.IN_PROGRESS);
        ticket.resolve("Service restored successfully.");
        assignment.release();

        when(
                ticketAssignmentRepository
                        .findAuthorizedSupportAssignments(
                                "FD-TEST-001",
                                supportEngineerId
                        )
        ).thenReturn(
                List.of(
                        assignment
                )
        );

        SupportTicketResponse response =
                supportTicketService
                        .getMyAssignedTicket(
                                supportEngineerId,
                                "FD-TEST-001"
                        );

        assertEquals(
                "FD-TEST-001",
                response.ticketNumber()
        );
        assertEquals(
                TicketStatus.RESOLVED,
                response.status()
        );
        assertEquals(
                "Service restored successfully.",
                response.resolutionSummary()
        );
        assertNotNull(
                response.resolvedAt()
        );
    }

    @Test
    void assignedTicketCanBeMovedToInProgress() {

        UUID supportEngineerId =
                UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();

        Ticket ticket =
                assignment.getTicket();

        User employee =
                ticket.getCreatedByUser();

        User supportEngineer =
                assignment.getAssignedToUser();

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                "FD-TEST-001",
                                supportEngineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        SupportTicketResponse response =
                supportTicketService
                        .updateStatus(
                                supportEngineerId,
                                "FD-TEST-001",
                                new UpdateTicketStatusRequest(
                                        TicketStatus.IN_PROGRESS
                                )
                        );

        assertEquals(
                TicketStatus.IN_PROGRESS,
                response.status()
        );

        assertNull(
                assignment.getReleasedAt()
        );

        verify(
                notificationService
        ).createNotification(
                employee,
                supportEngineer,
                ticket,
                NotificationType
                        .TICKET_STATUS_CHANGED,
                "Support work started",
                "A support engineer started working on FD-TEST-001"
        );

        verify(
                auditService
        ).recordUserAction(
                eq(supportEngineer),
                eq(
                        AuditAction
                                .TICKET_STATUS_CHANGED
                ),
                eq(
                        AuditTargetType.TICKET
                ),
                isNull(),
                eq(
                        "FD-TEST-001"
                ),
                eq(
                        Map.of(
                                "fromStatus",
                                "ASSIGNED",
                                "toStatus",
                                "IN_PROGRESS"
                        )
                )
        );
    }

    @Test
    void inProgressTicketCanBeMovedToWaitingForUser() {

        UUID supportEngineerId =
                UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();

        Ticket ticket =
                assignment.getTicket();

        User employee =
                ticket.getCreatedByUser();

        User supportEngineer =
                assignment.getAssignedToUser();

        ticket.transitionSupportStatus(
                TicketStatus.IN_PROGRESS
        );

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                "FD-TEST-001",
                                supportEngineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        SupportTicketResponse response =
                supportTicketService
                        .updateStatus(
                                supportEngineerId,
                                "FD-TEST-001",
                                new UpdateTicketStatusRequest(
                                        TicketStatus.WAITING_FOR_USER
                                )
                        );

        assertEquals(
                TicketStatus.WAITING_FOR_USER,
                response.status()
        );

        assertNull(
                assignment.getReleasedAt()
        );

        verify(
                notificationService
        ).createNotification(
                employee,
                supportEngineer,
                ticket,
                NotificationType
                        .TICKET_STATUS_CHANGED,
                "Waiting for your response",
                "Support is waiting for your response on FD-TEST-001"
        );

        verify(
                auditService
        ).recordUserAction(
                eq(supportEngineer),
                eq(
                        AuditAction
                                .TICKET_STATUS_CHANGED
                ),
                eq(
                        AuditTargetType.TICKET
                ),
                isNull(),
                eq(
                        "FD-TEST-001"
                ),
                eq(
                        Map.of(
                                "fromStatus",
                                "IN_PROGRESS",
                                "toStatus",
                                "WAITING_FOR_USER"
                        )
                )
        );
    }

    @Test
    void waitingForUserTicketCanResumeInProgress() {

        UUID supportEngineerId =
                UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();

        Ticket ticket =
                assignment.getTicket();

        User employee =
                ticket.getCreatedByUser();

        User supportEngineer =
                assignment.getAssignedToUser();

        ticket.transitionSupportStatus(
                TicketStatus.IN_PROGRESS
        );

        ticket.transitionSupportStatus(
                TicketStatus.WAITING_FOR_USER
        );

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                "FD-TEST-001",
                                supportEngineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        SupportTicketResponse response =
                supportTicketService
                        .updateStatus(
                                supportEngineerId,
                                "FD-TEST-001",
                                new UpdateTicketStatusRequest(
                                        TicketStatus.IN_PROGRESS
                                )
                        );

        assertEquals(
                TicketStatus.IN_PROGRESS,
                response.status()
        );

        assertNull(
                assignment.getReleasedAt()
        );

        verify(
                notificationService
        ).createNotification(
                employee,
                supportEngineer,
                ticket,
                NotificationType
                        .TICKET_STATUS_CHANGED,
                "Work resumed",
                "Support resumed work on FD-TEST-001"
        );

        verify(
                auditService
        ).recordUserAction(
                eq(supportEngineer),
                eq(
                        AuditAction
                                .TICKET_STATUS_CHANGED
                ),
                eq(
                        AuditTargetType.TICKET
                ),
                isNull(),
                eq(
                        "FD-TEST-001"
                ),
                eq(
                        Map.of(
                                "fromStatus",
                                "WAITING_FOR_USER",
                                "toStatus",
                                "IN_PROGRESS"
                        )
                )
        );
    }

    @Test
    void invalidStatusTransitionReturnsConflict() {

        UUID supportEngineerId =
                UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                "FD-TEST-001",
                                supportEngineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                supportTicketService
                                        .updateStatus(
                                                supportEngineerId,
                                                "FD-TEST-001",
                                                new UpdateTicketStatusRequest(
                                                        TicketStatus.WAITING_FOR_USER
                                                )
                                        )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                TicketStatus.ASSIGNED,
                assignment.getTicket()
                        .getStatus()
        );

        assertNull(
                assignment.getReleasedAt()
        );

        verifyNoInteractions(
                notificationService,
                auditService
        );
    }

    @Test
    void genericStatusEndpointCannotResolveTicket() {

        UUID supportEngineerId =
                UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();

        assignment.getTicket()
                .transitionSupportStatus(
                        TicketStatus.IN_PROGRESS
                );

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                "FD-TEST-001",
                                supportEngineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                supportTicketService
                                        .updateStatus(
                                                supportEngineerId,
                                                "FD-TEST-001",
                                                new UpdateTicketStatusRequest(
                                                        TicketStatus.RESOLVED
                                                )
                                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        assertEquals(
                "400 BAD_REQUEST \"Use the resolve endpoint to resolve a ticket\"",
                exception.getMessage()
        );

        assertEquals(
                TicketStatus.IN_PROGRESS,
                assignment.getTicket()
                        .getStatus()
        );

        assertNull(
                assignment.getReleasedAt()
        );

        verifyNoInteractions(
                notificationService,
                auditService
        );
    }

    @Test
    void inProgressTicketCanBeResolvedWithSummary() {

        UUID supportEngineerId =
                UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();

        Ticket ticket =
                assignment.getTicket();

        User employee =
                ticket.getCreatedByUser();

        User supportEngineer =
                assignment.getAssignedToUser();

        ticket.transitionSupportStatus(
                TicketStatus.IN_PROGRESS
        );

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                "FD-TEST-001",
                                supportEngineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        SupportTicketResponse response =
                supportTicketService
                        .resolveTicket(
                                supportEngineerId,
                                "FD-TEST-001",
                                new ResolveTicketRequest(
                                        "  Restarted the printer service and restored connectivity.  "
                                )
                        );

        assertEquals(
                TicketStatus.RESOLVED,
                response.status()
        );

        assertNotNull(
                response.resolvedAt()
        );

        assertEquals(
                "Restarted the printer service and restored connectivity.",
                response.resolutionSummary()
        );

        assertEquals(
                "Restarted the printer service and restored connectivity.",
                ticket.getResolutionSummary()
        );

        assertNotNull(
                assignment.getReleasedAt()
        );

        verify(
                notificationService
        ).createNotification(
                employee,
                supportEngineer,
                ticket,
                NotificationType
                        .TICKET_RESOLVED,
                "Ticket resolved",
                "FD-TEST-001 has been resolved"
        );

        verify(
                auditService
        ).recordUserAction(
                eq(supportEngineer),
                eq(
                        AuditAction
                                .TICKET_STATUS_CHANGED
                ),
                eq(
                        AuditTargetType.TICKET
                ),
                isNull(),
                eq(
                        "FD-TEST-001"
                ),
                eq(
                        Map.of(
                                "fromStatus",
                                "IN_PROGRESS",
                                "toStatus",
                                "RESOLVED"
                        )
                )
        );
    }

    @Test
    void blankResolutionSummaryReturnsBadRequest() {

        UUID supportEngineerId =
                UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();

        assignment.getTicket()
                .transitionSupportStatus(
                        TicketStatus.IN_PROGRESS
                );

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                "FD-TEST-001",
                                supportEngineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                supportTicketService
                                        .resolveTicket(
                                                supportEngineerId,
                                                "FD-TEST-001",
                                                new ResolveTicketRequest(
                                                        "   "
                                                )
                                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        assertEquals(
                TicketStatus.IN_PROGRESS,
                assignment.getTicket()
                        .getStatus()
        );

        assertNull(
                assignment.getReleasedAt()
        );

        verifyNoInteractions(
                notificationService,
                auditService
        );
    }

    @Test
    void ticketCannotBeResolvedUnlessInProgress() {

        UUID supportEngineerId =
                UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                "FD-TEST-001",
                                supportEngineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                supportTicketService
                                        .resolveTicket(
                                                supportEngineerId,
                                                "FD-TEST-001",
                                                new ResolveTicketRequest(
                                                        "Resolved"
                                                )
                                        )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        assertEquals(
                TicketStatus.ASSIGNED,
                assignment.getTicket()
                        .getStatus()
        );

        assertNull(
                assignment.getReleasedAt()
        );

        verifyNoInteractions(
                notificationService,
                auditService
        );
    }

    @Test
    void engineerCannotResolveTicketWithoutActiveOwnership() {

        UUID supportEngineerId =
                UUID.randomUUID();

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                "FD-TEST-001",
                                supportEngineerId
                        )
        ).thenReturn(
                Optional.empty()
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                supportTicketService
                                        .resolveTicket(
                                                supportEngineerId,
                                                "FD-TEST-001",
                                                new ResolveTicketRequest(
                                                        "Resolved"
                                                )
                                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                notificationService,
                auditService
        );
    }

    @Test
    void nullStatusReturnsBadRequest() {

        UUID supportEngineerId =
                UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                "FD-TEST-001",
                                supportEngineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                supportTicketService
                                        .updateStatus(
                                                supportEngineerId,
                                                "FD-TEST-001",
                                                new UpdateTicketStatusRequest(
                                                        null
                                                )
                                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        assertEquals(
                TicketStatus.ASSIGNED,
                assignment.getTicket()
                        .getStatus()
        );

        assertNull(
                assignment.getReleasedAt()
        );

        verifyNoInteractions(
                notificationService,
                auditService
        );
    }

    private TicketAssignment
            createAssignedTicketAssignment() {

        User employee =
                mock(User.class);

        User supportEngineer =
                mock(User.class);

        Ticket ticket =
                new Ticket(
                        "FD-TEST-001",
                        TicketType.INCIDENT,
                        "Printer connection issue",
                        "Unable to connect to office printer",
                        employee
                );

        SlaPolicy slaPolicy =
                new SlaPolicy(
                        TicketPriority.MEDIUM,
                        60,
                        1440
                );

        ticket.applySlaPolicy(
                slaPolicy,
                OffsetDateTime.now(
                        ZoneOffset.UTC
                )
        );

        ticket.markAssigned();

        return new TicketAssignment(
                ticket,
                supportEngineer
        );
    }
    @Test
    void updatePriorityWhenAssignedUpdatesPriorityAndRecalculatesSla() {
        UUID engineerId = UUID.randomUUID();
        User engineer = mock(User.class);
        when(engineer.getRoles()).thenReturn(Set.of(new Role(RoleCode.SUPPORT_ENGINEER, "Support Engineer")));
        when(userRepository.findById(engineerId)).thenReturn(Optional.of(engineer));

        TicketAssignment assignment = createAssignedTicketAssignment();
        Ticket ticket = assignment.getTicket();
        String ticketNumber = ticket.getTicketNumber();

        when(ticketAssignmentRepository.findActiveAssignmentForUpdate(eq(ticketNumber), eq(engineerId)))
                .thenReturn(Optional.of(assignment));
        when(slaEventRepository.existsByTicket_Id(ticket.getId())).thenReturn(false);

        SlaPolicy highSlaPolicy = new SlaPolicy(TicketPriority.HIGH, 30, 480);
        when(slaPolicyRepository.findByPriorityAndActiveTrue(TicketPriority.HIGH))
                .thenReturn(Optional.of(highSlaPolicy));

        UpdateTicketPriorityRequest request = new UpdateTicketPriorityRequest(TicketPriority.HIGH);
        SupportTicketResponse response = supportTicketService.updatePriority(engineerId, ticketNumber, request);

        assertNotNull(response);
        assertEquals(TicketPriority.HIGH, response.priority());
        assertEquals(TicketPriority.HIGH, ticket.getPriority());

        verify(ticketRepository).saveAndFlush(ticket);
        verify(auditService).recordUserAction(
                eq(engineer),
                eq(AuditAction.TICKET_PRIORITY_CHANGED),
                eq(AuditTargetType.TICKET),
                eq(ticket.getId()),
                eq(ticket.getTicketNumber()),
                eq(Map.of("fromPriority", "MEDIUM", "toPriority", "HIGH"))
        );
    }

    @Test
    void updatePriorityWhenInProgressUpdatesPriorityAndRecalculatesResolutionSla() {
        UUID engineerId = UUID.randomUUID();
        User engineer = mock(User.class);
        when(engineer.getRoles()).thenReturn(Set.of(new Role(RoleCode.SUPPORT_ENGINEER, "Support Engineer")));
        when(userRepository.findById(engineerId)).thenReturn(Optional.of(engineer));

        TicketAssignment assignment = createAssignedTicketAssignment();
        Ticket ticket = assignment.getTicket();
        ticket.transitionSupportStatus(TicketStatus.IN_PROGRESS);
        String ticketNumber = ticket.getTicketNumber();

        when(ticketAssignmentRepository.findActiveAssignmentForUpdate(eq(ticketNumber), eq(engineerId)))
                .thenReturn(Optional.of(assignment));
        when(slaEventRepository.existsByTicket_Id(ticket.getId())).thenReturn(false);

        SlaPolicy lowSlaPolicy = new SlaPolicy(TicketPriority.LOW, 120, 2880);
        when(slaPolicyRepository.findByPriorityAndActiveTrue(TicketPriority.LOW))
                .thenReturn(Optional.of(lowSlaPolicy));

        UpdateTicketPriorityRequest request = new UpdateTicketPriorityRequest(TicketPriority.LOW);
        SupportTicketResponse response = supportTicketService.updatePriority(engineerId, ticketNumber, request);

        assertNotNull(response);
        assertEquals(TicketPriority.LOW, response.priority());
        assertEquals(TicketPriority.LOW, ticket.getPriority());
    }

    @Test
    void updatePriorityWhenWaitingForUserUpdatesPriority() {
        UUID engineerId = UUID.randomUUID();
        User engineer = mock(User.class);
        when(engineer.getRoles()).thenReturn(Set.of(new Role(RoleCode.SUPPORT_ENGINEER, "Support Engineer")));
        when(userRepository.findById(engineerId)).thenReturn(Optional.of(engineer));

        TicketAssignment assignment = createAssignedTicketAssignment();
        Ticket ticket = assignment.getTicket();
        ticket.transitionSupportStatus(TicketStatus.IN_PROGRESS);
        ticket.transitionSupportStatus(TicketStatus.WAITING_FOR_USER);
        String ticketNumber = ticket.getTicketNumber();

        when(ticketAssignmentRepository.findActiveAssignmentForUpdate(eq(ticketNumber), eq(engineerId)))
                .thenReturn(Optional.of(assignment));
        when(slaEventRepository.existsByTicket_Id(ticket.getId())).thenReturn(false);

        SlaPolicy highSlaPolicy = new SlaPolicy(TicketPriority.HIGH, 30, 480);
        when(slaPolicyRepository.findByPriorityAndActiveTrue(TicketPriority.HIGH))
                .thenReturn(Optional.of(highSlaPolicy));

        UpdateTicketPriorityRequest request = new UpdateTicketPriorityRequest(TicketPriority.HIGH);
        SupportTicketResponse response = supportTicketService.updatePriority(engineerId, ticketNumber, request);

        assertNotNull(response);
        assertEquals(TicketPriority.HIGH, response.priority());
        assertEquals(TicketPriority.HIGH, ticket.getPriority());
    }

    @Test
    void updatePriorityWhenNotAssignedToCallerThrowsNotFound() {
        UUID engineerId = UUID.randomUUID();
        User engineer = mock(User.class);
        when(engineer.getRoles()).thenReturn(Set.of(new Role(RoleCode.SUPPORT_ENGINEER, "Support Engineer")));
        when(userRepository.findById(engineerId)).thenReturn(Optional.of(engineer));

        when(ticketAssignmentRepository.findActiveAssignmentForUpdate(eq("FD-TEST-001"), eq(engineerId)))
                .thenReturn(Optional.empty());

        UpdateTicketPriorityRequest request = new UpdateTicketPriorityRequest(TicketPriority.HIGH);
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> supportTicketService.updatePriority(engineerId, "FD-TEST-001", request)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void updatePriorityWhenTicketNotInActiveStatusThrowsConflict() {
        UUID engineerId = UUID.randomUUID();
        User engineer = mock(User.class);
        when(engineer.getRoles()).thenReturn(Set.of(new Role(RoleCode.SUPPORT_ENGINEER, "Support Engineer")));
        when(userRepository.findById(engineerId)).thenReturn(Optional.of(engineer));

        TicketAssignment assignment = createAssignedTicketAssignment();
        Ticket ticket = assignment.getTicket();
        ticket.transitionSupportStatus(TicketStatus.IN_PROGRESS);
        ticket.resolve("Resolved issue"); // Transitions to RESOLVED

        when(ticketAssignmentRepository.findActiveAssignmentForUpdate(eq(ticket.getTicketNumber()), eq(engineerId)))
                .thenReturn(Optional.of(assignment));

        UpdateTicketPriorityRequest request = new UpdateTicketPriorityRequest(TicketPriority.HIGH);
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> supportTicketService.updatePriority(engineerId, ticket.getTicketNumber(), request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Ticket priority can only be changed while operationally active (ASSIGNED, IN_PROGRESS, or WAITING_FOR_USER)", exception.getReason());
    }

    @Test
    void updatePriorityWhenSlaBreachRecordedThrowsConflict() {
        UUID engineerId = UUID.randomUUID();
        User engineer = mock(User.class);
        when(engineer.getRoles()).thenReturn(Set.of(new Role(RoleCode.SUPPORT_ENGINEER, "Support Engineer")));
        when(userRepository.findById(engineerId)).thenReturn(Optional.of(engineer));

        TicketAssignment assignment = createAssignedTicketAssignment();
        Ticket ticket = assignment.getTicket();

        when(ticketAssignmentRepository.findActiveAssignmentForUpdate(eq(ticket.getTicketNumber()), eq(engineerId)))
                .thenReturn(Optional.of(assignment));
        when(slaEventRepository.existsByTicket_Id(ticket.getId())).thenReturn(true);

        UpdateTicketPriorityRequest request = new UpdateTicketPriorityRequest(TicketPriority.HIGH);
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> supportTicketService.updatePriority(engineerId, ticket.getTicketNumber(), request)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Ticket priority cannot be changed after an SLA breach has occurred or been recorded", exception.getReason());
    }

    @Test
    void updatePriorityWhenTeamLeadUpdatesOtherEngineerTicket() {
        UUID leadId = UUID.randomUUID();
        User teamLead = mock(User.class);
        when(teamLead.getRoles()).thenReturn(Set.of(new Role(RoleCode.TEAM_LEAD, "Team Lead")));
        when(userRepository.findById(leadId)).thenReturn(Optional.of(teamLead));

        TicketAssignment assignment = createAssignedTicketAssignment();
        Ticket ticket = assignment.getTicket();
        String ticketNumber = ticket.getTicketNumber();

        // Lead not assigned directly
        when(ticketAssignmentRepository.findActiveAssignmentForUpdate(eq(ticketNumber), eq(leadId)))
                .thenReturn(Optional.empty());
        when(ticketRepository.findByTicketNumber(eq(ticketNumber))).thenReturn(Optional.of(ticket));
        when(ticketAssignmentRepository.findActiveAssignmentByTicketIdForUpdate(eq(ticket.getId())))
                .thenReturn(Optional.of(assignment));
        when(slaEventRepository.existsByTicket_Id(ticket.getId())).thenReturn(false);

        SlaPolicy criticalSlaPolicy = new SlaPolicy(TicketPriority.CRITICAL, 15, 120);
        when(slaPolicyRepository.findByPriorityAndActiveTrue(TicketPriority.CRITICAL))
                .thenReturn(Optional.of(criticalSlaPolicy));

        UpdateTicketPriorityRequest request = new UpdateTicketPriorityRequest(TicketPriority.CRITICAL);
        SupportTicketResponse response = supportTicketService.updatePriority(leadId, ticketNumber, request);

        assertNotNull(response);
        assertEquals(TicketPriority.CRITICAL, response.priority());
        assertEquals(TicketPriority.CRITICAL, ticket.getPriority());

        verify(ticketRepository).saveAndFlush(ticket);
        verify(auditService).recordUserAction(
                eq(teamLead),
                eq(AuditAction.TICKET_PRIORITY_CHANGED),
                eq(AuditTargetType.TICKET),
                eq(ticket.getId()),
                eq(ticket.getTicketNumber()),
                eq(Map.of("fromPriority", "MEDIUM", "toPriority", "CRITICAL"))
        );
    }
}
