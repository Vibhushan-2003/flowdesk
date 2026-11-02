package com.flowdesk.ticket.domain;

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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "ticket_comments")
public class TicketComment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_user_id", nullable = false)
    private User authorUser;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private CommentVisibility visibility = CommentVisibility.PUBLIC;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    protected TicketComment() {
    }

    public TicketComment(
        Ticket ticket,
        User authorUser,
        String body
    ) {
        this(ticket, authorUser, body, CommentVisibility.PUBLIC);
    }

    public TicketComment(
        Ticket ticket,
        User authorUser,
        String body,
        CommentVisibility visibility
    ) {
        if (ticket == null) {
            throw new IllegalArgumentException(
                "Ticket is required"
            );
        }

        if (authorUser == null) {
            throw new IllegalArgumentException(
                "Comment author is required"
            );
        }

        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException(
                "Comment body is required"
            );
        }

        String normalizedBody = body.trim();

        if (normalizedBody.length() > 4000) {
            throw new IllegalArgumentException(
                "Comment body must not exceed 4000 characters"
            );
        }

        this.ticket = ticket;
        this.authorUser = authorUser;
        this.body = normalizedBody;
        this.visibility = visibility != null ? visibility : CommentVisibility.PUBLIC;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }

        if (createdAt == null) {
            createdAt =
                OffsetDateTime.now(ZoneOffset.UTC);
        }

        if (visibility == null) {
            visibility = CommentVisibility.PUBLIC;
        }
    }

    public UUID getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public User getAuthorUser() {
        return authorUser;
    }

    public String getBody() {
        return body;
    }

    public CommentVisibility getVisibility() {
        return visibility;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
