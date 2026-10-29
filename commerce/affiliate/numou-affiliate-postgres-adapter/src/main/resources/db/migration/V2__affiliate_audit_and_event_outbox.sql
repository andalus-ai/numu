-- Durable, tenant-scoped audit trail and transactional dispatch outbox for Affiliate.
-- Domain mutations must insert these records in the same transaction as the state change.

CREATE TABLE IF NOT EXISTS affiliate_audit_event (
    event_id UUID PRIMARY KEY,
    tenant_id VARCHAR(200) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255),
    idempotency_key VARCHAR(255),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_affiliate_audit_tenant_event UNIQUE (tenant_id, event_id)
);

CREATE INDEX IF NOT EXISTS ix_affiliate_audit_aggregate_timeline
    ON affiliate_audit_event (tenant_id, aggregate_type, aggregate_id, occurred_at, event_id);

CREATE INDEX IF NOT EXISTS ix_affiliate_audit_tenant_timeline
    ON affiliate_audit_event (tenant_id, occurred_at DESC);

-- This table is an integration outbox, not a replacement for the audit trail.
-- Consumers must be idempotent; delivery is at-least-once, not exactly-once.
CREATE TABLE IF NOT EXISTS affiliate_event_outbox (
    outbox_id UUID PRIMARY KEY,
    tenant_id VARCHAR(200) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    event_key VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    delivery_status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        CHECK (delivery_status IN ('PENDING', 'IN_FLIGHT', 'DELIVERED', 'DEAD_LETTER')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(255),
    lease_until TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uq_affiliate_outbox_tenant_event_key UNIQUE (tenant_id, event_key),
    CONSTRAINT uq_affiliate_outbox_tenant_id UNIQUE (tenant_id, outbox_id),
    CONSTRAINT ck_affiliate_outbox_lease CHECK (
        (delivery_status = 'IN_FLIGHT' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL)
        OR (delivery_status <> 'IN_FLIGHT')
    ),
    CONSTRAINT ck_affiliate_outbox_delivery_time CHECK (
        delivery_status <> 'DELIVERED' OR delivered_at IS NOT NULL
    )
);

CREATE INDEX IF NOT EXISTS ix_affiliate_outbox_claim
    ON affiliate_event_outbox (available_at, occurred_at, outbox_id)
    WHERE delivery_status IN ('PENDING', 'IN_FLIGHT');

CREATE INDEX IF NOT EXISTS ix_affiliate_outbox_tenant_aggregate
    ON affiliate_event_outbox (tenant_id, aggregate_type, aggregate_id, occurred_at);
