package com.flowdesk.ticket.service;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;
import com.flowdesk.ticket.dto.CreateTicketRequest;
import com.flowdesk.ticket.dto.TicketResponse;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private User creator;

    private TicketService ticketService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(
                ticketRepository,
                userRepository
        );

        userId = UUID.randomUUID();
    }

    @Test
    void createTicketShouldGenerateDefaultsAndPersistTicket() {
        CreateTicketRequest request =
                new CreateTicketRequest(
                        TicketType.INCIDENT,
                        "  VPN is not working  ",
                        "  Cannot connect to company VPN.  "
                );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(creator));

        when(creator.getId())
                .thenReturn(userId);

        when(creator.getEmail())
                .thenReturn("employee@example.com");

        when(ticketRepository.getNextTicketNumberSequenceValue())
                .thenReturn(3L);

        when(ticketRepository.saveAndFlush(any(Ticket.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        TicketResponse response =
                ticketService.createTicket(
                        userId,
                        request
                );

        assertEquals(
                "FD-000003",
                response.ticketNumber()
        );

        assertEquals(
                TicketType.INCIDENT,
                response.type()
        );

        assertEquals(
                "VPN is not working",
                response.title()
        );

        assertEquals(
                "Cannot connect to company VPN.",
                response.description()
        );

        assertEquals(
                TicketPriority.MEDIUM,
                response.priority()
        );

        assertEquals(
                TicketStatus.OPEN,
                response.status()
        );

        assertEquals(
                userId,
                response.createdByUserId()
        );

        assertEquals(
                "employee@example.com",
                response.createdByEmail()
        );

        ArgumentCaptor<Ticket> ticketCaptor =
                ArgumentCaptor.forClass(Ticket.class);

        verify(ticketRepository)
                .saveAndFlush(ticketCaptor.capture());

        Ticket savedTicket =
                ticketCaptor.getValue();

        assertEquals(
                "FD-000003",
                savedTicket.getTicketNumber()
        );

        assertEquals(
                TicketPriority.MEDIUM,
                savedTicket.getPriority()
        );

        assertEquals(
                TicketStatus.OPEN,
                savedTicket.getStatus()
        );

        assertSame(
                creator,
                savedTicket.getCreatedByUser()
        );

        verify(userRepository)
                .findById(userId);

        verify(ticketRepository)
                .getNextTicketNumberSequenceValue();
    }
}