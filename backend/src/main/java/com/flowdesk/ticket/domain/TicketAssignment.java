package com.flowdesk.ticket.domain;

import com.flowdesk.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "ticket_assignments")
public class TicketAssignment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_to_user_id", nullable = false)
    private User assignedToUser;

    @Column(
            name = "assigned_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime assignedAt;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    protected TicketAssignment() {
    }

    public TicketAssignment(
            Ticket ticket,
            User assignedToUser
    ) {
        this.ticket = ticket;
        this.assignedToUser = assignedToUser;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }

        if (assignedAt == null) {
            assignedAt =
                    OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public UUID getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public User getAssignedToUser() {
        return assignedToUser;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }

    public OffsetDateTime getReleasedAt() {
        return releasedAt;
    }
}