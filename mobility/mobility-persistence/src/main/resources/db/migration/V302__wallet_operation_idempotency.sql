-- Durable wallet operation identity. The aggregate's bounded in-memory ref cache is only a fast path;
-- this index makes the ledger the lifetime idempotency registry for each tenant + wallet + operation ref.
--
-- Fail migration explicitly if historical data already violates this invariant; silently deleting or
-- choosing among financial movements would be unsafe. Reconcile those rows before retrying migration.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM wallet_ledger
        GROUP BY tenant_id, wallet_id, ref
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot enforce wallet operation idempotency: duplicate (tenant_id, wallet_id, ref) rows exist; reconcile ledger history first';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS wallet_ledger_operation_ref_uq
    ON wallet_ledger (tenant_id, wallet_id, ref);
