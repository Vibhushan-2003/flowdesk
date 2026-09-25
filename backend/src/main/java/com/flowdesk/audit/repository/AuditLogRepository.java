package com.flowdesk.audit.repository;

import com.flowdesk.audit.domain.AuditLog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findAllByOrderByOccurredAtDesc(
            Pageable pageable
    );
}