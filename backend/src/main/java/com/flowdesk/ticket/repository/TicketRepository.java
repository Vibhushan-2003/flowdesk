package com.flowdesk.ticket.repository;

import com.flowdesk.sla.domain.SlaEventType;
import com.flowdesk.sla.repository.projection.SlaPriorityBreachCount;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketPriority;
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
    @Query(
        value = """
                SELECT ticket
                FROM Ticket ticket
                WHERE ticket.createdByUser.id = :createdByUserId
                  AND (
                      :searchPattern IS NULL
                      OR LOWER(ticket.ticketNumber)
                          LIKE :searchPattern ESCAPE '!'
                      OR LOWER(ticket.title)
                          LIKE :searchPattern ESCAPE '!'
                  )
                  AND (
                      :status IS NULL
                      OR ticket.status = :status
                  )
                  AND (
                      :priority IS NULL
                      OR ticket.priority = :priority
                  )
                """,
        countQuery = """
                SELECT COUNT(ticket)
                FROM Ticket ticket
                WHERE ticket.createdByUser.id = :createdByUserId
                  AND (
                      :searchPattern IS NULL
                      OR LOWER(ticket.ticketNumber)
                          LIKE :searchPattern ESCAPE '!'
                      OR LOWER(ticket.title)
                          LIKE :searchPattern ESCAPE '!'
                  )
                  AND (
                      :status IS NULL
                      OR ticket.status = :status
                  )
                  AND (
                      :priority IS NULL
                      OR ticket.priority = :priority
                  )
                """
)
Page<Ticket> searchMyTickets(
        @Param("createdByUserId") UUID createdByUserId,
        @Param("searchPattern") String searchPattern,
        @Param("status") TicketStatus status,
        @Param("priority") TicketPriority priority,
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

    long countByStatusIn(
            Collection<TicketStatus> statuses
    );

    @Query("""
            SELECT COUNT(ticket)
            FROM Ticket ticket
            WHERE ticket.firstRespondedAt IS NULL
              AND ticket.responseDueAt < :evaluatedAt
              AND ticket.status IN :statuses
            """)
    long countCurrentResponseSlaBreaches(
            @Param("evaluatedAt") OffsetDateTime evaluatedAt,
            @Param("statuses") Collection<TicketStatus> statuses
    );

    @Query("""
            SELECT COUNT(ticket)
            FROM Ticket ticket
            WHERE ticket.resolvedAt IS NULL
              AND ticket.resolutionDueAt < :evaluatedAt
              AND ticket.status IN :statuses
            """)
    long countCurrentResolutionSlaBreaches(
            @Param("evaluatedAt") OffsetDateTime evaluatedAt,
            @Param("statuses") Collection<TicketStatus> statuses
    );

    @Query("""
            SELECT COUNT(ticket)
            FROM Ticket ticket
            WHERE ticket.firstRespondedAt IS NOT NULL
              AND ticket.responseDueAt IS NOT NULL
            """)
    long countKnownResponseSlaSamples();

    @Query("""
            SELECT COUNT(ticket)
            FROM Ticket ticket
            WHERE ticket.firstRespondedAt IS NOT NULL
              AND ticket.responseDueAt IS NOT NULL
              AND ticket.firstRespondedAt <= ticket.responseDueAt
            """)
    long countMetResponseSlaSamples();

    @Query("""
            SELECT COUNT(ticket)
            FROM Ticket ticket
            WHERE ticket.resolvedAt IS NOT NULL
              AND ticket.resolutionDueAt IS NOT NULL
            """)
    long countKnownResolutionSlaSamples();

    @Query("""
            SELECT COUNT(ticket)
            FROM Ticket ticket
            WHERE ticket.resolvedAt IS NOT NULL
              AND ticket.resolutionDueAt IS NOT NULL
              AND ticket.resolvedAt <= ticket.resolutionDueAt
            """)
    long countMetResolutionSlaSamples();

    @Query("""
            SELECT
                ticket.priority AS priority,
                COUNT(ticket) AS breachCount
            FROM Ticket ticket
            WHERE ticket.status IN :activeStatuses
              AND (
                    (
                        ticket.status IN :responseStatuses
                        AND ticket.firstRespondedAt IS NULL
                        AND ticket.responseDueAt < :evaluatedAt
                    )
                    OR
                    (
                        ticket.resolvedAt IS NULL
                        AND ticket.resolutionDueAt < :evaluatedAt
                    )
              )
            GROUP BY ticket.priority
            """)
    List<SlaPriorityBreachCount>
            countCurrentSlaBreachesByPriority(
                    @Param("evaluatedAt")
                    OffsetDateTime evaluatedAt,
                    @Param("activeStatuses")
                    Collection<TicketStatus> activeStatuses,
                    @Param("responseStatuses")
                    Collection<TicketStatus> responseStatuses
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
