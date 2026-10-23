CREATE TABLE warehouse_work_order (
    tenant_id VARCHAR(160) NOT NULL,
    work_order_id VARCHAR(160) NOT NULL,
    aggregate_version BIGINT NOT NULL CHECK (aggregate_version > 0),
    snapshot_payload TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, work_order_id)
);

CREATE TABLE warehouse_command_receipt (
    tenant_id VARCHAR(160) NOT NULL,
    command_key VARCHAR(200) NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    work_order_id VARCHAR(160) NOT NULL,
    result_version BIGINT NOT NULL CHECK (result_version > 0),
    result_payload TEXT NOT NULL,
    committed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, command_key)
);

CREATE INDEX warehouse_receipt_work_order_idx
    ON warehouse_command_receipt (tenant_id, work_order_id, committed_at);

CREATE TABLE warehouse_outbox_event (
    event_id VARCHAR(160) PRIMARY KEY,
    tenant_id VARCHAR(160) NOT NULL,
    aggregate_id VARCHAR(160) NOT NULL,
    aggregate_version BIGINT NOT NULL CHECK (aggregate_version > 0),
    event_type VARCHAR(200) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    claim_owner VARCHAR(200),
    claim_until TIMESTAMPTZ,
    delivery_attempts INTEGER NOT NULL DEFAULT 0 CHECK (delivery_attempts >= 0),
    last_error TEXT
);

CREATE INDEX warehouse_outbox_pending_idx
    ON warehouse_outbox_event (occurred_at, event_id)
    WHERE published_at IS NULL;

CREATE INDEX warehouse_outbox_claim_expiry_idx
    ON warehouse_outbox_event (claim_until)
    WHERE published_at IS NULL;
