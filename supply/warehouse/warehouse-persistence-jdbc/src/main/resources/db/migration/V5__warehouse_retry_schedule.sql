-- Durable retry eligibility for warehouse execution recovery.
-- M25 assigns this change a unique Flyway version because the former V2 retry
-- migration collided with V2__warehouse_transactional_execution.sql.
-- IF NOT EXISTS keeps this forward migration usable where objects were created
-- manually or by the legacy retry migration.
ALTER TABLE warehouse_execution_attempt
    ADD COLUMN IF NOT EXISTS next_eligible_at TIMESTAMPTZ NULL;

CREATE INDEX IF NOT EXISTS ix_warehouse_execution_retry_eligible
    ON warehouse_execution_attempt (next_eligible_at, updated_at, created_at)
    WHERE status IN ('PREPARED', 'INVENTORY_POSTED', 'NEEDS_RECONCILIATION');

CREATE INDEX IF NOT EXISTS ix_warehouse_execution_dead_letter
    ON warehouse_execution_attempt (updated_at, created_at)
    WHERE status = 'DEAD_LETTER';

-- M20 adds a terminal state; extend the original V1 status constraint without
-- rewriting existing rows. Drop/re-add makes the intended allowed set explicit.
ALTER TABLE warehouse_execution_attempt
    DROP CONSTRAINT IF EXISTS warehouse_execution_attempt_status_check;

ALTER TABLE warehouse_execution_attempt
    ADD CONSTRAINT warehouse_execution_attempt_status_check
    CHECK (status IN ('PREPARED', 'INVENTORY_POSTED', 'WAREHOUSE_COMMITTED',
                      'NEEDS_RECONCILIATION', 'DEAD_LETTER'));
