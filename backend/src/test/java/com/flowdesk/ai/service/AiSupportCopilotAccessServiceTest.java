package com.flowdesk.ai.service;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;

import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSupportCopilotAccessServiceTest {

    @Mock
    private TicketAssignmentRepository
            ticketAssignmentRepository;

    private AiSupportCopilotAccessService
            accessService;

    @BeforeEach
    void setUp() {

        accessService =
                new AiSupportCopilotAccessService(
                        ticketAssignmentRepository
                );
    }

    @Test
    void prepareRunReturnsInternalContextForCurrentAssignee() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        Ticket ticket =
                mock(Ticket.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000060",
                                engineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        when(
                assignment.getTicket()
        ).thenReturn(
                ticket
        );

        when(
                ticket.getId()
        ).thenReturn(
                ticketId
        );

        AiSupportCopilotRunContext context =
                accessService
                        .prepareRun(
                                engineerId,
                                "  fd-000060  "
                        );

        assertEquals(
                ticketId,
                context.ticketId()
        );

        assertEquals(
                "FD-000060",
                context.ticketNumber()
        );

        verify(
                ticketAssignmentRepository
        ).findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                "FD-000060",
                engineerId
        );
    }

    @Test
    void prepareRunRejectsTicketWithoutCurrentAssignment() {

        UUID engineerId =
                UUID.randomUUID();

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000061",
                                engineerId
                        )
        ).thenReturn(
                Optional.empty()
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                accessService
                                        .prepareRun(
                                                engineerId,
                                                "FD-000061"
                                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        assertEquals(
                "Ticket not found or it is no longer assigned to you",
                exception.getReason()
        );
    }

    @Test
    void prepareRunRejectsMissingTicketNumberBeforeRepositoryAccess() {

        UUID engineerId =
                UUID.randomUUID();

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                accessService
                                        .prepareRun(
                                                engineerId,
                                                "   "
                                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        assertEquals(
                "Ticket number is required",
                exception.getReason()
        );
    }

    @Test
    void prepareRunRequiresAuthenticatedUser() {

        assertThrows(
                NullPointerException.class,
                () ->
                        accessService
                                .prepareRun(
                                        null,
                                        "FD-000062"
                                )
        );
    }
}