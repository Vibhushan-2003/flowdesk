package com.flowdesk.ticket.domain;

import com.flowdesk.sla.domain.SlaPolicy;
import com.flowdesk.sla.domain.SlaStatus;

import com.flowdesk.user.domain.User;

import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.mock;

class TicketTest {

    @Test
    void assignedTicketCanMoveToInProgress() {
        Ticket ticket =
                createAssignedTicket();

        ticket.transitionSupportStatus(
                TicketStatus.IN_PROGRESS
        );

        assertEquals(
                TicketStatus.IN_PROGRESS,
                ticket.getStatus()
        );

        assertNotNull(
                ticket.getFirstRespondedAt()
        );
    }

    @Test
    void inProgressTicketCanMoveToWaitingForUser() {
        Ticket ticket =
                createInProgressTicket();

        ticket.transitionSupportStatus(
                TicketStatus.WAITING_FOR_USER
        );

        assertEquals(
                TicketStatus.WAITING_FOR_USER,
                ticket.getStatus()
        );
    }

    @Test
    void waitingForUserTicketCanReturnToInProgress() {
        Ticket ticket =
                createInProgressTicket();

        ticket.transitionSupportStatus(
                TicketStatus.WAITING_FOR_USER
        );

        ticket.transitionSupportStatus(
                TicketStatus.IN_PROGRESS
        );

        assertEquals(
                TicketStatus.IN_PROGRESS,
                ticket.getStatus()
        );
    }

    @Test
    void firstResponseTimeShouldNotChangeWhenWorkResumes() {
        Ticket ticket =
                createAssignedTicket();

        ticket.transitionSupportStatus(
                TicketStatus.IN_PROGRESS
        );

        OffsetDateTime firstRespondedAt =
                ticket.getFirstRespondedAt();

        assertNotNull(
                firstRespondedAt
        );

        ticket.transitionSupportStatus(
                TicketStatus.WAITING_FOR_USER
        );

        ticket.transitionSupportStatus(
                TicketStatus.IN_PROGRESS
        );

        assertEquals(
                firstRespondedAt,
                ticket.getFirstRespondedAt()
        );
    }

    @Test
    void slaPolicyShouldCreateDeadlineSnapshot() {
        Ticket ticket =
                createTicket();

        SlaPolicy slaPolicy =
                createMediumSlaPolicy();

        OffsetDateTime slaStartedAt =
                OffsetDateTime.parse(
                        "2026-09-23T04:00:00Z"
                );

        ticket.applySlaPolicy(
                slaPolicy,
                slaStartedAt
        );

        assertSame(
                slaPolicy,
                ticket.getSlaPolicy()
        );

        assertEquals(
                slaStartedAt.plusMinutes(60),
                ticket.getResponseDueAt()
        );

        assertEquals(
                slaStartedAt.plusMinutes(1440),
                ticket.getResolutionDueAt()
        );
    }

    @Test
    void assignedTicketCanChangePriorityWithoutResettingSlaStart() {
        Ticket ticket =
                createTicket();

        OffsetDateTime originalSlaStart =
                OffsetDateTime.parse(
                        "2026-10-13T04:00:00Z"
                );

        ticket.applySlaPolicy(
                createMediumSlaPolicy(),
                originalSlaStart
        );

        ticket.markAssigned();

        SlaPolicy highPolicy =
                new SlaPolicy(
                        TicketPriority.HIGH,
                        30,
                        480
                );

        ticket.applyPriorityAndRecalculateSla(
                TicketPriority.HIGH,
                highPolicy
        );

        assertEquals(
                TicketPriority.HIGH,
                ticket.getPriority()
        );

        assertSame(
                highPolicy,
                ticket.getSlaPolicy()
        );

        assertEquals(
                originalSlaStart.plusMinutes(30),
                ticket.getResponseDueAt()
        );

        assertEquals(
                originalSlaStart.plusMinutes(480),
                ticket.getResolutionDueAt()
        );
    }

