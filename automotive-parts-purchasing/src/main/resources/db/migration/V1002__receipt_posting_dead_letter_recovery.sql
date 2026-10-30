ALTER TABLE purchasing_receipt_inventory_posting
    DROP CONSTRAINT IF EXISTS purchasing_receipt_inventory_posting_status_check,
    DROP CONSTRAINT IF EXISTS purchasing_receipt_inventory_posting_check;

ALTER TABLE purchasing_receipt_inventory_posting
    ADD COLUMN IF NOT EXISTS max_attempts INTEGER NOT NULL DEFAULT 5 CHECK (max_attempts BETWEEN 1 AND 100),
    ADD COLUMN IF NOT EXISTS terminal_failure_reason TEXT,
    ADD COLUMN IF NOT EXISTS dead_lettered_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS requeued_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS requeued_by VARCHAR(256),
    ADD COLUMN IF NOT EXISTS requeue_reason TEXT;

ALTER TABLE purchasing_receipt_inventory_posting
    ADD CONSTRAINT purchasing_receipt_inventory_posting_status_check
        CHECK (status IN ('PENDING', 'POSTED', 'DEAD_LETTER')),
    ADD CONSTRAINT purchasing_receipt_inventory_posting_movement_state_check
        CHECK (status <> 'POSTED' OR movement_id IS NOT NULL),
    ADD CONSTRAINT purchasing_receipt_inventory_posting_terminal_state_check
        CHECK (status <> 'DEAD_LETTER' OR
               (dead_lettered_at IS NOT NULL AND terminal_failure_reason IS NOT NULL));

CREATE INDEX IF NOT EXISTS idx_purchasing_receipt_posting_dead_letter
    ON purchasing_receipt_inventory_posting (tenant_id, dead_lettered_at DESC, receipt_id)
    WHERE status = 'DEAD_LETTER';
