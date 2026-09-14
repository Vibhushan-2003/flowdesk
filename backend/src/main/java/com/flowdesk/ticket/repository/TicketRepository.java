package com.flowdesk.ticket.repository;

import com.flowdesk.ticket.domain.Ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface TicketRepository
        extends JpaRepository<Ticket, UUID> {

    @Query(
            value = "SELECT nextval('ticket_number_seq')",
            nativeQuery = true
    )
    long getNextTicketNumberSequenceValue();

    Page<Ticket> findByCreatedByUser_Id(
            UUID createdByUserId,
            Pageable pageable
    );

    Optional<Ticket> findByTicketNumberAndCreatedByUser_Id(
            String ticketNumber,
            UUID createdByUserId
    );
}