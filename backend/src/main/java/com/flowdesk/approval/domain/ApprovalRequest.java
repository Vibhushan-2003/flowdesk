package com.flowdesk.approval.domain;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketType;

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
@Table(name = "approval_requests")
public class ApprovalRequest {

    private static final int MAX_DECISION_NOTE_LENGTH =
            500;

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

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "requested_by_user_id",
            nullable = false
    )
    private User requestedByUser;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private ApprovalStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "decided_by_user_id"
    )
    private User decidedByUser;

    @Column(
            name = "decision_note",
            length = 500
    )
    private String decisionNote;

    @Column(
            name = "requested_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime requestedAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    protected ApprovalRequest() {
    }

    public ApprovalRequest(
            Ticket ticket,
            User requestedByUser,
            OffsetDateTime requestedAt
    ) {
        this.ticket =
                Objects.requireNonNull(
                        ticket,
                        "Ticket is required"
                );

        this.requestedByUser =
                Objects.requireNonNull(
                        requestedByUser,
                        "Requester is required"
                );

        Objects.requireNonNull(
                requestedAt,
                "Requested time is required"
        );

        if (ticket.getType()
                != TicketType.SERVICE_REQUEST) {

            throw new IllegalArgumentException(
                    "Only service requests require approval"
            );
        }

        this.status =
                ApprovalStatus.PENDING;

        this.requestedAt =
                requestedAt.withOffsetSameInstant(
                        ZoneOffset.UTC
                );
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }

        if (status == null) {
            status =
                    ApprovalStatus.PENDING;
        }

        if (requestedAt == null) {
            requestedAt =
                    OffsetDateTime.now(
                            ZoneOffset.UTC
                    );
        }
    }

    public void approve(
            User approver,
            String note,
            OffsetDateTime decisionTime
    ) {
        validatePendingDecision(
                approver,
                decisionTime
        );

        this.status =
                ApprovalStatus.APPROVED;

        this.decidedByUser =
                approver;

        this.decisionNote =
                normalizeOptionalNote(
                        note
                );

        this.decidedAt =
                normalizeDecisionTime(
                        decisionTime
                );
    }

    public void reject(
            User approver,
            String reason,
            OffsetDateTime decisionTime
    ) {
        validatePendingDecision(
                approver,
                decisionTime
        );

        String normalizedReason =
                normalizeRequiredNote(
                        reason
                );

        this.status =
                ApprovalStatus.REJECTED;

        this.decidedByUser =
                approver;

        this.decisionNote =
                normalizedReason;

        this.decidedAt =
                normalizeDecisionTime(
                        decisionTime
                );
    }

    private void validatePendingDecision(
            User approver,
            OffsetDateTime decisionTime
    ) {
        Objects.requireNonNull(
                approver,
                "Approver is required"
        );

        Objects.requireNonNull(
                decisionTime,
                "Decision time is required"
        );

        if (status
                != ApprovalStatus.PENDING) {

            throw new IllegalStateException(
                    "Approval request has already been decided"
            );
        }

        if (sameUser(
                requestedByUser,
                approver
        )) {

            throw new IllegalStateException(
                    "Requester cannot approve or reject their own request"
            );
        }

        OffsetDateTime normalizedDecisionTime =
                normalizeDecisionTime(
                        decisionTime
                );

        if (normalizedDecisionTime
                .isBefore(
                        requestedAt
                )) {

            throw new IllegalArgumentException(
                    "Decision time cannot be before request time"
            );
        }
    }

    private boolean sameUser(
            User first,
            User second
    ) {
        if (first == second) {
            return true;
        }

        if (first == null
                || second == null) {

            return false;
        }

        UUID firstId =
                first.getId();

        UUID secondId =
                second.getId();

        return firstId != null
                && secondId != null
                && firstId.equals(
                        secondId
                );
    }

    private String normalizeOptionalNote(
            String note
    ) {
        if (note == null
                || note.isBlank()) {

            return null;
        }

        return validateNoteLength(
                note.trim()
        );
    }

    private String normalizeRequiredNote(
            String note
    ) {
        if (note == null
                || note.isBlank()) {

            throw new IllegalArgumentException(
                    "Rejection reason is required"
            );
        }

        return validateNoteLength(
                note.trim()
        );
    }

    private String validateNoteLength(
            String note
    ) {
        if (note.length()
                > MAX_DECISION_NOTE_LENGTH) {

            throw new IllegalArgumentException(
                    "Decision note must not exceed 500 characters"
            );
        }

        return note;
    }

    private OffsetDateTime normalizeDecisionTime(
            OffsetDateTime decisionTime
    ) {
        return decisionTime
                .withOffsetSameInstant(
                        ZoneOffset.UTC
                );
    }

    public UUID getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public User getRequestedByUser() {
        return requestedByUser;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public User getDecidedByUser() {
        return decidedByUser;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }
}