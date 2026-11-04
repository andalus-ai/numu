CREATE TABLE sg_connection (
    tenant_id UUID NOT NULL,
    connection_id UUID NOT NULL,
    requester_party_id UUID NOT NULL,
    recipient_party_id UUID NOT NULL,
    pair_low UUID NOT NULL,
    pair_high UUID NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'CANCELLED', 'REMOVED')),
    requested_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    version INTEGER NOT NULL CHECK (version >= 0),
    PRIMARY KEY (tenant_id, connection_id),
    CHECK (requester_party_id <> recipient_party_id),
    CHECK (pair_low < pair_high),
    CHECK (updated_at >= requested_at),
    CHECK ((status = 'PENDING' AND resolved_at IS NULL) OR
           (status <> 'PENDING' AND resolved_at IS NOT NULL))
);

-- At most one pending or active relationship per unordered Party pair in a tenant.
CREATE UNIQUE INDEX ux_sg_connection_open_pair
    ON sg_connection (tenant_id, pair_low, pair_high)
    WHERE status IN ('PENDING', 'ACTIVE');

CREATE INDEX ix_sg_connection_requester
    ON sg_connection (tenant_id, requester_party_id, status, updated_at DESC);

CREATE INDEX ix_sg_connection_recipient
    ON sg_connection (tenant_id, recipient_party_id, status, updated_at DESC);

CREATE TABLE sg_connection_audit (
    audit_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    connection_id UUID NOT NULL,
    actor_party_id UUID NOT NULL,
    from_status VARCHAR(16) CHECK (from_status IS NULL OR from_status IN ('PENDING', 'ACTIVE', 'REJECTED', 'CANCELLED', 'REMOVED')),
    to_status VARCHAR(16) NOT NULL CHECK (to_status IN ('PENDING', 'ACTIVE', 'REJECTED', 'CANCELLED', 'REMOVED')),
    action VARCHAR(24) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_sg_audit_connection
        FOREIGN KEY (tenant_id, connection_id)
        REFERENCES sg_connection (tenant_id, connection_id)
);

CREATE INDEX ix_sg_connection_audit_history
    ON sg_connection_audit (tenant_id, connection_id, occurred_at, audit_id);

-- Audit is append-only even if a future adapter accidentally exposes mutation.
CREATE FUNCTION sg_reject_connection_audit_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $
BEGIN
    RAISE EXCEPTION 'social graph connection audit is append-only';
END;
$;

CREATE TRIGGER trg_sg_connection_audit_immutable
    BEFORE UPDATE OR DELETE ON sg_connection_audit
    FOR EACH ROW EXECUTE FUNCTION sg_reject_connection_audit_mutation();

CREATE TABLE sg_command_idempotency (
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_fingerprint VARCHAR(256) NOT NULL,
    connection_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, idempotency_key),
    CONSTRAINT fk_sg_idempotency_connection
        FOREIGN KEY (tenant_id, connection_id)
        REFERENCES sg_connection (tenant_id, connection_id)
);

CREATE TABLE sg_outbox (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    actor_party_id UUID NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_version INTEGER NOT NULL CHECK (aggregate_version >= 0),
    event_type VARCHAR(160) NOT NULL,
    schema_version INTEGER NOT NULL CHECK (schema_version >= 1),
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    published_at TIMESTAMPTZ,
    lease_token UUID,
    leased_until TIMESTAMPTZ,
    dead_lettered_at TIMESTAMPTZ,
    CONSTRAINT ux_sg_outbox_aggregate_event
        UNIQUE (tenant_id, aggregate_id, aggregate_version, event_type)
);

CREATE INDEX ix_sg_outbox_due
    ON sg_outbox (available_at, created_at)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;
