ALTER TABLE transport_checkout
    ADD COLUMN IF NOT EXISTS refund_id VARCHAR(256),
    ADD COLUMN IF NOT EXISTS refund_status VARCHAR(32);

ALTER TABLE transport_checkout
    DROP CONSTRAINT IF EXISTS ck_transport_checkout_refund_state;

ALTER TABLE transport_checkout
    ADD CONSTRAINT ck_transport_checkout_refund_state
    CHECK (
        (refund_id IS NULL AND refund_status IS NULL)
        OR
        (refund_id IS NOT NULL AND refund_status IN ('REQUESTED', 'PENDING', 'SUCCEEDED', 'FAILED', 'UNKNOWN'))
    );

CREATE UNIQUE INDEX IF NOT EXISTS uq_transport_checkout_refund
    ON transport_checkout (tenant_id, refund_id)
    WHERE refund_id IS NOT NULL;
