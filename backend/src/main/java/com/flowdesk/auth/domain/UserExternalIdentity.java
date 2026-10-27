package com.flowdesk.auth.domain;

import com.flowdesk.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "user_external_identities",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_external_identities_provider_subject",
                        columnNames = {"provider", "provider_subject"}
                )
        }
)
public class UserExternalIdentity {

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserExternalIdentity() {
    }

    public UserExternalIdentity(User user, String provider, String providerSubject, String email) {
        this.user = Objects.requireNonNull(user, "User must not be null");
        this.provider = Objects.requireNonNull(provider, "Provider must not be null");
        this.providerSubject = Objects.requireNonNull(providerSubject, "Provider subject must not be null");
        this.email = Objects.requireNonNull(email, "Email must not be null");
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateEmail(String email) {
        this.email = Objects.requireNonNull(email, "Email must not be null");
    }
}
