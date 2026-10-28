CREATE TABLE IF NOT EXISTS marketplace_checkout (
    checkout_id UUID PRIMARY KEY,
    tenant_id VARCHAR(200) NOT NULL,
    order_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    checkout_status VARCHAR(40) NOT NULL CHECK (checkout_status IN (
        'RESERVING_INVENTORY', 'AWAITING_PAYMENT', 'READY_FOR_FULFILLMENT',
        'COMPENSATION_REQUIRED', 'RECONCILIATION_REQUIRED', 'FAILED'
    )),
    payment_id VARCHAR(255),
    failure_code VARCHAR(120),
    failure_detail VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_marketplace_checkout_tenant_id UNIQUE (tenant_id, checkout_id),
    CONSTRAINT uq_marketplace_checkout_key UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_marketplace_checkout_order FOREIGN KEY (tenant_id, order_id)
        REFERENCES marketplace_order (tenant_id, order_id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_marketplace_checkout_recovery
    ON marketplace_checkout (tenant_id, checkout_status, updated_at);

-- A failed attempt can be retried with a new key, but only one non-failed checkout
-- may coordinate inventory/payment for a given order at a time.
CREATE UNIQUE INDEX IF NOT EXISTS uq_marketplace_checkout_active_order
    ON marketplace_checkout (tenant_id, order_id)
    WHERE checkout_status <> 'FAILED';

CREATE TABLE IF NOT EXISTS marketplace_checkout_reservation (
    tenant_id VARCHAR(200) NOT NULL,
    checkout_id UUID NOT NULL,
    line_id VARCHAR(200) NOT NULL,
    reservation_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, checkout_id, line_id),
    CONSTRAINT uq_marketplace_checkout_reservation UNIQUE (tenant_id, reservation_id),
    CONSTRAINT fk_marketplace_checkout_reservation_checkout
        FOREIGN KEY (tenant_id, checkout_id)
        REFERENCES marketplace_checkout (tenant_id, checkout_id) ON DELETE RESTRICT
);
