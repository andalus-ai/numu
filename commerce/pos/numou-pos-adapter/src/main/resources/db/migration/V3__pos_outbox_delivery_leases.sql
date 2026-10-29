-- Lease fencing prevents an expired worker from completing a row reclaimed by another worker.
ALTER TABLE pos_outbox_event
    ADD COLUMN IF NOT EXISTS lease_token UUID,
    ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ;

-- Existing IN_FLIGHT rows from the initial schema are immediately eligible for recovery.
UPDATE pos_outbox_event
   SET delivery_status = 'PENDING', locked_at = NULL
 WHERE delivery_status = 'IN_FLIGHT';

CREATE INDEX IF NOT EXISTS ix_pos_outbox_due
    ON pos_outbox_event (available_at, created_at)
    WHERE delivery_status = 'PENDING';

CREATE INDEX IF NOT EXISTS ix_pos_outbox_expired_lease
    ON pos_outbox_event (lease_until)
    WHERE delivery_status = 'IN_FLIGHT';
