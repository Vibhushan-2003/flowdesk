package com.flowdesk.ticket.domain;

import com.flowdesk.sla.domain.SlaPolicy;
import com.flowdesk.sla.domain.SlaStatus;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    private UUID id;

    @Column(
            name = "ticket_number",
            nullable = false,
            unique = true,
            length = 30
    )
    private String ticketNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketType type;

    @Column(
            nullable = false,
            length = 200
    )
    private String title;

    @Column(
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 30
    )
    private TicketStatus status;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "created_by_user_id",
            nullable = false
    )
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sla_policy_id")
    private SlaPolicy slaPolicy;

    @Column(name = "response_due_at")
    private OffsetDateTime responseDueAt;

    @Column(name = "resolution_due_at")
    private OffsetDateTime resolutionDueAt;

    @Column(name = "first_responded_at")
    private OffsetDateTime firstRespondedAt;

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

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    protected Ticket() {
    }

    public Ticket(
            String ticketNumber,
            TicketType type,
            String title,
            String description,
            User createdByUser
    ) {
        this.ticketNumber =
                ticketNumber;

        this.type =
                type;

        this.title =
                title;

        this.description =
                description;

        this.createdByUser =
                createdByUser;

        this.priority =
                TicketPriority.MEDIUM;

        this.status =
                initialStatusFor(
                        type
                );
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id =
                    UUID.randomUUID();
        }

        if (priority == null) {
            priority =
                    TicketPriority.MEDIUM;
        }

        if (status == null) {
            status =
                    initialStatusFor(
                            type
                    );
        }

        OffsetDateTime now =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );

        createdAt =
                now;

        updatedAt =
                now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );
    }

    private TicketStatus initialStatusFor(
            TicketType ticketType
    ) {
        if (ticketType
                == TicketType.SERVICE_REQUEST) {

            return TicketStatus
                    .PENDING_APPROVAL;
        }

        return TicketStatus.OPEN;
    }

    public void approveServiceRequest() {
        validatePendingServiceRequest();

        status =
                TicketStatus.OPEN;
    }

    public void rejectServiceRequest() {
        validatePendingServiceRequest();

        status =
                TicketStatus.CANCELLED;
    }

    private void validatePendingServiceRequest() {
        if (type
                != TicketType.SERVICE_REQUEST) {

            throw new IllegalStateException(
                    "Only service requests use the approval workflow"
            );
        }

        if (status
                != TicketStatus.PENDING_APPROVAL) {

            throw new IllegalStateException(
                    "Service request is not pending approval"
            );
        }
    }

    public void applySlaPolicy(
            SlaPolicy slaPolicy,
            OffsetDateTime slaStartedAt
    ) {
        Objects.requireNonNull(
                slaPolicy,
                "SLA policy is required"
        );

        Objects.requireNonNull(
                slaStartedAt,
                "SLA start time is required"
        );

        if (status
                != TicketStatus.OPEN) {

            throw new IllegalStateException(
                    "SLA can only start when ticket is open"
            );
        }

        if (this.slaPolicy != null
                || responseDueAt != null
                || resolutionDueAt != null) {

            throw new IllegalStateException(
                    "SLA policy is already assigned"
            );
        }

        if (!slaPolicy.isActive()) {
            throw new IllegalArgumentException(
                    "SLA policy must be active"
            );
        }

        if (slaPolicy.getPriority()
                != priority) {

            throw new IllegalArgumentException(
                    "SLA policy priority does not match ticket priority"
            );
        }

        OffsetDateTime normalizedStart =
                slaStartedAt
                        .withOffsetSameInstant(
                                ZoneOffset.UTC
                        );

        this.slaPolicy =
                slaPolicy;

        this.responseDueAt =
                normalizedStart
                        .plusMinutes(
                                slaPolicy
                                        .getResponseMinutes()
                        );

        this.resolutionDueAt =
                normalizedStart
                        .plusMinutes(
                                slaPolicy
                                        .getResolutionMinutes()
                        );
    }

    public void applyPriorityAndRecalculateSla(
            TicketPriority newPriority,
            SlaPolicy newSlaPolicy
    ) {
        Objects.requireNonNull(
                newPriority,
                "New ticket priority is required"
        );

        Objects.requireNonNull(
                newSlaPolicy,
                "New SLA policy is required"
        );

        /*
         * Day 26 intentionally allows AI-assisted
         * priority application only before support work
         * begins.
         *
         * Once the ticket moves to IN_PROGRESS, changing
         * the SLA target would rewrite historical response
         * performance.
         */
        if (status
                != TicketStatus.ASSIGNED) {

            throw new IllegalStateException(
                    "Ticket priority can only be changed while assigned"
            );
        }

        /*
         * An assigned ticket should already have its SLA
         * snapshot.
         *
         * Incidents receive it at creation.
         * Service requests receive it after approval.
         */
        if (slaPolicy == null
                || responseDueAt == null
                || resolutionDueAt == null) {

            throw new IllegalStateException(
                    "Ticket SLA is not initialized"
            );
        }

        if (!newSlaPolicy.isActive()) {
            throw new IllegalArgumentException(
                    "SLA policy must be active"
            );
        }

        if (newSlaPolicy.getPriority()
                != newPriority) {

            throw new IllegalArgumentException(
                    "SLA policy priority does not match new ticket priority"
            );
        }

        /*
         * Preserve the ORIGINAL SLA start time.
         *
         * We do not reset the SLA clock when priority
         * changes.
         *
         * Example:
         *
         * Original MEDIUM:
         * start     = 10:00
         * response  = 11:00
         *
         * If HIGH response time is 30 minutes,
         * the new deadline becomes 10:30,
         * NOT "current time + 30 minutes".
         */
        OffsetDateTime responseSlaStartedAt =
                responseDueAt
                        .minusMinutes(
                                slaPolicy
                                        .getResponseMinutes()
                        );

        OffsetDateTime resolutionSlaStartedAt =
                resolutionDueAt
                        .minusMinutes(
                                slaPolicy
                                        .getResolutionMinutes()
                        );

        /*
         * Both existing deadlines must represent the same
         * original SLA start.
         *
         * If they do not, the ticket already contains an
         * inconsistent SLA snapshot and we must not silently
         * overwrite it.
         */
        if (!responseSlaStartedAt
                .toInstant()
                .equals(
                        resolutionSlaStartedAt
                                .toInstant()
                )) {

            throw new IllegalStateException(
                    "Existing SLA deadlines are inconsistent"
            );
        }

        OffsetDateTime preservedSlaStart =
                responseSlaStartedAt
                        .withOffsetSameInstant(
                                ZoneOffset.UTC
                        );

        priority =
                newPriority;

        slaPolicy =
                newSlaPolicy;

        responseDueAt =
                preservedSlaStart
                        .plusMinutes(
                                newSlaPolicy
                                        .getResponseMinutes()
                        );

        resolutionDueAt =
                preservedSlaStart
                        .plusMinutes(
                                newSlaPolicy
                                        .getResolutionMinutes()
                        );
    }

    public void markAssigned() {
        if (status
                != TicketStatus.OPEN) {

            throw new IllegalStateException(
                    "Only open tickets can be assigned"
            );
        }

        status =
                TicketStatus.ASSIGNED;
    }

    public void transitionSupportStatus(
            TicketStatus newStatus
    ) {
        if (newStatus == null) {
            throw new IllegalArgumentException(
                    "New ticket status is required"
            );
        }

        boolean validTransition =
                switch (status) {

                    case ASSIGNED ->
                            newStatus
                                    == TicketStatus.IN_PROGRESS;

                    case IN_PROGRESS ->
                            newStatus
                                    == TicketStatus.WAITING_FOR_USER
                                    || newStatus
                                    == TicketStatus.RESOLVED;

                    case WAITING_FOR_USER ->
                            newStatus
                                    == TicketStatus.IN_PROGRESS;

                    default ->
                            false;
                };

        if (!validTransition) {
            throw new IllegalStateException(
                    "Invalid ticket status transition: "
                            + status
                            + " -> "
                            + newStatus
            );
        }

        status =
                newStatus;

        OffsetDateTime now =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );

        if (newStatus
                == TicketStatus.IN_PROGRESS
                && firstRespondedAt == null) {

            firstRespondedAt =
                    now;
        }

        if (newStatus
                == TicketStatus.RESOLVED) {

            resolvedAt =
                    now;
        }
    }

    public SlaStatus evaluateResponseSla(
            OffsetDateTime evaluatedAt
    ) {
        boolean responseAlreadyOccurredButTimestampUnknown =
                firstRespondedAt == null
                        && switch (status) {

                    case IN_PROGRESS,
                            WAITING_FOR_USER,
                            RESOLVED,
                            CLOSED,
                            CANCELLED ->
                            true;

                    default ->
                            false;
                };

        return evaluateSla(
                responseDueAt,
                firstRespondedAt,
                evaluatedAt,
                responseAlreadyOccurredButTimestampUnknown
        );
    }

    public SlaStatus evaluateResolutionSla(
            OffsetDateTime evaluatedAt
    ) {
        boolean resolutionAlreadyOccurredButTimestampUnknown =
                resolvedAt == null
                        && switch (status) {

                    case RESOLVED,
                            CLOSED,
                            CANCELLED ->
                            true;

                    default ->
                            false;
                };

        return evaluateSla(
                resolutionDueAt,
                resolvedAt,
                evaluatedAt,
                resolutionAlreadyOccurredButTimestampUnknown
        );
    }

    private SlaStatus evaluateSla(
            OffsetDateTime dueAt,
            OffsetDateTime completedAt,
            OffsetDateTime evaluatedAt,
            boolean completionTimestampUnknown
    ) {
        Objects.requireNonNull(
                evaluatedAt,
                "SLA evaluation time is required"
        );

        if (dueAt == null) {
            return SlaStatus.UNKNOWN;
        }

        if (completedAt != null) {
            return completedAt
                    .isAfter(
                            dueAt
                    )
                    ? SlaStatus.BREACHED
                    : SlaStatus.MET;
        }

        if (completionTimestampUnknown) {
            return SlaStatus.UNKNOWN;
        }

        return evaluatedAt
                .isAfter(
                        dueAt
                )
                ? SlaStatus.BREACHED
                : SlaStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public TicketType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public SlaPolicy getSlaPolicy() {
        return slaPolicy;
    }

    public OffsetDateTime getResponseDueAt() {
        return responseDueAt;
    }

    public OffsetDateTime getResolutionDueAt() {
        return resolutionDueAt;
    }

    public OffsetDateTime getFirstRespondedAt() {
        return firstRespondedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }
}