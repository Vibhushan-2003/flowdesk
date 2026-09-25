package com.flowdesk.sla.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;

import com.flowdesk.notification.domain.NotificationType;
import com.flowdesk.notification.service.NotificationService;

import com.flowdesk.sla.domain.SlaEventType;
import com.flowdesk.sla.repository.SlaEventRepository;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketStatus;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketRepository;

import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaMonitoringServiceTest {

    @Mock
    private TicketRepository
            ticketRepository;

    @Mock
    private SlaEventRepository
            slaEventRepository;

    @Mock
    private TicketAssignmentRepository
            ticketAssignmentRepository;

    @Mock
    private UserRepository
            userRepository;

    @Mock
    private NotificationService
            notificationService;

    @Mock
    private AuditService
            auditService;

    private SlaMonitoringService
            service;

    @BeforeEach
    void setUp() {
        service =
                new SlaMonitoringService(
                        ticketRepository,
                        slaEventRepository,
                        ticketAssignmentRepository,
                        userRepository,
                        notificationService,
                        auditService
                );
    }

    @Test
    void responseBreachNotifiesAssigneeTeamLeadAndAdmin() {

        OffsetDateTime evaluatedAt =
                OffsetDateTime.parse(
                        "2026-09-24T05:00:00Z"
                );

        OffsetDateTime responseDueAt =
                OffsetDateTime.parse(
                        "2026-09-24T04:00:00Z"
                );

        Ticket ticket =
                mock(Ticket.class);

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        User engineer =
                mock(User.class);

        User teamLead =
                mock(User.class);

        User admin =
                mock(User.class);

        UUID ticketId =
                UUID.randomUUID();

        UUID engineerId =
                UUID.randomUUID();

        UUID teamLeadId =
                UUID.randomUUID();

        UUID adminId =
                UUID.randomUUID();

        when(
                ticket.getId()
        ).thenReturn(
                ticketId
        );

        when(
                ticket.getTicketNumber()
        ).thenReturn(
                "FD-000010"
        );

        when(
                ticket.getResponseDueAt()
        ).thenReturn(
                responseDueAt
        );

        when(
                assignment.getAssignedToUser()
        ).thenReturn(
                engineer
        );

        when(
                engineer.getId()
        ).thenReturn(
                engineerId
        );

        when(
                teamLead.getId()
        ).thenReturn(
                teamLeadId
        );

        when(
                admin.getId()
        ).thenReturn(
                adminId
        );

        when(
                ticketRepository
                        .findResponseSlaBreachCandidates(
                                eq(evaluatedAt),
                                eq(
                                        List.of(
                                                TicketStatus.OPEN,
                                                TicketStatus.ASSIGNED
                                        )
                                ),
                                eq(
                                        SlaEventType
                                                .RESPONSE_BREACHED
                                ),
                                any(Pageable.class)
                        )
        ).thenReturn(
                List.of(
                        ticket
                )
        );

        when(
                ticketRepository
                        .findResolutionSlaBreachCandidates(
                                eq(evaluatedAt),
                                any(),
                                eq(
                                        SlaEventType
                                                .RESOLUTION_BREACHED
                                ),
                                any(Pageable.class)
                        )
        ).thenReturn(
                List.of()
        );

        when(
                slaEventRepository
                        .insertIfAbsent(
                                any(UUID.class),
                                eq(ticketId),
                                eq(
                                        "RESPONSE_BREACHED"
                                ),
                                eq(responseDueAt)
                        )
        ).thenReturn(
                1
        );

        when(
                ticketAssignmentRepository
                        .findByTicket_IdAndReleasedAtIsNull(
                                ticketId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        when(
                userRepository
                        .findDistinctByRoles_CodeInAndStatus(
                                Set.of(
                                        RoleCode.TEAM_LEAD,
                                        RoleCode.ADMIN
                                ),
                                UserStatus.ACTIVE
                        )
        ).thenReturn(
                List.of(
                        teamLead,
                        admin
                )
        );

        int createdEvents =
                service.monitorBreaches(
                        evaluatedAt
                );

        assertEquals(
                1,
                createdEvents
        );

        verify(
                auditService
        ).recordSystemAction(
                eq(
                        AuditAction
                                .SLA_RESPONSE_BREACHED
                ),
                eq(
                        AuditTargetType.TICKET
                ),
                eq(
                        ticketId
                ),
                eq(
                        "FD-000010"
                ),
                eq(
                        Map.of(
                                "deadline",
                                "2026-09-24T04:00Z",
                                "eventType",
                                "RESPONSE_BREACHED"
                        )
                )
        );

        verify(
                notificationService
        ).createNotification(
                engineer,
                null,
                ticket,
                NotificationType
                        .SLA_RESPONSE_BREACHED,
                "Response SLA breached",
                "FD-000010 missed its response SLA deadline"
        );

        verify(
                notificationService
        ).createNotification(
                teamLead,
                null,
                ticket,
                NotificationType
                        .SLA_RESPONSE_BREACHED,
                "Response SLA breached",
                "FD-000010 missed its response SLA deadline"
        );

        verify(
                notificationService
        ).createNotification(
                admin,
                null,
                ticket,
                NotificationType
                        .SLA_RESPONSE_BREACHED,
                "Response SLA breached",
                "FD-000010 missed its response SLA deadline"
        );
    }

    @Test
    void resolutionBreachCreatesResolutionNotification() {

        OffsetDateTime evaluatedAt =
                OffsetDateTime.parse(
                        "2026-09-24T05:00:00Z"
                );

        OffsetDateTime resolutionDueAt =
                OffsetDateTime.parse(
                        "2026-09-24T03:00:00Z"
                );

        Ticket ticket =
                mock(Ticket.class);

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        User engineer =
                mock(User.class);

        UUID ticketId =
                UUID.randomUUID();

        UUID engineerId =
                UUID.randomUUID();

        when(
                ticket.getId()
        ).thenReturn(
                ticketId
        );

        when(
                ticket.getTicketNumber()
        ).thenReturn(
                "FD-000011"
        );

        when(
                ticket.getResolutionDueAt()
        ).thenReturn(
                resolutionDueAt
        );

        when(
                assignment.getAssignedToUser()
        ).thenReturn(
                engineer
        );

        when(
                engineer.getId()
        ).thenReturn(
                engineerId
        );

        when(
                ticketRepository
                        .findResponseSlaBreachCandidates(
                                eq(evaluatedAt),
                                any(),
                                eq(
                                        SlaEventType
                                                .RESPONSE_BREACHED
                                ),
                                any(Pageable.class)
                        )
        ).thenReturn(
                List.of()
        );

        when(
                ticketRepository
                        .findResolutionSlaBreachCandidates(
                                eq(evaluatedAt),
                                eq(
                                        List.of(
                                                TicketStatus.OPEN,
                                                TicketStatus.ASSIGNED,
                                                TicketStatus.IN_PROGRESS,
                                                TicketStatus.WAITING_FOR_USER
                                        )
                                ),
                                eq(
                                        SlaEventType
                                                .RESOLUTION_BREACHED
                                ),
                                any(Pageable.class)
                        )
        ).thenReturn(
                List.of(
                        ticket
                )
        );

        when(
                slaEventRepository
                        .insertIfAbsent(
                                any(UUID.class),
                                eq(ticketId),
                                eq(
                                        "RESOLUTION_BREACHED"
                                ),
                                eq(resolutionDueAt)
                        )
        ).thenReturn(
                1
        );

        when(
                ticketAssignmentRepository
                        .findByTicket_IdAndReleasedAtIsNull(
                                ticketId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        when(
                userRepository
                        .findDistinctByRoles_CodeInAndStatus(
                                any(),
                                eq(
                                        UserStatus.ACTIVE
                                )
                        )
        ).thenReturn(
                List.of()
        );

        int createdEvents =
                service.monitorBreaches(
                        evaluatedAt
                );

        assertEquals(
                1,
                createdEvents
        );

        verify(
                auditService
        ).recordSystemAction(
                eq(
                        AuditAction
                                .SLA_RESOLUTION_BREACHED
                ),
                eq(
                        AuditTargetType.TICKET
                ),
                eq(
                        ticketId
                ),
                eq(
                        "FD-000011"
                ),
                eq(
                        Map.of(
                                "deadline",
                                "2026-09-24T03:00Z",
                                "eventType",
                                "RESOLUTION_BREACHED"
                        )
                )
        );

        verify(
                notificationService
        ).createNotification(
                engineer,
                null,
                ticket,
                NotificationType
                        .SLA_RESOLUTION_BREACHED,
                "Resolution SLA breached",
                "FD-000011 missed its resolution SLA deadline"
        );
    }

    @Test
    void duplicateBreachDoesNotCreateNotificationsOrAudit() {

        OffsetDateTime evaluatedAt =
                OffsetDateTime.parse(
                        "2026-09-24T05:00:00Z"
                );

        OffsetDateTime responseDueAt =
                OffsetDateTime.parse(
                        "2026-09-24T04:00:00Z"
                );

        Ticket ticket =
                mock(Ticket.class);

        UUID ticketId =
                UUID.randomUUID();

        when(
                ticket.getId()
        ).thenReturn(
                ticketId
        );

        when(
                ticket.getResponseDueAt()
        ).thenReturn(
                responseDueAt
        );

        when(
                ticketRepository
                        .findResponseSlaBreachCandidates(
                                eq(evaluatedAt),
                                any(),
                                eq(
                                        SlaEventType
                                                .RESPONSE_BREACHED
                                ),
                                any(Pageable.class)
                        )
        ).thenReturn(
                List.of(
                        ticket
                )
        );

        when(
                ticketRepository
                        .findResolutionSlaBreachCandidates(
                                eq(evaluatedAt),
                                any(),
                                eq(
                                        SlaEventType
                                                .RESOLUTION_BREACHED
                                ),
                                any(Pageable.class)
                        )
        ).thenReturn(
                List.of()
        );

        when(
                slaEventRepository
                        .insertIfAbsent(
                                any(UUID.class),
                                eq(ticketId),
                                eq(
                                        "RESPONSE_BREACHED"
                                ),
                                eq(responseDueAt)
                        )
        ).thenReturn(
                0
        );

        int createdEvents =
                service.monitorBreaches(
                        evaluatedAt
                );

        assertEquals(
                0,
                createdEvents
        );

        verifyNoInteractions(
                ticketAssignmentRepository,
                userRepository,
                notificationService,
                auditService
        );
    }

    @Test
    void duplicateOperationalRecipientReceivesOnlyOneNotification() {

        OffsetDateTime evaluatedAt =
                OffsetDateTime.parse(
                        "2026-09-24T05:00:00Z"
                );

        OffsetDateTime responseDueAt =
                OffsetDateTime.parse(
                        "2026-09-24T04:00:00Z"
                );

        Ticket ticket =
                mock(Ticket.class);

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        User engineerAndTeamLead =
                mock(User.class);

        UUID ticketId =
                UUID.randomUUID();

        UUID recipientId =
                UUID.randomUUID();

        when(
                ticket.getId()
        ).thenReturn(
                ticketId
        );

        when(
                ticket.getTicketNumber()
        ).thenReturn(
                "FD-000012"
        );

        when(
                ticket.getResponseDueAt()
        ).thenReturn(
                responseDueAt
        );

        when(
                assignment.getAssignedToUser()
        ).thenReturn(
                engineerAndTeamLead
        );

        when(
                engineerAndTeamLead.getId()
        ).thenReturn(
                recipientId
        );

        when(
                ticketRepository
                        .findResponseSlaBreachCandidates(
                                eq(evaluatedAt),
                                any(),
                                eq(
                                        SlaEventType
                                                .RESPONSE_BREACHED
                                ),
                                any(Pageable.class)
                        )
        ).thenReturn(
                List.of(
                        ticket
                )
        );

        when(
                ticketRepository
                        .findResolutionSlaBreachCandidates(
                                eq(evaluatedAt),
                                any(),
                                eq(
                                        SlaEventType
                                                .RESOLUTION_BREACHED
                                ),
                                any(Pageable.class)
                        )
        ).thenReturn(
                List.of()
        );

        when(
                slaEventRepository
                        .insertIfAbsent(
                                any(UUID.class),
                                eq(ticketId),
                                eq(
                                        "RESPONSE_BREACHED"
                                ),
                                eq(responseDueAt)
                        )
        ).thenReturn(
                1
        );

        when(
                ticketAssignmentRepository
                        .findByTicket_IdAndReleasedAtIsNull(
                                ticketId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        when(
                userRepository
                        .findDistinctByRoles_CodeInAndStatus(
                                any(),
                                eq(
                                        UserStatus.ACTIVE
                                )
                        )
        ).thenReturn(
                List.of(
                        engineerAndTeamLead
                )
        );

        int createdEvents =
                service.monitorBreaches(
                        evaluatedAt
                );

        assertEquals(
                1,
                createdEvents
        );

        verify(
                auditService
        ).recordSystemAction(
                eq(
                        AuditAction
                                .SLA_RESPONSE_BREACHED
                ),
                eq(
                        AuditTargetType.TICKET
                ),
                eq(
                        ticketId
                ),
                eq(
                        "FD-000012"
                ),
                eq(
                        Map.of(
                                "deadline",
                                "2026-09-24T04:00Z",
                                "eventType",
                                "RESPONSE_BREACHED"
                        )
                )
        );

        verify(
                notificationService
        ).createNotification(
                engineerAndTeamLead,
                null,
                ticket,
                NotificationType
                        .SLA_RESPONSE_BREACHED,
                "Response SLA breached",
                "FD-000012 missed its response SLA deadline"
        );
    }

    @Test
    void breachIsRecordedAndAuditedEvenWhenNoOperationalRecipientExists() {

        OffsetDateTime evaluatedAt =
                OffsetDateTime.parse(
                        "2026-09-24T05:00:00Z"
                );

        OffsetDateTime responseDueAt =
                OffsetDateTime.parse(
                        "2026-09-24T04:00:00Z"
                );

        Ticket ticket =
                mock(Ticket.class);

        UUID ticketId =
                UUID.randomUUID();

        when(
                ticket.getId()
        ).thenReturn(
                ticketId
        );

        when(
                ticket.getTicketNumber()
        ).thenReturn(
                "FD-000013"
        );

        when(
                ticket.getResponseDueAt()
        ).thenReturn(
                responseDueAt
        );

        when(
                ticketRepository
                        .findResponseSlaBreachCandidates(
                                eq(evaluatedAt),
                                any(),
                                eq(
                                        SlaEventType
                                                .RESPONSE_BREACHED
                                ),
                                any(Pageable.class)
                        )
        ).thenReturn(
                List.of(
                        ticket
                )
        );

        when(
                ticketRepository
                        .findResolutionSlaBreachCandidates(
                                eq(evaluatedAt),
                                any(),
                                eq(
                                        SlaEventType
                                                .RESOLUTION_BREACHED
                                ),
                                any(Pageable.class)
                        )
        ).thenReturn(
                List.of()
        );

        when(
                slaEventRepository
                        .insertIfAbsent(
                                any(UUID.class),
                                eq(ticketId),
                                eq(
                                        "RESPONSE_BREACHED"
                                ),
                                eq(responseDueAt)
                        )
        ).thenReturn(
                1
        );

        when(
                ticketAssignmentRepository
                        .findByTicket_IdAndReleasedAtIsNull(
                                ticketId
                        )
        ).thenReturn(
                Optional.empty()
        );

        when(
                userRepository
                        .findDistinctByRoles_CodeInAndStatus(
                                any(),
                                eq(
                                        UserStatus.ACTIVE
                                )
                        )
        ).thenReturn(
                List.of()
        );

        int createdEvents =
                service.monitorBreaches(
                        evaluatedAt
                );

        assertEquals(
                1,
                createdEvents
        );

        verify(
                auditService
        ).recordSystemAction(
                eq(
                        AuditAction
                                .SLA_RESPONSE_BREACHED
                ),
                eq(
                        AuditTargetType.TICKET
                ),
                eq(
                        ticketId
                ),
                eq(
                        "FD-000013"
                ),
                eq(
                        Map.of(
                                "deadline",
                                "2026-09-24T04:00Z",
                                "eventType",
                                "RESPONSE_BREACHED"
                        )
                )
        );

        verify(
                notificationService,
                never()
        ).createNotification(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void noCandidatesCreatesNothing() {

        OffsetDateTime evaluatedAt =
                OffsetDateTime.parse(
                        "2026-09-24T05:00:00Z"
                );

        when(
                ticketRepository
                        .findResponseSlaBreachCandidates(
                                eq(evaluatedAt),
                                any(),
                                eq(
                                        SlaEventType
                                                .RESPONSE_BREACHED
                                ),
                                any(Pageable.class)
                        )
        ).thenReturn(
                List.of()
        );

        when(
                ticketRepository
                        .findResolutionSlaBreachCandidates(
                                eq(evaluatedAt),
                                any(),
                                eq(
                                        SlaEventType
                                                .RESOLUTION_BREACHED
                                ),
                                any(Pageable.class)
                        )
        ).thenReturn(
                List.of()
        );

        int createdEvents =
                service.monitorBreaches(
                        evaluatedAt
                );

        assertEquals(
                0,
                createdEvents
        );

        verifyNoInteractions(
                slaEventRepository,
                ticketAssignmentRepository,
                userRepository,
                notificationService,
                auditService
        );
    }
}