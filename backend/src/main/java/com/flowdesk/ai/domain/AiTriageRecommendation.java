package com.flowdesk.ai.domain;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketPriority;

import com.flowdesk.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ai_triage_recommendations")
public class AiTriageRecommendation {

    private static final int MAX_REASON_LENGTH =
            1000;

    private static final int MAX_PROVIDER_LENGTH =
            50;

    private static final int MAX_MODEL_LENGTH =
            100;

    private static final int MAX_PROMPT_VERSION_LENGTH =
            30;

    private static final int CONFIDENCE_SCALE =
            4;

    @Id
    private UUID id;

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "ticket_id",
            nullable = false,
            unique = true
    )
    private Ticket ticket;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "generated_by_user_id",
            nullable = false
    )
    private User generatedByUser;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "suggested_priority",
            nullable = false,
            length = 20
    )
    private TicketPriority suggestedPriority;

    @Column(
            nullable = false,
            precision = 5,
            scale = 4
    )
    private BigDecimal confidence;

    @Column(
            nullable = false,
            length = MAX_REASON_LENGTH
    )
    private String reason;

    @Column(
            nullable = false,
            length = MAX_PROVIDER_LENGTH
    )
    private String provider;

    @Column(
            nullable = false,
            length = MAX_MODEL_LENGTH
    )
    private String model;

    @Column(
            name = "prompt_version",
            nullable = false,
            length = MAX_PROMPT_VERSION_LENGTH
    )
    private String promptVersion;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_by_user_id")
    private User appliedByUser;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    protected AiTriageRecommendation() {
    }

    public AiTriageRecommendation(
            Ticket ticket,
            User generatedByUser,
            TicketPriority suggestedPriority,
            BigDecimal confidence,
            String reason,
            String provider,
            String model,
            String promptVersion,
            OffsetDateTime createdAt
    ) {
        this.ticket =
                Objects.requireNonNull(
                        ticket,
                        "Ticket is required"
                );

        this.generatedByUser =
                Objects.requireNonNull(
                        generatedByUser,
                        "Generating user is required"
                );

        this.suggestedPriority =
                Objects.requireNonNull(
                        suggestedPriority,
                        "Suggested priority is required"
                );

        this.confidence =
                normalizeConfidence(
                        confidence
                );

        this.reason =
                normalizeRequiredText(
                        reason,
                        "AI triage reason is required",
                        MAX_REASON_LENGTH
                );

        this.provider =
                normalizeRequiredText(
                        provider,
                        "AI provider is required",
                        MAX_PROVIDER_LENGTH
                );

        this.model =
                normalizeRequiredText(
                        model,
                        "AI model is required",
                        MAX_MODEL_LENGTH
                );

        this.promptVersion =
                normalizeRequiredText(
                        promptVersion,
                        "AI prompt version is required",
                        MAX_PROMPT_VERSION_LENGTH
                );

        this.createdAt =
                Objects.requireNonNull(
                        createdAt,
                        "Created time is required"
                )
                        .withOffsetSameInstant(
                                ZoneOffset.UTC
                        );
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id =
                    UUID.randomUUID();
        }

        if (createdAt == null) {
            createdAt =
                    OffsetDateTime.now(
                            ZoneOffset.UTC
                    );
        }
    }

    public void markApplied(
            User appliedByUser,
            OffsetDateTime appliedAt
    ) {
        Objects.requireNonNull(
                appliedByUser,
                "Applying user is required"
        );

        Objects.requireNonNull(
                appliedAt,
                "Applied time is required"
        );

        if (isApplied()) {
            throw new IllegalStateException(
                    "AI triage recommendation has already been applied"
            );
        }

        this.appliedByUser =
                appliedByUser;

        this.appliedAt =
                appliedAt
                        .withOffsetSameInstant(
                                ZoneOffset.UTC
                        );
    }

    public boolean isApplied() {
        return appliedByUser != null
                || appliedAt != null;
    }

    private BigDecimal normalizeConfidence(
            BigDecimal value
    ) {
        Objects.requireNonNull(
                value,
                "AI confidence is required"
        );

        if (value.compareTo(
                BigDecimal.ZERO
        ) < 0
                || value.compareTo(
                        BigDecimal.ONE
                ) > 0) {

            throw new IllegalArgumentException(
                    "AI confidence must be between 0 and 1"
            );
        }

        return value.setScale(
                CONFIDENCE_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private String normalizeRequiredText(
            String value,
            String requiredMessage,
            int maxLength
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    requiredMessage
            );
        }

        String normalized =
                value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    requiredMessage
            );
        }

        if (normalized.length()
                > maxLength) {

            throw new IllegalArgumentException(
                    "Value must not exceed "
                            + maxLength
                            + " characters"
            );
        }

        return normalized;
    }

    public UUID getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public User getGeneratedByUser() {
        return generatedByUser;
    }

    public TicketPriority getSuggestedPriority() {
        return suggestedPriority;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getReason() {
        return reason;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public User getAppliedByUser() {
        return appliedByUser;
    }

    public OffsetDateTime getAppliedAt() {
        return appliedAt;
    }
}