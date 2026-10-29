CREATE TABLE IF NOT EXISTS purchasing_receipt_inventory_posting (
    tenant_id            VARCHAR(128) NOT NULL,
    receipt_id           VARCHAR(256) NOT NULL,
    payload_fingerprint  VARCHAR(128) NOT NULL,
    status               VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'POSTED')),
    movement_id          VARCHAR(256),
    attempts             INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_failure         TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, receipt_id),
    CHECK ((status = 'POSTED' AND movement_id IS NOT NULL) OR status = 'PENDING')
);

CREATE INDEX IF NOT EXISTS idx_purchasing_receipt_posting_pending
    ON purchasing_receipt_inventory_posting (updated_at)
    WHERE status = 'PENDING';
