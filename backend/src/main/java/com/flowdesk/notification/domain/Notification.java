package com.flowdesk.notification.domain;

import com.flowdesk.ticket.domain.Ticket;
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
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    private static final int MAX_TITLE_LENGTH = 160;
    private static final int MAX_MESSAGE_LENGTH = 500;

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipientUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "message", nullable = false, length = MAX_MESSAGE_LENGTH)
    private String message;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Notification() {
    }

    public Notification(
            User recipientUser,
            User actorUser,
            Ticket ticket,
            NotificationType type,
            String title,
            String message
    ) {
        this.recipientUser = Objects.requireNonNull(
                recipientUser,
                "Recipient user is required"
        );

        this.actorUser = actorUser;
        this.ticket = ticket;

        this.type = Objects.requireNonNull(
                type,
                "Notification type is required"
        );

        this.title = normalizeRequiredText(
                title,
                "Notification title is required",
                MAX_TITLE_LENGTH
        );

        this.message = normalizeRequiredText(
                message,
                "Notification message is required",
                MAX_MESSAGE_LENGTH
        );
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }

        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public void markAsRead() {
        if (readAt == null) {
            readAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public UUID getId() {
        return id;
    }

    public User getRecipientUser() {
        return recipientUser;
    }

    public User getActorUser() {
        return actorUser;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public OffsetDateTime getReadAt() {
        return readAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return readAt != null;
    }

    private static String normalizeRequiredText(
            String value,
            String requiredMessage,
            int maxLength
    ) {
        if (value == null) {
            throw new IllegalArgumentException(requiredMessage);
        }

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(requiredMessage);
        }

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Value must not exceed " + maxLength + " characters"
            );
        }

        return normalized;
    }
}