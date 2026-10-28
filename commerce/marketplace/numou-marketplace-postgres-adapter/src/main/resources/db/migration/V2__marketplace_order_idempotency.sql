CREATE TABLE IF NOT EXISTS marketplace_order_idempotency (
    tenant_id VARCHAR(200) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    order_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, idempotency_key),
    CONSTRAINT uq_marketplace_order_idempotency_order UNIQUE (tenant_id, order_id)
);

CREATE INDEX IF NOT EXISTS ix_marketplace_order_idempotency_created
    ON marketplace_order_idempotency (tenant_id, created_at DESC);
