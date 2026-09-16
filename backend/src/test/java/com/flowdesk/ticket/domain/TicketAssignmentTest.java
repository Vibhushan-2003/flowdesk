package com.flowdesk.ticket.domain;

import com.flowdesk.user.domain.User;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class TicketAssignmentTest {

    @Test
    void activeAssignmentCanBeReleased() {
        TicketAssignment assignment =
                createAssignment();

        assertNull(
                assignment.getReleasedAt()
        );

        assignment.release();

        assertNotNull(
                assignment.getReleasedAt()
        );
    }

    @Test
    void releasedAssignmentCannotBeReleasedAgain() {
        TicketAssignment assignment =
                createAssignment();

        assignment.release();

        assertThrows(
                IllegalStateException.class,
                assignment::release
        );
    }

    private TicketAssignment createAssignment() {
        User employee = mock(User.class);
        User supportEngineer = mock(User.class);

        Ticket ticket = new Ticket(
                "FD-TEST-002",
                TicketType.INCIDENT,
                "Test ticket",
                "Test description",
                employee
        );

        return new TicketAssignment(
                ticket,
                supportEngineer
        );
    }
}