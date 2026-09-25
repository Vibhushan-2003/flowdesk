package com.flowdesk.audit.domain;

import com.flowdesk.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "actor_type",
            nullable = false,
            length = 20,
            updatable = false
    )
    private AuditActorType actorType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "actor_user_id",
            updatable = false
    )
    private User actorUser;

    @Column(
            name = "actor_email",
            length = 320,
            updatable = false
    )
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "action",
            nullable = false,
            length = 60,
            updatable = false
    )
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "target_type",
            nullable = false,
            length = 40,
            updatable = false
    )
    private AuditTargetType targetType;

    @Column(
            name = "target_id",
            nullable = false,
            updatable = false
    )
    private UUID targetId;

    @Column(
            name = "target_reference",
            length = 100,
            updatable = false
    )
    private String targetReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "metadata",
            nullable = false,
            columnDefinition = "jsonb",
            updatable = false
    )
    private Map<String, Object> metadata;

    @Column(
            name = "occurred_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime occurredAt;

    protected AuditLog() {
    }

    private AuditLog(
            AuditActorType actorType,
            User actorUser,
            String actorEmail,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String targetReference,
            Map<String, Object> metadata,
            OffsetDateTime occurredAt
    ) {
        this.actorType =
                Objects.requireNonNull(
                        actorType,
                        "Audit actor type is required"
                );

        this.actorUser = actorUser;

        this.actorEmail =
                normalizeNullableText(
                        actorEmail
                );

        this.action =
                Objects.requireNonNull(
                        action,
                        "Audit action is required"
                );

        this.targetType =
                Objects.requireNonNull(
                        targetType,
                        "Audit target type is required"
                );

        this.targetId =
                Objects.requireNonNull(
                        targetId,
                        "Audit target id is required"
                );

        this.targetReference =
                normalizeNullableText(
                        targetReference
                );

        this.metadata =
                copyMetadata(metadata);

        this.occurredAt =
                Objects.requireNonNull(
                        occurredAt,
                        "Audit occurrence time is required"
                ).withOffsetSameInstant(
                        ZoneOffset.UTC
                );

        validateActor();
    }

    public static AuditLog userAction(
            User actorUser,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String targetReference,
            Map<String, Object> metadata,
            OffsetDateTime occurredAt
    ) {
        if (actorUser == null) {
            throw new IllegalArgumentException(
                    "Audit user actor is required"
            );
        }

        if (actorUser.getId() == null) {
            throw new IllegalArgumentException(
                    "Audit user actor must be persisted"
            );
        }

        String actorEmail =
                normalizeNullableText(
                        actorUser.getEmail()
                );

        if (actorEmail == null) {
            throw new IllegalArgumentException(
                    "Audit user actor email is required"
            );
        }

        return new AuditLog(
                AuditActorType.USER,
                actorUser,
                actorEmail,
                action,
                targetType,
                targetId,
                targetReference,
                metadata,
                occurredAt
        );
    }

    public static AuditLog systemAction(
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String targetReference,
            Map<String, Object> metadata,
            OffsetDateTime occurredAt
    ) {
        return new AuditLog(
                AuditActorType.SYSTEM,
                null,
                null,
                action,
                targetType,
                targetId,
                targetReference,
                metadata,
                occurredAt
        );
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    private void validateActor() {

        if (actorType == AuditActorType.USER) {

            if (actorUser == null) {
                throw new IllegalArgumentException(
                        "USER audit actor requires actor user"
                );
            }

            if (actorUser.getId() == null) {
                throw new IllegalArgumentException(
                        "USER audit actor must be persisted"
                );
            }

            if (actorEmail == null) {
                throw new IllegalArgumentException(
                        "USER audit actor requires actor email"
                );
            }

            return;
        }

        if (actorUser != null || actorEmail != null) {
            throw new IllegalArgumentException(
                    "SYSTEM audit actor cannot have user details"
            );
        }
    }

    private static Map<String, Object> copyMetadata(
            Map<String, Object> metadata
    ) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }

        return Map.copyOf(
                new LinkedHashMap<>(
                        metadata
                )
        );
    }

    private static String normalizeNullableText(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized =
                value.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        return normalized;
    }

    public UUID getId() {
        return id;
    }

    public AuditActorType getActorType() {
        return actorType;
    }

    public User getActorUser() {
        return actorUser;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditTargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getTargetReference() {
        return targetReference;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}