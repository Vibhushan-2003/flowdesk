package com.flowdesk.sla.domain;

import com.flowdesk.ticket.domain.Ticket;

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
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "sla_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_sla_events_ticket_event_type",
                        columnNames = {
                                "ticket_id",
                                "event_type"
                        }
                )
        }
)
public class SlaEvent {

    @Id
    private UUID id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "ticket_id",
            nullable = false
    )
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "event_type",
            nullable = false,
            length = 40
    )
    private SlaEventType eventType;

    @Column(
            name = "occurred_at",
            nullable = false
    )
    private OffsetDateTime occurredAt;

    protected SlaEvent() {
    }

    public SlaEvent(
            Ticket ticket,
            SlaEventType eventType,
            OffsetDateTime occurredAt
    ) {
        this.ticket =
                Objects.requireNonNull(
                        ticket,
                        "SLA event ticket is required"
                );

        this.eventType =
                Objects.requireNonNull(
                        eventType,
                        "SLA event type is required"
                );

        this.occurredAt =
                Objects.requireNonNull(
                        occurredAt,
                        "SLA event occurrence time is required"
                )
                .withOffsetSameInstant(
                        ZoneOffset.UTC
                );
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }

        if (occurredAt == null) {
            occurredAt =
                    OffsetDateTime.now(
                            ZoneOffset.UTC
                    );
        }
        else {
            occurredAt =
                    occurredAt
                            .withOffsetSameInstant(
                                    ZoneOffset.UTC
                            );
        }
    }

    public UUID getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public SlaEventType getEventType() {
        return eventType;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
