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
                createAssignedTicket();

        OffsetDateTime startedAt =
                OffsetDateTime.now()
                        .minusMinutes(10);

        ticket.applySlaPolicy(
                createMediumSlaPolicy(),
                startedAt
        );

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
                createAssignedTicket();

        OffsetDateTime startedAt =
                OffsetDateTime.now()
                        .minusHours(2);

        ticket.applySlaPolicy(
                createMediumSlaPolicy(),
                startedAt
        );

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
