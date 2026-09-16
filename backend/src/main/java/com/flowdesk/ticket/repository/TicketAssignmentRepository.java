package com.flowdesk.ticket.repository;

import com.flowdesk.ticket.domain.TicketAssignment;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TicketAssignmentRepository
        extends JpaRepository<TicketAssignment, UUID> {

    Optional<TicketAssignment>
            findByTicket_IdAndReleasedAtIsNull(
                    UUID ticketId
            );

    Page<TicketAssignment>
            findByAssignedToUser_IdAndReleasedAtIsNull(
                    UUID assignedToUserId,
                    Pageable pageable
            );

    Optional<TicketAssignment>
            findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                    String ticketNumber,
                    UUID assignedToUserId
            );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT assignment
            FROM TicketAssignment assignment
            WHERE assignment.ticket.ticketNumber = :ticketNumber
              AND assignment.assignedToUser.id = :assignedToUserId
              AND assignment.releasedAt IS NULL
            """)
    Optional<TicketAssignment>
            findActiveAssignmentForUpdate(
                    @Param("ticketNumber")
                    String ticketNumber,

                    @Param("assignedToUserId")
                    UUID assignedToUserId
            );
}