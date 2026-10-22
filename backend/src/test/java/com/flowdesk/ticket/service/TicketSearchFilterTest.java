
package com.flowdesk.ticket.service;

import com.flowdesk.ticket.repository.TicketRepository;
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

class TicketSearchFilterTest {

    private TicketRepository ticketRepository;
    private TicketService ticketService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ticketRepository = mock(TicketRepository.class);

        ticketService = new TicketService(
                ticketRepository,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Test
    void searchShouldIgnoreCaseAndTrimSpaces() {
        when(ticketRepository.searchMyTickets(
                eq(userId),
                eq("%vpn%"),
                isNull(),
                isNull(),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        ticketService.getMyTickets(
                userId,
                0,
                10,
                "  VPN  ",
                null,
                null,
                "newest"
        );

        verify(ticketRepository).searchMyTickets(
                eq(userId),
                eq("%vpn%"),
                isNull(),
                isNull(),
                any(Pageable.class)
        );
    }

    @Test
    void searchShouldEscapeWildcardCharacters() {
        when(ticketRepository.searchMyTickets(
                eq(userId),
                eq("%!%!_!!%"),
                isNull(),
                isNull(),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        ticketService.getMyTickets(
                userId,
                0,
                10,
                "%_!",
                null,
                null,
                "newest"
        );

        verify(ticketRepository).searchMyTickets(
                eq(userId),
                eq("%!%!_!!%"),
                isNull(),
                isNull(),
                any(Pageable.class)
        );
    }

    @Test
    void newestShouldSortDescending() {
        when(ticketRepository.searchMyTickets(
                eq(userId),
                isNull(),
                isNull(),
                isNull(),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        ticketService.getMyTickets(
                userId,
                0,
                10,
                null,
                null,
                null,
                "newest"
        );

        verify(ticketRepository).searchMyTickets(
                eq(userId),
                isNull(),
                isNull(),
                isNull(),
                argThat(pageable ->
                        pageable.getSort()
                                .getOrderFor("createdAt")
                                .getDirection() == Sort.Direction.DESC
                )
        );
    }

    @Test
    void oldestShouldSortAscending() {
        when(ticketRepository.searchMyTickets(
                eq(userId),
                isNull(),
                isNull(),
                isNull(),
                any(Pageable.class)
        )).thenReturn(Page.empty());

        ticketService.getMyTickets(
                userId,
                0,
                10,
                null,
                null,
                null,
                "oldest"
        );

        verify(ticketRepository).searchMyTickets(
                eq(userId),
                isNull(),
                isNull(),
                isNull(),
                argThat(pageable ->
                        pageable.getSort()
                                .getOrderFor("createdAt")
                                .getDirection() == Sort.Direction.ASC
                )
        );
    }

    @Test
    void invalidSortShouldReturnBadRequest() {
        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> ticketService.getMyTickets(
                                userId,
                                0,
                                10,
                                null,
                                null,
                                null,
                                "invalid"
                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verifyNoInteractions(ticketRepository);
    }

    @Test
    void tooLongSearchShouldReturnBadRequest() {
        String search = "a".repeat(101);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> ticketService.getMyTickets(
                                userId,
                                0,
                                10,
                                search,
                                null,
                                null,
                                "newest"
                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verifyNoInteractions(ticketRepository);
    }
}
