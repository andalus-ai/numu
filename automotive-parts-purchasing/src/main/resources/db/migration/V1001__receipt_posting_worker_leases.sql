ALTER TABLE purchasing_receipt_inventory_posting
    ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(256),
    ADD COLUMN IF NOT EXISTS lease_until TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_purchasing_receipt_posting_lease
    ON purchasing_receipt_inventory_posting (lease_until, updated_at)
    WHERE status = 'PENDING';
