CREATE TABLE IF NOT EXISTS pos_checkout_session (
    tenant_id VARCHAR(80) NOT NULL,
    checkout_id UUID NOT NULL,
    sale_id UUID NOT NULL,
    amount NUMERIC(24, 8) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'STOCK_PENDING', 'PAYMENT_PENDING', 'PAYMENT_UNKNOWN', 'PAID',
        'COMPLETED', 'COMPENSATION_REQUIRED', 'CANCELLED'
    )),
    reservation_reference VARCHAR(200),
    payment_id VARCHAR(200),
    version BIGINT NOT NULL CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, checkout_id),
    CHECK (updated_at >= created_at),
    CHECK (status = 'STOCK_PENDING' OR reservation_reference IS NOT NULL),
    CHECK (status NOT IN ('PAYMENT_PENDING', 'PAYMENT_UNKNOWN', 'PAID', 'COMPLETED') OR payment_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS ix_pos_checkout_reconciliation
    ON pos_checkout_session (tenant_id, status, updated_at)
    WHERE status IN ('PAYMENT_PENDING', 'PAYMENT_UNKNOWN', 'PAID');
