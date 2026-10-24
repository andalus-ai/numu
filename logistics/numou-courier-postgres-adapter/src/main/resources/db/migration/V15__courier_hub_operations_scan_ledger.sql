-- M12: durable scan intake and operational exception audit.
-- Adapters must insert the scan receipt, append tracking facts, and enqueue outbox
-- messages in one database transaction. A repeated idempotency key with the same
-- fingerprint is a replay; a different fingerprint is a conflict and must not mutate
-- the original receipt or produce another tracking/outbox event.

CREATE TABLE courier_hub_scan_receipts (
    tenant_id UUID NOT NULL,
    id UUID NOT NULL,
    source VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    payload_fingerprint VARCHAR(128) NOT NULL,
    shipment_id UUID NOT NULL,
    parcel_reference VARCHAR(160) NOT NULL,
    scan_type VARCHAR(40) NOT NULL,
    hub_reference VARCHAR(160) NOT NULL,
    actor_reference VARCHAR(160),
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_courier_scan_idempotency UNIQUE (tenant_id, source, idempotency_key),
    CONSTRAINT fk_courier_scan_shipment FOREIGN KEY (tenant_id, shipment_id)
        REFERENCES courier_shipments (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_courier_scan_type CHECK (scan_type IN (
        'PICKUP_COLLECTED', 'HUB_INTAKE', 'SORTED', 'DISPATCH_LOADED',
        'CONTAINER_SEALED', 'CONTAINER_DISPATCHED', 'HUB_RECEIVED',
        'CONTAINER_RECEIVED', 'EXCEPTION_RECORDED'
    )),
    CONSTRAINT ck_courier_scan_fingerprint CHECK (length(trim(payload_fingerprint)) BETWEEN 1 AND 128),
    CONSTRAINT ck_courier_scan_timestamps CHECK (received_at >= occurred_at AND accepted_at >= received_at)
);

CREATE INDEX ix_courier_scan_shipment_timeline
    ON courier_hub_scan_receipts (tenant_id, shipment_id, occurred_at DESC, id DESC);

CREATE INDEX ix_courier_scan_hub_queue
    ON courier_hub_scan_receipts (tenant_id, hub_reference, scan_type, received_at DESC);

CREATE TABLE courier_hub_operation_audit (
    tenant_id UUID NOT NULL,
    id UUID NOT NULL,
    operation_type VARCHAR(48) NOT NULL,
    aggregate_reference VARCHAR(160) NOT NULL,
    decision VARCHAR(24) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    actor_reference VARCHAR(160) NOT NULL,
    source VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_courier_operation_audit_idempotency UNIQUE (tenant_id, source, idempotency_key),
    CONSTRAINT ck_courier_operation_audit_decision CHECK (decision IN ('APPROVED','REJECTED','OVERRIDDEN')),
    CONSTRAINT ck_courier_operation_audit_reason CHECK (length(trim(reason)) BETWEEN 1 AND 500),
    CONSTRAINT ck_courier_operation_audit_actor CHECK (length(trim(actor_reference)) BETWEEN 1 AND 160),
    CONSTRAINT ck_courier_operation_audit_timestamps CHECK (recorded_at >= occurred_at)
);

CREATE INDEX ix_courier_operation_audit_aggregate
    ON courier_hub_operation_audit (tenant_id, aggregate_reference, recorded_at DESC);

COMMENT ON TABLE courier_hub_scan_receipts IS
    'Idempotency ledger for hub scans. Duplicate delivery must not create duplicate tracking facts or outbox events.';
COMMENT ON TABLE courier_hub_operation_audit IS
    'Append-only operational decision evidence; authorization must be enforced by the application before insertion.';
