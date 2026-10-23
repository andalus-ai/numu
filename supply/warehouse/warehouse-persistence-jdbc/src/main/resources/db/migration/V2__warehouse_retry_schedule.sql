-- Durable retry eligibility for warehouse execution recovery.
-- Existing rows remain immediately eligible (NULL) for backward-compatible rollout.
ALTER TABLE warehouse_execution_attempt
    ADD COLUMN next_eligible_at TIMESTAMPTZ NULL;

CREATE INDEX ix_warehouse_execution_retry_eligible
    ON warehouse_execution_attempt (next_eligible_at, updated_at, created_at)
    WHERE status IN ('PREPARED', 'INVENTORY_POSTED', 'NEEDS_RECONCILIATION');

CREATE INDEX ix_warehouse_execution_dead_letter
    ON warehouse_execution_attempt (updated_at, created_at)
    WHERE status = 'DEAD_LETTER';
