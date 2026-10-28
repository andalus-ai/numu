-- Existing checkouts predate durable reservation request snapshots. Leave their values NULL
-- so recovery can route them to explicit operator reconciliation rather than guessing.
ALTER TABLE marketplace_checkout
    ADD COLUMN IF NOT EXISTS warehouse_reference VARCHAR(200),
    ADD COLUMN IF NOT EXISTS reservation_expires_at TIMESTAMPTZ;

ALTER TABLE marketplace_checkout
    ADD CONSTRAINT ck_marketplace_checkout_snapshot_pair
    CHECK ((warehouse_reference IS NULL AND reservation_expires_at IS NULL)
        OR (warehouse_reference IS NOT NULL AND length(trim(warehouse_reference)) > 0
            AND reservation_expires_at IS NOT NULL));