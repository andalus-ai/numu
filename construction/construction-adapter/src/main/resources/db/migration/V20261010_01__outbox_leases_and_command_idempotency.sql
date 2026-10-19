-- Follow-up to V20261009_01. Verify migration naming against the deployed Flyway setup.
-- Lease fields allow competing workers to claim rows without holding a transaction
-- open while calling a broker. Delivery remains at-least-once.
ALTER TABLE construction_outbox_event
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS worker_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS lease_token UUID,
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS max_attempts INTEGER NOT NULL DEFAULT 12 CHECK (max_attempts >= 1);

CREATE INDEX IF NOT EXISTS idx_construction_outbox_claimable
    ON construction_outbox_event (available_at, occurred_at)
    WHERE status IN ('PENDING', 'FAILED', 'PUBLISHING');

CREATE TABLE IF NOT EXISTS construction_command_idempotency (
    tenant_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    operation VARCHAR(160) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('PROCESSING', 'COMPLETED')),
    response_json JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NULL,
    expires_at TIMESTAMPTZ NULL,
    PRIMARY KEY (tenant_id, idempotency_key),
    CHECK ((state <> 'COMPLETED') OR (completed_at IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_construction_command_idempotency_expiry
    ON construction_command_idempotency (expires_at)
    WHERE expires_at IS NOT NULL;
