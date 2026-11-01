-- V22__add_reporting_indexes_and_deflection_tracking.sql
-- FlowDesk Day 58: Performance indexes for operational reporting & KB deflection tracking

-- 1. Knowledge Base Deflection Tracking
ALTER TABLE kb_articles
ADD COLUMN IF NOT EXISTS deflection_count INTEGER NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_kb_articles_deflection_count_non_negative'
    ) THEN
        ALTER TABLE kb_articles
        ADD CONSTRAINT chk_kb_articles_deflection_count_non_negative
            CHECK (deflection_count >= 0);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_kb_articles_deflection_count
    ON kb_articles (deflection_count DESC);

-- 2. Performance Indexes for Ticket Volume, Status, Priority & Time-Series Reporting
CREATE INDEX IF NOT EXISTS idx_tickets_priority
    ON tickets (priority);

CREATE INDEX IF NOT EXISTS idx_tickets_created_status_priority
    ON tickets (created_at, status, priority);

CREATE INDEX IF NOT EXISTS idx_tickets_resolved_created
    ON tickets (resolved_at, created_at)
    WHERE resolved_at IS NOT NULL;

-- 3. Performance Indexes for SLA Reporting
CREATE INDEX IF NOT EXISTS idx_sla_events_occurred_type
    ON sla_events (occurred_at, event_type);

-- 4. Performance Indexes for Support Engineer Assignment Reporting
CREATE INDEX IF NOT EXISTS idx_ticket_assignments_engineer_period
    ON ticket_assignments (assigned_to_user_id, assigned_at);
