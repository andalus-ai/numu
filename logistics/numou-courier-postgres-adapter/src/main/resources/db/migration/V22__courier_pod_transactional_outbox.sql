-- M20: commit POD evidence and its publishable event atomically.
CREATE TABLE courier_event_outbox (
    tenant_id UUID NOT NULL,
    id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_reference VARCHAR(160) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    lease_owner VARCHAR(160),
    lease_until TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, id),
    CONSTRAINT ck_courier_outbox_event_type CHECK (length(trim(event_type)) BETWEEN 1 AND 120),
    CONSTRAINT ck_courier_outbox_aggregate_type CHECK (length(trim(aggregate_type)) BETWEEN 1 AND 80),
    CONSTRAINT ck_courier_outbox_aggregate_ref CHECK (length(trim(aggregate_reference)) BETWEEN 1 AND 160),
    CONSTRAINT ck_courier_outbox_payload_object CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_courier_outbox_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_courier_outbox_lease_pair CHECK ((lease_owner IS NULL) = (lease_until IS NULL)),
    CONSTRAINT ck_courier_outbox_published_unleased CHECK (published_at IS NULL OR lease_owner IS NULL)
);

CREATE INDEX ix_courier_outbox_ready
    ON courier_event_outbox (available_at, created_at)
    WHERE published_at IS NULL;

CREATE INDEX ix_courier_outbox_tenant_aggregate
    ON courier_event_outbox (tenant_id, aggregate_type, aggregate_reference, occurred_at);