    @Test
    void priorityChangeIsRejectedAfterSupportWorkStarts() {
        Ticket ticket =
                createTicket();

        ticket.applySlaPolicy(
                createMediumSlaPolicy(),
                OffsetDateTime.parse(
                        "2026-10-13T04:00:00Z"
                )
        );

        ticket.markAssigned();

        ticket.transitionSupportStatus(
                TicketStatus.IN_PROGRESS
        );

        SlaPolicy highPolicy =
                new SlaPolicy(
                        TicketPriority.HIGH,
                        30,
                        480
                );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                ticket.applyPriorityAndRecalculateSla(
                                        TicketPriority.HIGH,
                                        highPolicy
                                )
                );

        assertEquals(
                "Ticket priority can only be changed while assigned",
                exception.getMessage()
        );

        assertEquals(
                TicketPriority.MEDIUM,
                ticket.getPriority()
        );
    }

    @Test
    void priorityChangeRejectsMismatchedSlaPolicy() {
        Ticket ticket =
                createTicket();

        ticket.applySlaPolicy(
                createMediumSlaPolicy(),
                OffsetDateTime.parse(
                        "2026-10-13T04:00:00Z"
                )
        );

        ticket.markAssigned();

        SlaPolicy criticalPolicy =
                new SlaPolicy(
                        TicketPriority.CRITICAL,
                        15,
                        240
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ticket.applyPriorityAndRecalculateSla(
                                        TicketPriority.HIGH,
                                        criticalPolicy
                                )
                );

        assertEquals(
                "SLA policy priority does not match new ticket priority",
                exception.getMessage()
        );

        assertEquals(
                TicketPriority.MEDIUM,
                ticket.getPriority()
        );
    }

    @Test
    void priorityChangeRequiresExistingSlaSnapshot() {
        Ticket ticket =
                createAssignedTicket();

        SlaPolicy highPolicy =
                new SlaPolicy(
                        TicketPriority.HIGH,
                        30,
                        480
                );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                ticket.applyPriorityAndRecalculateSla(
                                        TicketPriority.HIGH,
                                        highPolicy
                                )
                );

        assertEquals(
                "Ticket SLA is not initialized",
                exception.getMessage()
        );

        assertEquals(
                TicketPriority.MEDIUM,
                ticket.getPriority()
        );
    }

    @Test
    void pendingSlaShouldRemainPendingBeforeDeadlines() {
        Ticket ticket =
                createTicket();

        OffsetDateTime startedAt =
                OffsetDateTime.parse(
                        "2026-09-23T04:00:00Z"
                );

        ticket.applySlaPolicy(
                createMediumSlaPolicy(),
                startedAt
        );

        OffsetDateTime evaluatedAt =
                startedAt.plusMinutes(30);

        assertEquals(
                SlaStatus.PENDING,
                ticket.evaluateResponseSla(
                        evaluatedAt
                )
        );

        assertEquals(
                SlaStatus.PENDING,
                ticket.evaluateResolutionSla(
                        evaluatedAt
                )
        );
    }

    @Test
    void responseSlaShouldBeMetWhenSupportStartsBeforeDeadline() {
        Ticket ticket =
                createTicket();

        OffsetDateTime startedAt =
                OffsetDateTime.now()
                        .minusMinutes(10);

        ticket.applySlaPolicy(
                createMediumSlaPolicy(),
                startedAt
        );

        ticket.markAssigned();

        ticket.transitionSupportStatus(
                TicketStatus.IN_PROGRESS
        );

        assertEquals(
                SlaStatus.MET,
                ticket.evaluateResponseSla(
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void responseSlaShouldBeBreachedWhenSupportStartsAfterDeadline() {
        Ticket ticket =
                createTicket();

        OffsetDateTime startedAt =
                OffsetDateTime.now()
                        .minusHours(2);

        ticket.applySlaPolicy(
                createMediumSlaPolicy(),
                startedAt
        );

        ticket.markAssigned();

        ticket.transitionSupportStatus(
                TicketStatus.IN_PROGRESS
        );

        assertEquals(
                SlaStatus.BREACHED,
                ticket.evaluateResponseSla(
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void unresolvedResolutionSlaShouldBeBreachedAfterDeadline() {
        Ticket ticket =
                createTicket();

        OffsetDateTime startedAt =
                OffsetDateTime.parse(
                        "2026-09-20T04:00:00Z"
                );

        ticket.applySlaPolicy(
                createMediumSlaPolicy(),
                startedAt
        );

        OffsetDateTime evaluatedAt =
                startedAt.plusHours(25);

        assertEquals(
                SlaStatus.BREACHED,
                ticket.evaluateResolutionSla(
                        evaluatedAt
                )
        );
    }

    @Test
    void migratedTicketWithUnknownFirstResponseTimeShouldReturnUnknown() {
        Ticket ticket =
                createTicket();

        OffsetDateTime startedAt =
                OffsetDateTime.parse(
                        "2026-09-20T04:00:00Z"
                );

        ticket.applySlaPolicy(
                createMediumSlaPolicy(),
                startedAt
        );

        ReflectionTestUtils.setField(
                ticket,
                "status",
                TicketStatus.RESOLVED
        );

        ReflectionTestUtils.setField(
                ticket,
                "resolvedAt",
                startedAt.plusHours(5)
        );

        assertEquals(
                SlaStatus.UNKNOWN,
                ticket.evaluateResponseSla(
                        startedAt.plusDays(2)
                )
        );

        assertEquals(
                SlaStatus.MET,
                ticket.evaluateResolutionSla(
                        startedAt.plusDays(2)
                )
        );
    }

    @Test
    void inProgressTicketCanBeResolved() {
        Ticket ticket =
                createInProgressTicket();

        ticket.transitionSupportStatus(
                TicketStatus.RESOLVED
        );

        assertEquals(
                TicketStatus.RESOLVED,
                ticket.getStatus()
        );

        assertNotNull(
                ticket.getResolvedAt()
        );
    }

    @Test
    void assignedTicketCannotJumpDirectlyToResolved() {
        Ticket ticket =
                createAssignedTicket();

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                ticket.transitionSupportStatus(
                                        TicketStatus.RESOLVED
                                )
                );

        assertEquals(
                "Invalid ticket status transition: "
                        + "ASSIGNED -> RESOLVED",
                exception.getMessage()
        );

        assertEquals(
                TicketStatus.ASSIGNED,
                ticket.getStatus()
        );
    }

    @Test
    void waitingForUserTicketCannotBeResolvedDirectly() {
        Ticket ticket =
                createInProgressTicket();

        ticket.transitionSupportStatus(
                TicketStatus.WAITING_FOR_USER
        );

        assertThrows(
                IllegalStateException.class,
                () ->
                        ticket.transitionSupportStatus(
                                TicketStatus.RESOLVED
                        )
        );

        assertEquals(
                TicketStatus.WAITING_FOR_USER,
                ticket.getStatus()
        );
    }

    @Test
    void openTicketCannotUseSupportStatusTransition() {
        Ticket ticket =
                createTicket();

        assertThrows(
                IllegalStateException.class,
                () ->
                        ticket.transitionSupportStatus(
                                TicketStatus.IN_PROGRESS
                        )
        );

        assertEquals(
                TicketStatus.OPEN,
                ticket.getStatus()
        );
    }

    @Test
    void nullStatusIsRejected() {
        Ticket ticket =
                createAssignedTicket();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ticket.transitionSupportStatus(
                                null
                        )
        );

        assertEquals(
                TicketStatus.ASSIGNED,
                ticket.getStatus()
        );
    }

    private SlaPolicy createMediumSlaPolicy() {
        return new SlaPolicy(
                TicketPriority.MEDIUM,
                60,
                1440
        );
    }

    private Ticket createTicket() {
        User user =
                mock(User.class);

        return new Ticket(
                "FD-TEST-001",
                TicketType.INCIDENT,
                "Test ticket",
                "Test description",
                user
        );
    }

    private Ticket createAssignedTicket() {
        Ticket ticket =
                createTicket();

        ticket.markAssigned();

        return ticket;
    }

    private Ticket createInProgressTicket() {
        Ticket ticket =
                createAssignedTicket();

        ticket.transitionSupportStatus(
                TicketStatus.IN_PROGRESS
        );

        return ticket;
    }
}