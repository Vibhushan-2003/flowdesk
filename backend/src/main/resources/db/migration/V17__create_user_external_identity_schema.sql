-- Day 51 / Phase 3: External OAuth identity linking

CREATE TABLE user_external_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_external_identities_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_external_identities_provider_subject
        UNIQUE (provider, provider_subject)
);

CREATE INDEX idx_external_identities_user_id
    ON user_external_identities (user_id);

COMMENT ON TABLE user_external_identities IS
    'Stores linked third-party OAuth identities (e.g. Google OIDC) mapped to FlowDesk users';
