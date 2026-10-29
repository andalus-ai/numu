CREATE TABLE IF NOT EXISTS pos_outbox_event (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    aggregate_type VARCHAR(120) NOT NULL,
    aggregate_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    schema_version INTEGER NOT NULL CHECK (schema_version >= 1),
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    delivery_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (delivery_status IN ('PENDING', 'IN_FLIGHT', 'PUBLISHED', 'DEAD')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        (delivery_status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR
        (delivery_status <> 'PUBLISHED' AND published_at IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS ix_pos_outbox_pending
    ON pos_outbox_event (available_at, created_at)
    WHERE delivery_status IN ('PENDING', 'IN_FLIGHT');

CREATE INDEX IF NOT EXISTS ix_pos_outbox_tenant_aggregate
    ON pos_outbox_event (tenant_id, aggregate_type, aggregate_id, created_at);
