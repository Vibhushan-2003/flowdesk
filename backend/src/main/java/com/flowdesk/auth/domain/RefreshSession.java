package com.flowdesk.auth.domain;

import com.flowdesk.user.domain.User;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private OffsetDateTime absoluteExpiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "replaced_at")
    private OffsetDateTime replacedAt;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    protected RefreshSession() {
    }

    public RefreshSession(
            User user,
            UUID familyId,
            String tokenHash,
            OffsetDateTime expiresAt,
            OffsetDateTime absoluteExpiresAt,
            String clientIp,
            String userAgent
    ) {
        this.id = UUID.randomUUID();
        this.user = Objects.requireNonNull(user, "user is required");
        this.familyId = Objects.requireNonNull(familyId, "familyId is required");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash is required");
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
        this.absoluteExpiresAt = Objects.requireNonNull(absoluteExpiresAt, "absoluteExpiresAt is required");
        this.clientIp = clientIp;
        this.userAgent = userAgent;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public boolean isActive(OffsetDateTime now) {
        return revokedAt == null
                && replacedAt == null
                && now.isBefore(expiresAt)
                && now.isBefore(absoluteExpiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isReplaced() {
        return replacedAt != null;
    }

    public boolean isExpired(OffsetDateTime now) {
        return now.isAfter(expiresAt) || now.isAfter(absoluteExpiresAt);
    }

    public void revoke(OffsetDateTime now) {
        this.revokedAt = now;
    }

    public void replace(OffsetDateTime now) {
        this.replacedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getAbsoluteExpiresAt() {
        return absoluteExpiresAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public OffsetDateTime getReplacedAt() {
        return replacedAt;
    }

    public void setReplacedAt(OffsetDateTime replacedAt) {
        this.replacedAt = replacedAt;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }
}

