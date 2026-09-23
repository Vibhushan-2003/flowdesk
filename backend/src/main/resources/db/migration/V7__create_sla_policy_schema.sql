CREATE TABLE sla_policies (
    id UUID PRIMARY KEY,

    priority VARCHAR(20) NOT NULL,

    response_minutes INTEGER NOT NULL,
    resolution_minutes INTEGER NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_sla_policies_priority
        CHECK (
            priority IN (
                'LOW',
                'MEDIUM',
                'HIGH',
                'CRITICAL'
            )
        ),

    CONSTRAINT chk_sla_policies_response_minutes
        CHECK (
            response_minutes > 0
        ),

    CONSTRAINT chk_sla_policies_resolution_minutes
        CHECK (
            resolution_minutes > 0
        ),

    CONSTRAINT chk_sla_policies_deadline_order
        CHECK (
            response_minutes <= resolution_minutes
        )
);

CREATE UNIQUE INDEX uq_sla_policies_active_priority
    ON sla_policies(priority)
    WHERE active = TRUE;

CREATE INDEX idx_sla_policies_priority
    ON sla_policies(priority);

CREATE INDEX idx_sla_policies_active
    ON sla_policies(active);


INSERT INTO sla_policies (
    id,
    priority,
    response_minutes,
    resolution_minutes,
    active
)
VALUES
(
    '00000000-0000-0000-0000-000000000101',
    'LOW',
    240,
    2880,
    TRUE
),
(
    '00000000-0000-0000-0000-000000000102',
    'MEDIUM',
    60,
    1440,
    TRUE
),
(
    '00000000-0000-0000-0000-000000000103',
    'HIGH',
    30,
    480,
    TRUE
),
(
    '00000000-0000-0000-0000-000000000104',
    'CRITICAL',
    15,
    240,
    TRUE
);


ALTER TABLE tickets
    ADD COLUMN sla_policy_id UUID,
    ADD COLUMN response_due_at TIMESTAMPTZ,
    ADD COLUMN resolution_due_at TIMESTAMPTZ,
    ADD COLUMN first_responded_at TIMESTAMPTZ;

ALTER TABLE tickets
    ADD CONSTRAINT fk_tickets_sla_policy
        FOREIGN KEY (sla_policy_id)
        REFERENCES sla_policies(id)
        ON DELETE RESTRICT;

ALTER TABLE tickets
    ADD CONSTRAINT chk_tickets_sla_deadline_order
        CHECK (
            response_due_at IS NULL
            OR resolution_due_at IS NULL
            OR response_due_at <= resolution_due_at
        );

ALTER TABLE tickets
    ADD CONSTRAINT chk_tickets_first_response_time
        CHECK (
            first_responded_at IS NULL
            OR first_responded_at >= created_at
        );


UPDATE tickets AS ticket
SET
    sla_policy_id = policy.id,

    response_due_at =
        ticket.created_at
        + (
            policy.response_minutes
            * INTERVAL '1 minute'
        ),

    resolution_due_at =
        ticket.created_at
        + (
            policy.resolution_minutes
            * INTERVAL '1 minute'
        )

FROM sla_policies AS policy
WHERE
    policy.priority = ticket.priority
    AND policy.active = TRUE;


CREATE INDEX idx_tickets_sla_policy
    ON tickets(sla_policy_id);

CREATE INDEX idx_tickets_response_due_at
    ON tickets(response_due_at);

CREATE INDEX idx_tickets_resolution_due_at
    ON tickets(resolution_due_at);