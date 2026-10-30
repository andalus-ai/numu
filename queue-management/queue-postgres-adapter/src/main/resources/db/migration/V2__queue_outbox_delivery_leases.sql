-- M5: durable, fenced outbox delivery leases.
-- Claiming is at-least-once: a dispatcher may publish and crash before acknowledging.
-- Consumers must deduplicate by event_id. Lease tokens fence stale workers from ack/retry.
ALTER TABLE queue_outbox
    ADD COLUMN available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN lease_owner VARCHAR(200),
    ADD COLUMN lease_token UUID,
    ADD COLUMN lease_until TIMESTAMPTZ,
    ADD COLUMN last_attempt_at TIMESTAMPTZ,
    ADD COLUMN dead_lettered_at TIMESTAMPTZ;

ALTER TABLE queue_outbox
    ADD CONSTRAINT ck_queue_outbox_lease_tuple CHECK (
        (lease_owner IS NULL AND lease_token IS NULL AND lease_until IS NULL)
        OR
        (lease_owner IS NOT NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
    ),
    ADD CONSTRAINT ck_queue_outbox_terminal_state CHECK (
        NOT (published_at IS NOT NULL AND dead_lettered_at IS NOT NULL)
        AND (published_at IS NULL OR (lease_owner IS NULL AND lease_token IS NULL AND lease_until IS NULL))
        AND (dead_lettered_at IS NULL OR (lease_owner IS NULL AND lease_token IS NULL AND lease_until IS NULL))
    ),
    ADD CONSTRAINT ck_queue_outbox_attempt_timestamps CHECK (
        last_attempt_at IS NULL OR last_attempt_at >= created_at
    );

-- The claim path only scans due, non-terminal rows whose lease is absent or expired.
CREATE INDEX ix_queue_outbox_due
    ON queue_outbox (tenant_id, available_at, created_at, event_id)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;

-- Expired leases can be reclaimed without scanning the complete outbox.
CREATE INDEX ix_queue_outbox_expired_lease
    ON queue_outbox (lease_until, tenant_id, event_id)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL AND lease_token IS NOT NULL;

COMMENT ON COLUMN queue_outbox.lease_token IS
    'Fencing token generated for each claim; ack/retry must match the current token.';
COMMENT ON COLUMN queue_outbox.available_at IS
    'Earliest instant at which an unpublished event may be claimed, including retry backoff.';
COMMENT ON COLUMN queue_outbox.dead_lettered_at IS
    'Terminal timestamp after the configured delivery-attempt limit; operator replay is explicit.';
