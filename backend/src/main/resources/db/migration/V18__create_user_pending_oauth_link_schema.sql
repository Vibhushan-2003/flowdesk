-- Day 51: Pending OAuth account-linking transactions

CREATE TABLE user_pending_oauth_links (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    failed_attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,

    CONSTRAINT fk_pending_oauth_links_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_pending_oauth_links_expiry
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_pending_oauth_links_token_hash
    ON user_pending_oauth_links (token_hash);

CREATE INDEX idx_pending_oauth_links_user_id
    ON user_pending_oauth_links (user_id);

CREATE INDEX idx_pending_oauth_links_expires_at
    ON user_pending_oauth_links (expires_at);

COMMENT ON TABLE user_pending_oauth_links IS
    'Stores short-lived, single-use, rate-limited pending OAuth linking transactions';
