package com.flowdesk.ticket.repository;

import com.flowdesk.ticket.domain.TicketIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TicketIdempotencyRepository extends JpaRepository<TicketIdempotencyRecord, UUID> {

    @Query("""
            SELECT r FROM TicketIdempotencyRecord r
            LEFT JOIN FETCH r.ticket
            WHERE r.userId = :userId AND r.idempotencyKey = :idempotencyKey
            """)
    Optional<TicketIdempotencyRecord> findByUserIdAndIdempotencyKey(
            @Param("userId") UUID userId,
            @Param("idempotencyKey") String idempotencyKey
    );
}

