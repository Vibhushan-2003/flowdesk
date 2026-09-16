ALTER TABLE tickets
ADD COLUMN resolved_at TIMESTAMPTZ;

CREATE INDEX idx_tickets_resolved_at
    ON tickets(resolved_at);