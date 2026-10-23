-- Durable worker leases prevent multiple recovery workers from selecting the same live attempt.
ALTER TABLE warehouse_execution_attempt
    ADD COLUMN claim_owner VARCHAR(200),
    ADD COLUMN claim_until TIMESTAMPTZ;

CREATE INDEX ix_warehouse_execution_claim_expiry
    ON warehouse_execution_attempt (claim_until, updated_at)
    WHERE status IN ('PREPARED', 'INVENTORY_POSTED', 'NEEDS_RECONCILIATION');
