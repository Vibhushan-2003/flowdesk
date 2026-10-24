package com.flowdesk.audit.repository;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditLog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findAllByOrderByOccurredAtDesc(
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT audit
                    FROM AuditLog audit
                    WHERE (:action IS NULL OR audit.action = :action)
                      AND (
                          :searchPattern IS NULL
                          OR LOWER(audit.targetReference) LIKE :searchPattern ESCAPE '!'
                          OR LOWER(audit.actorEmail) LIKE :searchPattern ESCAPE '!'
                      )
                    ORDER BY audit.occurredAt DESC
                    """,
            countQuery = """
                    SELECT COUNT(audit)
                    FROM AuditLog audit
                    WHERE (:action IS NULL OR audit.action = :action)
                      AND (
                          :searchPattern IS NULL
                          OR LOWER(audit.targetReference) LIKE :searchPattern ESCAPE '!'
                          OR LOWER(audit.actorEmail) LIKE :searchPattern ESCAPE '!'
                      )
                    """
    )
    Page<AuditLog> searchAuditEvents(
            @Param("action") AuditAction action,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );
}