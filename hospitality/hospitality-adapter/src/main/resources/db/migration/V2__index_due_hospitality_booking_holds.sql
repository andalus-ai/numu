-- Support the global bounded expiry scan while retaining the tenant-scoped index from V1.
-- The key order matches expireDue's deterministic ORDER BY and the partial predicate
-- keeps confirmed/cancelled/expired history out of the worker's index.
CREATE INDEX IF NOT EXISTS ix_hospitality_booking_due_expiry
    ON hospitality_stay_booking (hold_expires_at, tenant_id, booking_id)
    WHERE booking_status = 'HELD';
