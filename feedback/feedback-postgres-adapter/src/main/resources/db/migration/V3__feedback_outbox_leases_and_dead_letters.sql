ALTER TABLE feedback_outbox
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN lease_until TIMESTAMPTZ NULL,
    ADD COLUMN dead_lettered_at TIMESTAMPTZ NULL,
    ADD COLUMN lease_token UUID NULL,
    ADD CONSTRAINT feedback_outbox_lease_pair_ck
        CHECK ((lease_until IS NULL) = (lease_token IS NULL)),
    ADD CONSTRAINT feedback_outbox_terminal_state_ck
        CHECK (NOT (published_at IS NOT NULL AND dead_lettered_at IS NOT NULL));

CREATE INDEX feedback_outbox_claim_idx
    ON feedback_outbox (next_attempt_at, created_at, event_id)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;

CREATE INDEX feedback_outbox_lease_expiry_idx
    ON feedback_outbox (lease_until)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL AND lease_until IS NOT NULL;
