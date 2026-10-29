CREATE INDEX IF NOT EXISTS ix_transport_checkout_operator_queue
    ON transport_checkout (tenant_id, updated_at ASC, checkout_id ASC)
    WHERE status IN ('RECONCILIATION_REQUIRED', 'REFUND_REQUESTED', 'REFUND_PENDING', 'REFUND_FAILED');
