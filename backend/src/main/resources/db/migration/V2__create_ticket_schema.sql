CREATE SEQUENCE ticket_number_seq
    START WITH 1
    INCREMENT BY 1;

CREATE TABLE tickets (
    id UUID PRIMARY KEY,

    ticket_number VARCHAR(30) NOT NULL UNIQUE,

    type VARCHAR(30) NOT NULL,

    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,

    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',

    created_by_user_id UUID NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_tickets_type
        CHECK (
            type IN (
                'INCIDENT',
                'SERVICE_REQUEST'
            )
        ),

    CONSTRAINT chk_tickets_priority
        CHECK (
            priority IN (
                'LOW',
                'MEDIUM',
                'HIGH',
                'CRITICAL'
            )
        ),

    CONSTRAINT chk_tickets_status
        CHECK (
            status IN (
                'OPEN',
                'ASSIGNED',
                'IN_PROGRESS',
                'WAITING_FOR_USER',
                'RESOLVED',
                'CLOSED',
                'CANCELLED'
            )
        ),

    CONSTRAINT chk_tickets_title
        CHECK (
            CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 200
        ),

    CONSTRAINT chk_tickets_description
        CHECK (
            CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 5000
        ),

    CONSTRAINT fk_tickets_created_by_user
        FOREIGN KEY (created_by_user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_tickets_created_by_user
    ON tickets(created_by_user_id);

CREATE INDEX idx_tickets_status
    ON tickets(status);

CREATE INDEX idx_tickets_type
    ON tickets(type);

CREATE INDEX idx_tickets_created_at
    ON tickets(created_at);