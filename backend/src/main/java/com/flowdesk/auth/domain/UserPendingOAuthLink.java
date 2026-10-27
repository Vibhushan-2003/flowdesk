package com.flowdesk.auth.domain;

import com.flowdesk.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_pending_oauth_links")
public class UserPendingOAuthLink {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected UserPendingOAuthLink() {
    }

    public UserPendingOAuthLink(
            User user,
            String provider,
            String providerSubject,
            String email,
            String tokenHash,
            Instant expiresAt
    ) {
        this.user = Objects.requireNonNull(user, "User must not be null");
        this.provider = Objects.requireNonNull(provider, "Provider must not be null");
        this.providerSubject = Objects.requireNonNull(providerSubject, "Provider subject must not be null");
        this.email = Objects.requireNonNull(email, "Email must not be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "Token hash must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "Expires at must not be null");
        this.failedAttempts = 0;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public String getEmail() {
        return email;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public void incrementFailedAttempts() {
        this.failedAttempts++;
    }

    public void markConsumed() {
        this.consumedAt = Instant.now();
    }
}
