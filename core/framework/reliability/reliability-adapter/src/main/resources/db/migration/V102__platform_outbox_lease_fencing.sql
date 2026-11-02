-- Fence outbox state transitions with a per-claim token.
-- Relay workers must be upgraded together: pre-fencing workers do not supply tokens.
ALTER TABLE platform_outbox
    ADD COLUMN IF NOT EXISTS lease_token uuid;

CREATE INDEX IF NOT EXISTS platform_outbox_lease_idx
    ON platform_outbox (locked_until, available_at, created_at)
    WHERE published_at IS NULL AND attempts < 10;
