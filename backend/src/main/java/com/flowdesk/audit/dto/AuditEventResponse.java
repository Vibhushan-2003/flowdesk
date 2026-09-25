package com.flowdesk.audit.dto;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditActorType;
import com.flowdesk.audit.domain.AuditTargetType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditEventResponse(

        UUID id,

        AuditActorType actorType,

        UUID actorUserId,

        String actorEmail,

        AuditAction action,

        AuditTargetType targetType,

        UUID targetId,

        String targetReference,

        Map<String, Object> metadata,

        OffsetDateTime occurredAt

) {
}