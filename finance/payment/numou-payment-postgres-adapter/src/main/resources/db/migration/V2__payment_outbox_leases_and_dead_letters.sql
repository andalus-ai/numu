-- Leases allow competing workers to claim events without holding a transaction
-- open while publishing. Delivery is at-least-once, and consumers must be idempotent.
ALTER TABLE payment_operation_outbox
    ADD COLUMN IF NOT EXISTS terminal BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS worker_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS lease_token UUID,
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS max_attempts INTEGER NOT NULL DEFAULT 12
        CHECK (max_attempts >= 1);

CREATE INDEX IF NOT EXISTS ix_payment_operation_outbox_claimable
    ON payment_operation_outbox (next_attempt_at, occurred_at, event_id)
    WHERE published_at IS NULL AND terminal = FALSE;
