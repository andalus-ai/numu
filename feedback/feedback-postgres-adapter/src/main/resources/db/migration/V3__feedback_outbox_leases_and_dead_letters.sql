ALTER TABLE feedback_outbox
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN lease_until TIMESTAMPTZ NULL,
    ADD COLUMN dead_lettered_at TIMESTAMPTZ NULL;

CREATE INDEX feedback_outbox_claim_idx
    ON feedback_outbox (next_attempt_at, created_at, event_id)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;

CREATE INDEX feedback_outbox_lease_expiry_idx
    ON feedback_outbox (lease_until)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL AND lease_until IS NOT NULL;
