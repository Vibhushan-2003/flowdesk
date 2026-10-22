
package com.flowdesk.ticket.service;

import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupportTicketSearchFilterTest {

    private TicketAssignmentRepository repository;
    private SupportTicketService service;

    private final UUID engineerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(TicketAssignmentRepository.class);

        service = new SupportTicketService(
                repository,
                null,
                null
        );
    }

    @Test
    void searchShouldNormalizeText() {
        when(repository.searchMyActiveAssignments(
                eq(engineerId),
                eq("%printer%"),
                isNull(),
                isNull(),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        service.getMyAssignedTickets(
                engineerId,
                0,
                10,
                "  PRINTER ",
                null,
                null,
                "newest"
        );

        verify(repository).searchMyActiveAssignments(
                eq(engineerId),
                eq("%printer%"),
                isNull(),
                isNull(),
                any(Pageable.class)
        );
    }

    @Test
    void statusAndPriorityShouldReachRepository() {
        when(repository.searchMyActiveAssignments(
                eq(engineerId),
                isNull(),
                eq(TicketStatus.IN_PROGRESS),
                eq(TicketPriority.HIGH),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        service.getMyAssignedTickets(
                engineerId,
                0,
                10,
                null,
                TicketStatus.IN_PROGRESS,
                TicketPriority.HIGH,
                "newest"
        );

        verify(repository).searchMyActiveAssignments(
                eq(engineerId),
                isNull(),
                eq(TicketStatus.IN_PROGRESS),
                eq(TicketPriority.HIGH),
                any(Pageable.class)
        );
    }

    @Test
    void oldestShouldSortByAssignmentAscending() {
        when(repository.searchMyActiveAssignments(
                eq(engineerId),
                isNull(),
                isNull(),
                isNull(),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        service.getMyAssignedTickets(
                engineerId,
                0,
                10,
                null,
                null,
                null,
                "oldest"
        );

        verify(repository).searchMyActiveAssignments(
                eq(engineerId),
                isNull(),
                isNull(),
                isNull(),
                argThat(pageable ->
                        pageable.getSort()
                                .getOrderFor("assignedAt")
                                .getDirection() == Sort.Direction.ASC
                )
        );
    }

    @Test
    void invalidSortShouldBeRejected() {
        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.getMyAssignedTickets(
                                engineerId,
                                0,
                                10,
                                null,
                                null,
                                null,
                                "random"
                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verifyNoInteractions(repository);
    }

    @Test
    void longSearchShouldBeRejected() {
        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.getMyAssignedTickets(
                                engineerId,
                                0,
                                10,
                                "x".repeat(101),
                                null,
                                null,
                                "newest"
                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verifyNoInteractions(repository);
    }
}
