ALTER TABLE users
    ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 1;

COMMENT ON COLUMN users.credential_version IS
    'Monotonically increasing credential version used to immediately invalidate JWTs upon password change';
