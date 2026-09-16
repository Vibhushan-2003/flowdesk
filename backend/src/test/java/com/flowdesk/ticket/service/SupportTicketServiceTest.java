package com.flowdesk.ticket.service;

import com.flowdesk.common.dto.PageResponse;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTest {

    @Mock
    private TicketAssignmentRepository
            ticketAssignmentRepository;

    @InjectMocks
    private SupportTicketService
            supportTicketService;

    @Test
    void getMyAssignedTicketsReturnsActiveAssignments() {
        UUID supportEngineerId = UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();

        when(
                ticketAssignmentRepository
                        .findByAssignedToUser_IdAndReleasedAtIsNull(
                                eq(supportEngineerId),
                                any(Pageable.class)
                        )
        ).thenReturn(
                new PageImpl<>(
                        List.of(assignment),
                        PageRequest.of(0, 10),
                        1
                )
        );

        PageResponse<SupportTicketSummaryResponse> response =
                supportTicketService.getMyAssignedTickets(
                        supportEngineerId,
                        0,
                        10
                );

        assertNotNull(response);

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(ticketAssignmentRepository)
                .findByAssignedToUser_IdAndReleasedAtIsNull(
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
                        .getOrderFor("assignedAt");

        assertNotNull(
                assignedAtOrder
        );

        assertEquals(
                Sort.Direction.DESC,
                assignedAtOrder.getDirection()
        );
    }

    @Test
    void getMyAssignedTicketNormalizesTicketNumber() {
        UUID supportEngineerId = UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-TEST-001",
                                supportEngineerId
                        )
        ).thenReturn(
                Optional.of(assignment)
        );

        SupportTicketResponse response =
                supportTicketService.getMyAssignedTicket(
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

        verify(ticketAssignmentRepository)
                .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                        "FD-TEST-001",
                        supportEngineerId
                );
    }

    @Test
    void engineerCannotViewTicketWithoutActiveOwnership() {
        UUID supportEngineerId = UUID.randomUUID();

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
        UUID supportEngineerId = UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                "FD-TEST-001",
                                supportEngineerId
                        )
        ).thenReturn(
                Optional.of(assignment)
        );

        SupportTicketResponse response =
                supportTicketService.updateStatus(
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
    }

    @Test
    void invalidStatusTransitionReturnsConflict() {
        UUID supportEngineerId = UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                "FD-TEST-001",
                                supportEngineerId
                        )
        ).thenReturn(
                Optional.of(assignment)
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                supportTicketService.updateStatus(
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
                assignment.getTicket().getStatus()
        );

        assertNull(
                assignment.getReleasedAt()
        );
    }

    @Test
    void resolvingTicketReleasesActiveAssignment() {
        UUID supportEngineerId = UUID.randomUUID();

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
                Optional.of(assignment)
        );

        SupportTicketResponse response =
                supportTicketService.updateStatus(
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
    }

    @Test
    void nullStatusReturnsBadRequest() {
        UUID supportEngineerId = UUID.randomUUID();

        TicketAssignment assignment =
                createAssignedTicketAssignment();

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                "FD-TEST-001",
                                supportEngineerId
                        )
        ).thenReturn(
                Optional.of(assignment)
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                supportTicketService.updateStatus(
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
                assignment.getTicket().getStatus()
        );

        assertNull(
                assignment.getReleasedAt()
        );
    }

    private TicketAssignment
            createAssignedTicketAssignment() {

        User employee = mock(User.class);
        User supportEngineer = mock(User.class);

        Ticket ticket = new Ticket(
                "FD-TEST-001",
                TicketType.INCIDENT,
                "Printer connection issue",
                "Unable to connect to office printer",
                employee
        );

        ticket.markAssigned();

        return new TicketAssignment(
                ticket,
                supportEngineer
        );
    }
}