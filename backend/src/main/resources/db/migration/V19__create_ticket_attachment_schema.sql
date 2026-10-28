CREATE TABLE ticket_attachments (
    id UUID PRIMARY KEY,

    ticket_id UUID NOT NULL,
    uploaded_by_user_id UUID NOT NULL,

    original_filename VARCHAR(255) NOT NULL,
    sanitized_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,

    storage_key VARCHAR(255) NOT NULL UNIQUE,
    checksum_sha256 VARCHAR(64) NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_ticket_attachments_original_filename
        CHECK (
            CHAR_LENGTH(TRIM(original_filename)) BETWEEN 1 AND 255
        ),

    CONSTRAINT chk_ticket_attachments_sanitized_filename
        CHECK (
            CHAR_LENGTH(TRIM(sanitized_filename)) BETWEEN 1 AND 255
        ),

    CONSTRAINT chk_ticket_attachments_content_type
        CHECK (
            CHAR_LENGTH(TRIM(content_type)) BETWEEN 1 AND 100
        ),

    CONSTRAINT chk_ticket_attachments_size_bytes
        CHECK (
            size_bytes > 0 AND size_bytes <= 10485760
        ),

    CONSTRAINT chk_ticket_attachments_checksum
        CHECK (
            CHAR_LENGTH(checksum_sha256) = 64
        ),

    CONSTRAINT chk_ticket_attachments_status
        CHECK (
            status IN (
                'PENDING',
                'AVAILABLE',
                'DELETED'
            )
        ),

    CONSTRAINT fk_ticket_attachments_ticket
        FOREIGN KEY (ticket_id)
        REFERENCES tickets(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_ticket_attachments_uploaded_by_user
        FOREIGN KEY (uploaded_by_user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_ticket_attachments_ticket_created_at
    ON ticket_attachments(ticket_id, created_at);

CREATE INDEX idx_ticket_attachments_uploaded_by_user
    ON ticket_attachments(uploaded_by_user_id);

CREATE INDEX idx_ticket_attachments_storage_key
    ON ticket_attachments(storage_key);

CREATE INDEX idx_ticket_attachments_status
    ON ticket_attachments(status);
