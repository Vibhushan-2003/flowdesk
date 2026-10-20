ALTER TABLE ai_triage_recommendations
    ADD COLUMN applied_by_user_id UUID,
    ADD COLUMN applied_at TIMESTAMPTZ;

ALTER TABLE ai_triage_recommendations
    ADD CONSTRAINT fk_ai_triage_recommendations_applied_by_user
        FOREIGN KEY (applied_by_user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT;

ALTER TABLE ai_triage_recommendations
    ADD CONSTRAINT chk_ai_triage_recommendations_application_pair
        CHECK (
            (
                applied_by_user_id IS NULL
                AND applied_at IS NULL
            )
            OR
            (
                applied_by_user_id IS NOT NULL
                AND applied_at IS NOT NULL
            )
        );

CREATE INDEX idx_ai_triage_recommendations_applied_by_user
    ON ai_triage_recommendations (applied_by_user_id);

CREATE INDEX idx_ai_triage_recommendations_applied_at
    ON ai_triage_recommendations (applied_at);