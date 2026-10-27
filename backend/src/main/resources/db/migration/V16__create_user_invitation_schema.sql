
-- Day 50: Admin invitation schema

ALTER TABLE users
    DROP CONSTRAINT chk_users_status;

ALTER TABLE users
    ADD CONSTRAINT chk_users_status
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'INVITED'));

CREATE TABLE user_invitations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,

    CONSTRAINT fk_user_invitations_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_user_invitations_expiry
        CHECK (expires_at > created_at)
);

CREATE UNIQUE INDEX uk_user_invitations_unconsumed
    ON user_invitations (user_id)
    WHERE consumed_at IS NULL;

CREATE INDEX idx_user_invitations_expires_at
    ON user_invitations (expires_at);

COMMENT ON TABLE user_invitations IS
    'Stores hashed, expiring, single-use account invitation tokens';
