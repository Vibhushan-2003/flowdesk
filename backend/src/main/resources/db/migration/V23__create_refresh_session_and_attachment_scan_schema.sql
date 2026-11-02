-- V23__create_refresh_session_and_attachment_scan_schema.sql
-- FlowDesk Day 59: Secure Refresh Token Sessions & Attachment Malware Protection

-- 1. Refresh Token Sessions Table
CREATE TABLE IF NOT EXISTS refresh_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_at TIMESTAMPTZ,
    client_ip VARCHAR(64),
    user_agent VARCHAR(255),

    CONSTRAINT chk_refresh_sessions_token_hash_length CHECK (char_length(token_hash) = 64),
    CONSTRAINT chk_refresh_sessions_expires_after_created CHECK (expires_at >= created_at),
    CONSTRAINT chk_refresh_sessions_absolute_after_created CHECK (absolute_expires_at >= created_at)
);

CREATE INDEX IF NOT EXISTS idx_refresh_sessions_user_id ON refresh_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_family_id ON refresh_sessions(family_id);
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_token_hash ON refresh_sessions(token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_expires_at ON refresh_sessions(expires_at);
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_active ON refresh_sessions(user_id, expires_at)
    WHERE revoked_at IS NULL AND replaced_at IS NULL;

-- 2. Attachment Malware Scan Columns
ALTER TABLE ticket_attachments
ADD COLUMN IF NOT EXISTS scan_status VARCHAR(30) NOT NULL DEFAULT 'PENDING_SCAN';

ALTER TABLE ticket_attachments
ADD COLUMN IF NOT EXISTS scanned_at TIMESTAMPTZ;

ALTER TABLE ticket_attachments
ADD COLUMN IF NOT EXISTS scan_details VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_ticket_attachments_scan_status'
    ) THEN
        ALTER TABLE ticket_attachments
        ADD CONSTRAINT chk_ticket_attachments_scan_status
            CHECK (scan_status IN ('PENDING_SCAN', 'CLEAN', 'INFECTED', 'SCAN_FAILED'));
    END IF;
END $$;

DO $$
BEGIN
    ALTER TABLE ticket_attachments DROP CONSTRAINT IF EXISTS chk_ticket_attachments_status;
    ALTER TABLE ticket_attachments
    ADD CONSTRAINT chk_ticket_attachments_status
        CHECK (status IN ('PENDING', 'AVAILABLE', 'QUARANTINED', 'DELETED'));
END $$;

CREATE INDEX IF NOT EXISTS idx_ticket_attachments_scan_status
    ON ticket_attachments (scan_status);

