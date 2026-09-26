ALTER TABLE tickets
    DROP CONSTRAINT chk_tickets_status;

ALTER TABLE tickets
    ADD CONSTRAINT chk_tickets_status
        CHECK (
            status IN (
                'PENDING_APPROVAL',
                'OPEN',
                'ASSIGNED',
                'IN_PROGRESS',
                'WAITING_FOR_USER',
                'RESOLVED',
                'CLOSED',
                'CANCELLED'
            )
        );


CREATE TABLE approval_requests (
    id UUID PRIMARY KEY,

    ticket_id UUID NOT NULL,

    requested_by_user_id UUID NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    decided_by_user_id UUID,

    decision_note VARCHAR(500),

    requested_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    decided_at TIMESTAMPTZ,

    CONSTRAINT uq_approval_requests_ticket
        UNIQUE (ticket_id),

    CONSTRAINT fk_approval_requests_ticket
        FOREIGN KEY (ticket_id)
        REFERENCES tickets(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_approval_requests_requested_by
        FOREIGN KEY (requested_by_user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_approval_requests_decided_by
        FOREIGN KEY (decided_by_user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_approval_requests_status
        CHECK (
            status IN (
                'PENDING',
                'APPROVED',
                'REJECTED'
            )
        ),

    CONSTRAINT chk_approval_requests_decision_note
        CHECK (
            decision_note IS NULL
            OR CHAR_LENGTH(
                TRIM(decision_note)
            ) BETWEEN 1 AND 500
        ),

    CONSTRAINT chk_approval_requests_decision_state
        CHECK (
            (
                status = 'PENDING'
                AND decided_by_user_id IS NULL
                AND decided_at IS NULL
                AND decision_note IS NULL
            )
            OR
            (
                status = 'APPROVED'
                AND decided_by_user_id IS NOT NULL
                AND decided_at IS NOT NULL
            )
            OR
            (
                status = 'REJECTED'
                AND decided_by_user_id IS NOT NULL
                AND decided_at IS NOT NULL
                AND decision_note IS NOT NULL
            )
        ),

    CONSTRAINT chk_approval_requests_no_self_decision
        CHECK (
            decided_by_user_id IS NULL
            OR decided_by_user_id
                <> requested_by_user_id
        ),

    CONSTRAINT chk_approval_requests_decision_time
        CHECK (
            decided_at IS NULL
            OR decided_at >= requested_at
        )
);


CREATE INDEX idx_approval_requests_requested_by
    ON approval_requests (
        requested_by_user_id,
        requested_at DESC
    );

CREATE INDEX idx_approval_requests_decided_by
    ON approval_requests (
        decided_by_user_id,
        decided_at DESC
    );

CREATE INDEX idx_approval_requests_status
    ON approval_requests (
        status,
        requested_at ASC
    );

CREATE INDEX idx_approval_requests_pending
    ON approval_requests (
        requested_at ASC
    )
    WHERE status = 'PENDING';