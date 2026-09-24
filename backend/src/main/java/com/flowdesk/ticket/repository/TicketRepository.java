package com.flowdesk.ticket.repository;

import com.flowdesk.sla.domain.SlaEventType;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
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

    Page<Ticket> findByStatus(
            TicketStatus status,
            Pageable pageable
    );

    @Query("""
            SELECT ticket
            FROM Ticket ticket
            WHERE ticket.firstRespondedAt IS NULL
              AND ticket.responseDueAt < :evaluatedAt
              AND ticket.status IN :statuses
              AND NOT EXISTS (
                    SELECT slaEvent.id
                    FROM SlaEvent slaEvent
                    WHERE slaEvent.ticket = ticket
                      AND slaEvent.eventType = :eventType
              )
            ORDER BY ticket.responseDueAt ASC
            """)
    List<Ticket> findResponseSlaBreachCandidates(
            @Param("evaluatedAt") OffsetDateTime evaluatedAt,
            @Param("statuses") Collection<TicketStatus> statuses,
            @Param("eventType") SlaEventType eventType,
            Pageable pageable
    );

    @Query("""
            SELECT ticket
            FROM Ticket ticket
            WHERE ticket.resolvedAt IS NULL
              AND ticket.resolutionDueAt < :evaluatedAt
              AND ticket.status IN :statuses
              AND NOT EXISTS (
                    SELECT slaEvent.id
                    FROM SlaEvent slaEvent
                    WHERE slaEvent.ticket = ticket
                      AND slaEvent.eventType = :eventType
              )
            ORDER BY ticket.resolutionDueAt ASC
            """)
    List<Ticket> findResolutionSlaBreachCandidates(
            @Param("evaluatedAt") OffsetDateTime evaluatedAt,
            @Param("statuses") Collection<TicketStatus> statuses,
            @Param("eventType") SlaEventType eventType,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ticket
            FROM Ticket ticket
            WHERE ticket.ticketNumber = :ticketNumber
            """)
    Optional<Ticket> findByTicketNumberForUpdate(
            @Param("ticketNumber") String ticketNumber
    );
}
