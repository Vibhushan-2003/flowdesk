package com.flowdesk.audit.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditLog;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.repository.AuditLogRepository;
import com.flowdesk.user.domain.User;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(
            AuditLogRepository auditLogRepository
    ) {
        this.auditLogRepository =
                auditLogRepository;
    }

    @Transactional
    public AuditLog recordUserAction(
            User actorUser,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String targetReference,
            Map<String, Object> metadata
    ) {
        return recordUserAction(
                actorUser,
                action,
                targetType,
                targetId,
                targetReference,
                metadata,
                OffsetDateTime.now(
                        ZoneOffset.UTC
                )
        );
    }

    @Transactional
    public AuditLog recordSystemAction(
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String targetReference,
            Map<String, Object> metadata
    ) {
        return recordSystemAction(
                action,
                targetType,
                targetId,
                targetReference,
                metadata,
                OffsetDateTime.now(
                        ZoneOffset.UTC
                )
        );
    }

    AuditLog recordUserAction(
            User actorUser,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String targetReference,
            Map<String, Object> metadata,
            OffsetDateTime occurredAt
    ) {
        AuditLog auditLog =
                AuditLog.userAction(
                        actorUser,
                        action,
                        targetType,
                        targetId,
                        targetReference,
                        metadata,
                        occurredAt
                );

        return auditLogRepository
                .saveAndFlush(
                        auditLog
                );
    }

    AuditLog recordSystemAction(
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String targetReference,
            Map<String, Object> metadata,
            OffsetDateTime occurredAt
    ) {
        AuditLog auditLog =
                AuditLog.systemAction(
                        action,
                        targetType,
                        targetId,
                        targetReference,
                        metadata,
                        occurredAt
                );

        return auditLogRepository
                .saveAndFlush(
                        auditLog
                );
    }
}