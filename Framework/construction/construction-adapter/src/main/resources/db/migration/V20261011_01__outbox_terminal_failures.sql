-- Terminal failures must not be reclaimed by the relay. Verify naming/order against
-- the real Flyway setup before applying this migration.
ALTER TABLE construction_outbox_event
    ADD COLUMN IF NOT EXISTS terminal BOOLEAN NOT NULL DEFAULT FALSE;

DROP INDEX IF EXISTS idx_construction_outbox_claimable;
CREATE INDEX idx_construction_outbox_claimable
    ON construction_outbox_event (available_at, occurred_at, event_id)
    WHERE terminal = FALSE
      AND status IN ('PENDING', 'FAILED', 'PUBLISHING');
