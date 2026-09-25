CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,

    actor_type VARCHAR(20) NOT NULL,
    actor_user_id UUID,
    actor_email VARCHAR(320),

    action VARCHAR(60) NOT NULL,

    target_type VARCHAR(40) NOT NULL,
    target_id UUID NOT NULL,
    target_reference VARCHAR(100),

    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,

    occurred_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_audit_logs_actor_user
        FOREIGN KEY (actor_user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_audit_logs_actor_type
        CHECK (actor_type IN ('USER', 'SYSTEM')),

    CONSTRAINT chk_audit_logs_actor
        CHECK (
            (
                actor_type = 'USER'
                AND actor_user_id IS NOT NULL
                AND actor_email IS NOT NULL
            )
            OR
            (
                actor_type = 'SYSTEM'
                AND actor_user_id IS NULL
                AND actor_email IS NULL
            )
        ),

    CONSTRAINT chk_audit_logs_action_not_blank
        CHECK (btrim(action) <> ''),

    CONSTRAINT chk_audit_logs_target_type_not_blank
        CHECK (btrim(target_type) <> '')
);

CREATE INDEX idx_audit_logs_occurred_at
    ON audit_logs (occurred_at DESC);

CREATE INDEX idx_audit_logs_actor_user
    ON audit_logs (actor_user_id, occurred_at DESC);

CREATE INDEX idx_audit_logs_action
    ON audit_logs (action, occurred_at DESC);

CREATE INDEX idx_audit_logs_target
    ON audit_logs (
        target_type,
        target_id,
        occurred_at DESC
    );


CREATE OR REPLACE FUNCTION prevent_audit_log_mutation()
RETURNS TRIGGER
AS $$
BEGIN
    RAISE EXCEPTION
        'audit_logs are immutable and cannot be modified or deleted';
END;
$$ LANGUAGE plpgsql;


CREATE TRIGGER trg_audit_logs_immutable
BEFORE UPDATE OR DELETE
ON audit_logs
FOR EACH ROW
EXECUTE FUNCTION prevent_audit_log_mutation();