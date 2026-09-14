package com.flowdesk.ticket.service;

import com.flowdesk.common.dto.PageResponse;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.dto.CreateTicketRequest;
import com.flowdesk.ticket.dto.TicketResponse;
import com.flowdesk.ticket.dto.TicketSummaryResponse;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;

import jakarta.transaction.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
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

    @Transactional
    public PageResponse<TicketSummaryResponse> getMyTickets(
            UUID authenticatedUserId,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Direction.DESC,
                        "createdAt"
                )
        );

        Page<Ticket> ticketPage =
                ticketRepository.findByCreatedByUser_Id(
                        authenticatedUserId,
                        pageable
                );

        List<TicketSummaryResponse> content =
                ticketPage.getContent()
                        .stream()
                        .map(this::toSummaryResponse)
                        .toList();

        return new PageResponse<>(
                content,
                ticketPage.getNumber(),
                ticketPage.getSize(),
                ticketPage.getTotalElements(),
                ticketPage.getTotalPages(),
                ticketPage.isFirst(),
                ticketPage.isLast()
        );
    }

    @Transactional
    public TicketResponse getMyTicket(
            UUID authenticatedUserId,
            String ticketNumber
    ) {
        String normalizedTicketNumber =
                ticketNumber
                        .trim()
                        .toUpperCase(Locale.ROOT);

        Ticket ticket =
                ticketRepository
                        .findByTicketNumberAndCreatedByUser_Id(
                                normalizedTicketNumber,
                                authenticatedUserId
                        )
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Ticket not found"
                                )
                        );

        return toResponse(ticket);
    }

    private TicketResponse toResponse(
            Ticket ticket
    ) {
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

    private TicketSummaryResponse toSummaryResponse(
            Ticket ticket
    ) {
        return new TicketSummaryResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getType(),
                ticket.getTitle(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}