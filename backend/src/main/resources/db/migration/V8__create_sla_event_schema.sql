CREATE TABLE sla_events (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_sla_events_ticket
        FOREIGN KEY (ticket_id)
        REFERENCES tickets(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_sla_events_event_type
        CHECK (
            event_type IN (
                'RESPONSE_BREACHED',
                'RESOLUTION_BREACHED'
            )
        ),

    CONSTRAINT uq_sla_events_ticket_event_type
        UNIQUE (ticket_id, event_type)
);

CREATE INDEX idx_sla_events_event_type
    ON sla_events(event_type);

CREATE INDEX idx_sla_events_occurred_at
    ON sla_events(occurred_at);
