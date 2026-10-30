CREATE TABLE IF NOT EXISTS purchasing_receipt_inventory_posting_requeue_audit (
    audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(128) NOT NULL,
    receipt_id VARCHAR(256) NOT NULL,
    payload_fingerprint VARCHAR(128) NOT NULL,
    actor_id VARCHAR(256) NOT NULL,
    reason TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_receipt_posting_requeue_audit
        FOREIGN KEY (tenant_id, receipt_id)
        REFERENCES purchasing_receipt_inventory_posting (tenant_id, receipt_id)
);

CREATE INDEX IF NOT EXISTS idx_receipt_posting_requeue_audit_history
    ON purchasing_receipt_inventory_posting_requeue_audit (tenant_id, receipt_id, occurred_at DESC, audit_id);

CREATE OR REPLACE FUNCTION prevent_receipt_posting_requeue_audit_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'receipt posting requeue audit is append-only';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_receipt_posting_requeue_audit_immutable
    ON purchasing_receipt_inventory_posting_requeue_audit;
CREATE TRIGGER trg_receipt_posting_requeue_audit_immutable
    BEFORE UPDATE OR DELETE ON purchasing_receipt_inventory_posting_requeue_audit
    FOR EACH ROW EXECUTE FUNCTION prevent_receipt_posting_requeue_audit_mutation();

CREATE OR REPLACE FUNCTION record_receipt_posting_requeue()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = 'DEAD_LETTER'
       AND NEW.status = 'PENDING'
       AND NEW.requeued_at IS DISTINCT FROM OLD.requeued_at THEN
        INSERT INTO purchasing_receipt_inventory_posting_requeue_audit
            (tenant_id, receipt_id, payload_fingerprint, actor_id, reason, occurred_at)
        VALUES
            (NEW.tenant_id, NEW.receipt_id, NEW.payload_fingerprint,
             NEW.requeued_by, NEW.requeue_reason, NEW.requeued_at);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_receipt_posting_requeue_audit_capture
    ON purchasing_receipt_inventory_posting;
CREATE TRIGGER trg_receipt_posting_requeue_audit_capture
    AFTER UPDATE ON purchasing_receipt_inventory_posting
    FOR EACH ROW EXECUTE FUNCTION record_receipt_posting_requeue();
