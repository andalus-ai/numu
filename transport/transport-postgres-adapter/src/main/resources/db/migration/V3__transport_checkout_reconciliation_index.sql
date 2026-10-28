-- The reconciler scans all tenants in a stable oldest-first order.
-- Keep this partial index separate from tenant-scoped point lookups.
CREATE INDEX IF NOT EXISTS ix_transport_checkout_due_reconciliation
    ON transport_checkout (updated_at ASC, tenant_id ASC, checkout_id ASC)
    WHERE status IN ('REQUESTED', 'HOLD_CREATED', 'PAYMENT_PENDING', 'PAYMENT_ACTION_REQUIRED');
