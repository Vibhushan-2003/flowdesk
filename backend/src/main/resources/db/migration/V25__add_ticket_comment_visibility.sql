-- V25__add_ticket_comment_visibility.sql
-- Adds visibility classification to ticket_comments for private internal staff notes

ALTER TABLE ticket_comments
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';

ALTER TABLE ticket_comments
    ADD CONSTRAINT chk_ticket_comments_visibility
        CHECK (visibility IN ('PUBLIC', 'INTERNAL'));

CREATE INDEX idx_ticket_comments_ticket_visibility_created_at
    ON ticket_comments(ticket_id, visibility, created_at);

