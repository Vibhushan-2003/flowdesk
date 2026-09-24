package com.flowdesk.sla.repository;

import com.flowdesk.sla.domain.SlaEvent;
import com.flowdesk.sla.domain.SlaEventType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface SlaEventRepository
        extends JpaRepository<SlaEvent, UUID> {

    boolean existsByTicket_IdAndEventType(
            UUID ticketId,
            SlaEventType eventType
    );

    /*
     * Atomic idempotency guard.
     *
     * Even if two scheduler executions observe the same
     * breached ticket at the same time, PostgreSQL allows
     * only the first event to be inserted.
     */
    @Modifying
    @Query(
            value = """
                    INSERT INTO sla_events (
                        id,
                        ticket_id,
                        event_type,
                        occurred_at
                    )
                    VALUES (
                        :id,
                        :ticketId,
                        :eventType,
                        :occurredAt
                    )
                    ON CONFLICT (
                        ticket_id,
                        event_type
                    )
                    DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("ticketId") UUID ticketId,
            @Param("eventType") String eventType,
            @Param("occurredAt") OffsetDateTime occurredAt
    );
}
