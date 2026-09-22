CREATE TABLE notifications (
    id UUID PRIMARY KEY,

    recipient_user_id UUID NOT NULL,
    actor_user_id UUID,

    ticket_id UUID,

    type VARCHAR(50) NOT NULL,

    title VARCHAR(160) NOT NULL,
    message VARCHAR(500) NOT NULL,

    read_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_notifications_type_not_blank
        CHECK (CHAR_LENGTH(TRIM(type)) > 0),

    CONSTRAINT chk_notifications_title
        CHECK (CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 160),

    CONSTRAINT chk_notifications_message
        CHECK (CHAR_LENGTH(TRIM(message)) BETWEEN 1 AND 500),

    CONSTRAINT fk_notifications_recipient_user
        FOREIGN KEY (recipient_user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_notifications_actor_user
        FOREIGN KEY (actor_user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_notifications_ticket
        FOREIGN KEY (ticket_id)
        REFERENCES tickets(id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_notifications_recipient_created_at
    ON notifications(recipient_user_id, created_at DESC);

CREATE INDEX idx_notifications_recipient_read_at
    ON notifications(recipient_user_id, read_at);

CREATE INDEX idx_notifications_ticket
    ON notifications(ticket_id);