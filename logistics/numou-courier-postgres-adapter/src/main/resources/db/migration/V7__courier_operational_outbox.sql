CREATE TABLE courier_operational_outbox (
    tenant_id UUID NOT NULL,
    id UUID NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    shipment_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_key VARCHAR(240) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error VARCHAR(1000),
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_courier_outbox_tenant_event_key UNIQUE (tenant_id, event_key),
    CONSTRAINT fk_courier_outbox_shipment FOREIGN KEY (tenant_id, shipment_id)
        REFERENCES courier_shipments (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_courier_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_courier_outbox_published CHECK (published_at IS NULL OR published_at >= created_at)
);

CREATE INDEX ix_courier_outbox_pending
    ON courier_operational_outbox (next_attempt_at, created_at, id)
    WHERE published_at IS NULL;

CREATE INDEX ix_courier_outbox_tenant_shipment
    ON courier_operational_outbox (tenant_id, shipment_id, created_at DESC);

COMMENT ON TABLE courier_operational_outbox IS
    'Transactional domain event outbox. Dispatch workers must claim rows with FOR UPDATE SKIP LOCKED and publish idempotently.';
