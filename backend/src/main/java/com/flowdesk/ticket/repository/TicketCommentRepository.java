package com.flowdesk.ticket.repository;

import com.flowdesk.ticket.domain.TicketComment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketCommentRepository
        extends JpaRepository<TicketComment, UUID> {

    @EntityGraph(attributePaths = "authorUser")
    List<TicketComment>
            findByTicket_IdOrderByCreatedAtAsc(
                    UUID ticketId
            );
}