-- Durable reservation persistence primitives.
-- All participating writes must execute through PgReservationTransactionRunner's connection.
-- Snapshot JSON keeps domain restoration explicit while indexed columns support tenant-scoped queries.
CREATE TABLE IF NOT EXISTS reservation_record (
    tenant_id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    facility_id UUID NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    aggregate_version INTEGER NOT NULL CHECK (aggregate_version >= 0),
    snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, reservation_id),
    CHECK (ends_at > starts_at)
);

CREATE INDEX IF NOT EXISTS ix_reservation_record_facility_window
    ON reservation_record (tenant_id, facility_id, starts_at, ends_at);

CREATE TABLE IF NOT EXISTS reservation_availability_ledger (
    tenant_id UUID NOT NULL,
    ledger_id UUID NOT NULL,
    facility_id UUID NOT NULL,
    ledger_day DATE NOT NULL,
    aggregate_version INTEGER NOT NULL CHECK (aggregate_version >= 0),
    snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, ledger_id),
    UNIQUE (tenant_id, facility_id, ledger_day)
);

CREATE TABLE IF NOT EXISTS reservation_idempotency_key (
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    reservation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, idempotency_key),
    CONSTRAINT fk_reservation_idempotency_record
        FOREIGN KEY (tenant_id, reservation_id)
        REFERENCES reservation_record (tenant_id, reservation_id)
        DEFERRABLE INITIALLY DEFERRED,
    CHECK (length(btrim(idempotency_key)) > 0),
    CHECK (request_fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE TABLE IF NOT EXISTS reservation_outbox_event (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'DEAD')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at TIMESTAMPTZ NOT NULL,
    lease_owner VARCHAR(200),
    lease_until TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error TEXT,
    CHECK ((status = 'PROCESSING' AND lease_owner IS NOT NULL AND lease_until IS NOT NULL)
        OR status <> 'PROCESSING')
);

CREATE INDEX IF NOT EXISTS ix_reservation_outbox_pending
    ON reservation_outbox_event (available_at, occurred_at)
    WHERE status IN ('PENDING', 'PROCESSING');
