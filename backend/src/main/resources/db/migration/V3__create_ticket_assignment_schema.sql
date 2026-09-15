CREATE TABLE ticket_assignments (
    id UUID PRIMARY KEY,

    ticket_id UUID NOT NULL,
    assigned_to_user_id UUID NOT NULL,

    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMPTZ,

    CONSTRAINT fk_ticket_assignments_ticket
        FOREIGN KEY (ticket_id)
        REFERENCES tickets(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_ticket_assignments_assigned_to_user
        FOREIGN KEY (assigned_to_user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_ticket_assignments_release_time
        CHECK (
            released_at IS NULL
            OR released_at >= assigned_at
        )
);

CREATE INDEX idx_ticket_assignments_ticket
    ON ticket_assignments(ticket_id);

CREATE INDEX idx_ticket_assignments_assigned_to_user
    ON ticket_assignments(assigned_to_user_id);

CREATE INDEX idx_ticket_assignments_assigned_at
    ON ticket_assignments(assigned_at);

CREATE UNIQUE INDEX uq_ticket_assignments_active_ticket
    ON ticket_assignments(ticket_id)
    WHERE released_at IS NULL;