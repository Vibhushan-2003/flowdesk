CREATE TABLE ticket_comments (
    id UUID PRIMARY KEY,

    ticket_id UUID NOT NULL,
    author_user_id UUID NOT NULL,

    body TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_ticket_comments_body
        CHECK (
            CHAR_LENGTH(TRIM(body)) BETWEEN 1 AND 4000
        ),

    CONSTRAINT fk_ticket_comments_ticket
        FOREIGN KEY (ticket_id)
        REFERENCES tickets(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_ticket_comments_author_user
        FOREIGN KEY (author_user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_ticket_comments_ticket_created_at
    ON ticket_comments(ticket_id, created_at);

CREATE INDEX idx_ticket_comments_author_user
    ON ticket_comments(author_user_id);