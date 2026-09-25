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

import com.flowdesk.ticket.dto.SupportTicketResponse;
import com.flowdesk.ticket.dto.SupportTicketSummaryResponse;
import com.flowdesk.ticket.dto.UpdateTicketStatusRequest;

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
    void getMyAssignedTicketNormalizesTicketNumber() {

        UUID supportEngineerId =
                UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
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
        ).findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                "FD-TEST-001",
                supportEngineerId
        );
    }

    @Test
    void engineerCannotViewTicketWithoutActiveOwnership() {

        UUID supportEngineerId =
                UUID.randomUUID();

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
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
                                                        TicketStatus.RESOLVED
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
    void resolvingTicketReleasesActiveAssignment() {

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
                                        TicketStatus.RESOLVED
                                )
                        );

        assertEquals(
                TicketStatus.RESOLVED,
                response.status()
        );

        assertNotNull(
                response.resolvedAt()
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
}