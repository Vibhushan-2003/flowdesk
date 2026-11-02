-- V24__create_shedlock_schema.sql
-- FlowDesk Day 60: Multi-Instance Distributed Scheduler Locking & Attachment Scan Indexing

-- 1. ShedLock distributed locking table for multi-instance scheduler coordination
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

CREATE INDEX IF NOT EXISTS idx_shedlock_lock_until ON shedlock (lock_until);

-- 2. Index for pending attachment administrative queries and scanning pipeline
CREATE INDEX IF NOT EXISTS idx_ticket_attachments_scan_status ON ticket_attachments (scan_status);

