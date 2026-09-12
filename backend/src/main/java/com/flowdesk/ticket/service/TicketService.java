package com.flowdesk.ticket.service;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.dto.CreateTicketRequest;
import com.flowdesk.ticket.dto.TicketResponse;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public TicketService(
            TicketRepository ticketRepository,
            UserRepository userRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TicketResponse createTicket(
            UUID authenticatedUserId,
            CreateTicketRequest request
    ) {
        User creator = userRepository.findById(authenticatedUserId)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Authenticated user no longer exists"
                        )
                );

        long sequenceValue =
                ticketRepository.getNextTicketNumberSequenceValue();

        String ticketNumber =
                "FD-%06d".formatted(sequenceValue);

        Ticket ticket = new Ticket(
                ticketNumber,
                request.type(),
                request.title().trim(),
                request.description().trim(),
                creator
        );

        Ticket savedTicket =
                ticketRepository.saveAndFlush(ticket);

        return toResponse(savedTicket);
    }

    private TicketResponse toResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getType(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getCreatedByUser().getId(),
                ticket.getCreatedByUser().getEmail(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}