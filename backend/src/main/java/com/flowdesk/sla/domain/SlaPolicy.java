package com.flowdesk.sla.domain;

import com.flowdesk.ticket.domain.TicketPriority;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sla_policies")
public class SlaPolicy {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private TicketPriority priority;

    @Column(
            name = "response_minutes",
            nullable = false
    )
    private int responseMinutes;

    @Column(
            name = "resolution_minutes",
            nullable = false
    )
    private int resolutionMinutes;

    @Column(nullable = false)
    private boolean active;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private OffsetDateTime updatedAt;

    protected SlaPolicy() {
    }

    public SlaPolicy(
            TicketPriority priority,
            int responseMinutes,
            int resolutionMinutes
    ) {
        this.priority =
                Objects.requireNonNull(
                        priority,
                        "SLA priority is required"
                );

        if (responseMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Response minutes must be greater than zero"
            );
        }

        if (resolutionMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Resolution minutes must be greater than zero"
            );
        }

        if (responseMinutes > resolutionMinutes) {
            throw new IllegalArgumentException(
                    "Response deadline cannot exceed resolution deadline"
            );
        }

        this.responseMinutes =
                responseMinutes;

        this.resolutionMinutes =
                resolutionMinutes;

        this.active = true;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id =
                    UUID.randomUUID();
        }

        OffsetDateTime now =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );
    }

    public UUID getId() {
        return id;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public int getResponseMinutes() {
        return responseMinutes;
    }

    public int getResolutionMinutes() {
        return resolutionMinutes;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}