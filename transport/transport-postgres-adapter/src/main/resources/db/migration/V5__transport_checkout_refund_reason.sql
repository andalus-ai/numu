ALTER TABLE transport_checkout
    ADD COLUMN IF NOT EXISTS refund_reason VARCHAR(500);

ALTER TABLE transport_checkout
    DROP CONSTRAINT IF EXISTS ck_transport_checkout_refund_state;

ALTER TABLE transport_checkout
    ADD CONSTRAINT ck_transport_checkout_refund_state
    CHECK (
        (refund_id IS NULL AND refund_status IS NULL AND refund_reason IS NULL)
        OR
        (refund_id IS NULL AND refund_status IS NULL AND status = 'REFUND_REQUESTED'
            AND refund_reason IS NOT NULL AND length(trim(refund_reason)) BETWEEN 1 AND 500)
        OR
        (refund_id IS NOT NULL AND refund_status IS NOT NULL
            AND refund_status IN ('REQUESTED', 'PENDING', 'SUCCEEDED', 'FAILED', 'UNKNOWN')
            AND refund_reason IS NOT NULL AND length(trim(refund_reason)) BETWEEN 1 AND 500)
    );

ALTER TABLE transport_checkout
    DROP CONSTRAINT IF EXISTS ck_transport_checkout_status;

ALTER TABLE transport_checkout
    ADD CONSTRAINT ck_transport_checkout_status
    CHECK (status IN (
        'REQUESTED', 'HOLD_CREATED', 'PAYMENT_PENDING', 'PAYMENT_ACTION_REQUIRED',
        'PAYMENT_SUCCEEDED', 'PAYMENT_FAILED', 'CANCELLED', 'RECONCILIATION_REQUIRED',
        'REFUND_REQUESTED', 'REFUND_PENDING', 'REFUNDED', 'REFUND_FAILED'
    ));
