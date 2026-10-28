CREATE TABLE IF NOT EXISTS marketplace_order (
    order_id UUID PRIMARY KEY,
    tenant_id VARCHAR(200) NOT NULL,
    buyer_id VARCHAR(200) NOT NULL,
    currency CHAR(3) NOT NULL,
    order_status VARCHAR(40) NOT NULL CHECK (order_status IN (
        'CREATED', 'SUBMITTED', 'SELLER_ACCEPTED', 'SELLER_REJECTED',
        'FULFILLMENT_REQUESTED', 'CANCELLED', 'COMPLETED'
    )),
    cancellation_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
    CONSTRAINT uq_marketplace_order_tenant_id UNIQUE (tenant_id, order_id),
    CONSTRAINT ck_marketplace_order_cancel_reason CHECK (
        (order_status = 'CANCELLED' AND cancellation_reason IS NOT NULL)
        OR (order_status <> 'CANCELLED')
    )
);

CREATE INDEX IF NOT EXISTS ix_marketplace_order_tenant_buyer_created
    ON marketplace_order (tenant_id, buyer_id, created_at DESC);

CREATE TABLE IF NOT EXISTS marketplace_order_line (
    tenant_id VARCHAR(200) NOT NULL,
    order_id UUID NOT NULL,
    line_index INTEGER NOT NULL CHECK (line_index >= 0),
    line_id VARCHAR(200) NOT NULL,
    product_id VARCHAR(200) NOT NULL,
    offering_id VARCHAR(200) NOT NULL,
    seller_id VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    quantity NUMERIC(38, 18) NOT NULL CHECK (quantity > 0),
    unit VARCHAR(100) NOT NULL,
    unit_price NUMERIC(38, 18) NOT NULL CHECK (unit_price >= 0),
    currency CHAR(3) NOT NULL,
    PRIMARY KEY (tenant_id, order_id, line_index),
    CONSTRAINT uq_marketplace_order_line_id UNIQUE (tenant_id, order_id, line_id),
    CONSTRAINT fk_marketplace_order_line_tenant_order
        FOREIGN KEY (tenant_id, order_id)
        REFERENCES marketplace_order (tenant_id, order_id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_marketplace_order_line_seller
    ON marketplace_order_line (tenant_id, seller_id, order_id);