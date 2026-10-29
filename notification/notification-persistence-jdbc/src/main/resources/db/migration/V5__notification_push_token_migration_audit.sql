-- Auditable outcomes for bounded legacy push-token encryption batches.
-- This table deliberately stores operational counts only, never token material.
CREATE TABLE notification_push_token_migration_audit (
    id UUID PRIMARY KEY,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    scanned_count INTEGER NOT NULL CHECK (scanned_count >= 0),
    migrated_count INTEGER NOT NULL CHECK (migrated_count >= 0),
    remaining_count BIGINT NOT NULL CHECK (remaining_count >= 0),
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('SUCCEEDED', 'FAILED')),
    failure_code VARCHAR(64) NULL,
    CONSTRAINT notification_push_token_migration_audit_outcome_shape
        CHECK ((outcome = 'SUCCEEDED' AND failure_code IS NULL)
            OR (outcome = 'FAILED' AND failure_code IS NOT NULL))
);

CREATE INDEX notification_push_token_migration_audit_started
    ON notification_push_token_migration_audit (started_at DESC, id DESC);
