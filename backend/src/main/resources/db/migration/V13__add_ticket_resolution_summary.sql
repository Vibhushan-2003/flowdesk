ALTER TABLE tickets
    ADD COLUMN resolution_summary TEXT;

ALTER TABLE tickets
    ADD CONSTRAINT chk_ticket_resolution_summary_length
    CHECK (
        resolution_summary IS NULL
        OR (
            char_length(btrim(resolution_summary)) >= 1
            AND char_length(resolution_summary) <= 4000
        )
    );