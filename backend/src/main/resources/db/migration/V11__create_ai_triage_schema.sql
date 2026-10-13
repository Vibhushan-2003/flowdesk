CREATE TABLE ai_triage_recommendations (
    id UUID PRIMARY KEY,

    ticket_id UUID NOT NULL,

    generated_by_user_id UUID NOT NULL,

    suggested_priority VARCHAR(20) NOT NULL,

    confidence NUMERIC(5, 4) NOT NULL,

    reason VARCHAR(1000) NOT NULL,

    provider VARCHAR(50) NOT NULL,

    model VARCHAR(100) NOT NULL,

    prompt_version VARCHAR(30) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_ai_triage_recommendations_ticket
        UNIQUE (ticket_id),

    CONSTRAINT fk_ai_triage_recommendations_ticket
        FOREIGN KEY (ticket_id)
        REFERENCES tickets(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_ai_triage_recommendations_generated_by
        FOREIGN KEY (generated_by_user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_ai_triage_recommendations_priority
        CHECK (
            suggested_priority IN (
                'LOW',
                'MEDIUM',
                'HIGH',
                'CRITICAL'
            )
        ),

    CONSTRAINT chk_ai_triage_recommendations_confidence
        CHECK (
            confidence >= 0
            AND confidence <= 1
        ),

    CONSTRAINT chk_ai_triage_recommendations_reason
        CHECK (
            CHAR_LENGTH(
                TRIM(reason)
            ) BETWEEN 1 AND 1000
        ),

    CONSTRAINT chk_ai_triage_recommendations_provider
        CHECK (
            CHAR_LENGTH(
                TRIM(provider)
            ) BETWEEN 1 AND 50
        ),

    CONSTRAINT chk_ai_triage_recommendations_model
        CHECK (
            CHAR_LENGTH(
                TRIM(model)
            ) BETWEEN 1 AND 100
        ),

    CONSTRAINT chk_ai_triage_recommendations_prompt_version
        CHECK (
            CHAR_LENGTH(
                TRIM(prompt_version)
            ) BETWEEN 1 AND 30
        )
);


CREATE INDEX idx_ai_triage_recommendations_generated_by
    ON ai_triage_recommendations (
        generated_by_user_id,
        created_at DESC
    );


CREATE INDEX idx_ai_triage_recommendations_created_at
    ON ai_triage_recommendations (
        created_at DESC
    );