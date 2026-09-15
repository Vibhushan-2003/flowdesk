package com.flowdesk.ticket.repository;

import com.flowdesk.ticket.domain.TicketAssignment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TicketAssignmentRepository
        extends JpaRepository<TicketAssignment, UUID> {

    Optional<TicketAssignment>
            findByTicket_IdAndReleasedAtIsNull(
                    UUID ticketId
            );
}