-- V3__transactional_outbox.sql
SET search_path TO accounting, public;

CREATE TABLE IF NOT EXISTS outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(256) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    ledger_id VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_outbox_unprocessed ON outbox_event (processed_at, created_at) WHERE processed_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_outbox_tenant ON outbox_event (tenant_id, aggregate_type);
