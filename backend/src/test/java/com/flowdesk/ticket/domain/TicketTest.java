package com.flowdesk.ticket.domain;

import com.flowdesk.user.domain.User;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class TicketTest {

    @Test
    void assignedTicketCanMoveToInProgress() {
        Ticket ticket = createAssignedTicket();

        ticket.transitionSupportStatus(
                TicketStatus.IN_PROGRESS
        );

        assertEquals(
                TicketStatus.IN_PROGRESS,
                ticket.getStatus()
        );
    }

    @Test
    void inProgressTicketCanMoveToWaitingForUser() {
        Ticket ticket = createInProgressTicket();

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
        Ticket ticket = createInProgressTicket();

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
    void inProgressTicketCanBeResolved() {
        Ticket ticket = createInProgressTicket();

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
        Ticket ticket = createAssignedTicket();

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> ticket.transitionSupportStatus(
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
        Ticket ticket = createInProgressTicket();

        ticket.transitionSupportStatus(
                TicketStatus.WAITING_FOR_USER
        );

        assertThrows(
                IllegalStateException.class,
                () -> ticket.transitionSupportStatus(
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
        Ticket ticket = createTicket();

        assertThrows(
                IllegalStateException.class,
                () -> ticket.transitionSupportStatus(
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
        Ticket ticket = createAssignedTicket();

        assertThrows(
                IllegalArgumentException.class,
                () -> ticket.transitionSupportStatus(null)
        );

        assertEquals(
                TicketStatus.ASSIGNED,
                ticket.getStatus()
        );
    }

    private Ticket createTicket() {
        User user = mock(User.class);

        return new Ticket(
                "FD-TEST-001",
                TicketType.INCIDENT,
                "Test ticket",
                "Test description",
                user
        );
    }

    private Ticket createAssignedTicket() {
        Ticket ticket = createTicket();

        ticket.markAssigned();

        return ticket;
    }

    private Ticket createInProgressTicket() {
        Ticket ticket = createAssignedTicket();

        ticket.transitionSupportStatus(
                TicketStatus.IN_PROGRESS
        );

        return ticket;
    }
}