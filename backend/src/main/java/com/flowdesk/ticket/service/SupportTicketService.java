package com.flowdesk.ticket.service;

import com.flowdesk.common.dto.PageResponse;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.dto.SupportTicketResponse;
import com.flowdesk.ticket.dto.SupportTicketSummaryResponse;
import com.flowdesk.ticket.dto.UpdateTicketStatusRequest;
import com.flowdesk.ticket.repository.TicketAssignmentRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@Service
public class SupportTicketService {

    private final TicketAssignmentRepository
            ticketAssignmentRepository;

    public SupportTicketService(
            TicketAssignmentRepository
                    ticketAssignmentRepository
    ) {
        this.ticketAssignmentRepository =
                ticketAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupportTicketSummaryResponse>
            getMyAssignedTickets(
                    UUID supportEngineerId,
                    int page,
                    int size
            ) {

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Direction.DESC,
                        "assignedAt"
                )
        );

        Page<TicketAssignment> assignments =
                ticketAssignmentRepository
                        .findByAssignedToUser_IdAndReleasedAtIsNull(
                                supportEngineerId,
                                pageable
                        );

        Page<SupportTicketSummaryResponse> responsePage =
                assignments.map(
                        this::toSummaryResponse
                );

        return new PageResponse<>(
                responsePage.getContent(),
                responsePage.getNumber(),
                responsePage.getSize(),
                responsePage.getTotalElements(),
                responsePage.getTotalPages(),
                responsePage.isFirst(),
                responsePage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public SupportTicketResponse getMyAssignedTicket(
            UUID supportEngineerId,
            String ticketNumber
    ) {

        String normalizedTicketNumber =
                normalizeTicketNumber(ticketNumber);

        TicketAssignment assignment =
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                normalizedTicketNumber,
                                supportEngineerId
                        )
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Ticket not found"
                                )
                        );

        return toResponse(assignment);
    }

    @Transactional
    public SupportTicketResponse updateStatus(
            UUID supportEngineerId,
            String ticketNumber,
            UpdateTicketStatusRequest request
    ) {

        String normalizedTicketNumber =
                normalizeTicketNumber(ticketNumber);

        TicketAssignment assignment =
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                normalizedTicketNumber,
                                supportEngineerId
                        )
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Ticket not found"
                                )
                        );

        Ticket ticket =
                assignment.getTicket();

        try {
            ticket.transitionSupportStatus(
                    request.status()
            );
        }
        catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage()
            );
        }
        catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }

        if (ticket.getStatus() == TicketStatus.RESOLVED) {
            assignment.release();
        }

        ticketAssignmentRepository.flush();

        return toResponse(assignment);
    }

    private SupportTicketSummaryResponse toSummaryResponse(
            TicketAssignment assignment
    ) {

        Ticket ticket =
                assignment.getTicket();

        return new SupportTicketSummaryResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getType(),
                ticket.getTitle(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getCreatedAt(),
                assignment.getAssignedAt()
        );
    }

    private SupportTicketResponse toResponse(
            TicketAssignment assignment
    ) {

        Ticket ticket =
                assignment.getTicket();

        return new SupportTicketResponse(
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
                ticket.getUpdatedAt(),
                assignment.getAssignedAt(),
                ticket.getResolvedAt()
        );
    }

    private String normalizeTicketNumber(
            String ticketNumber
    ) {

        if (ticketNumber == null
                || ticketNumber.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ticket number is required"
            );
        }

        return ticketNumber
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}