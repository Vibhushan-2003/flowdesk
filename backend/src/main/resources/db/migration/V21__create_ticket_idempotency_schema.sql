-- V21__create_ticket_idempotency_schema.sql
-- Request-level idempotency protection for ticket creation

CREATE TABLE ticket_idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    ticket_id UUID REFERENCES tickets(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_ticket_idempotency_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT chk_ticket_idempotency_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_ticket_idempotency_key_not_blank CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT chk_ticket_idempotency_hash_not_blank CHECK (btrim(request_hash) <> '')
);

CREATE INDEX idx_ticket_idempotency_user_id ON ticket_idempotency_records (user_id);
CREATE INDEX idx_ticket_idempotency_created_at ON ticket_idempotency_records (created_at);

