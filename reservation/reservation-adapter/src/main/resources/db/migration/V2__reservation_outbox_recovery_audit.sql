-- Durable, tenant-scoped audit trail for operator-initiated outbox recovery.
-- Requeue and audit insertion must run in the same PostgreSQL transaction.
-- The event_id is deliberately retained: consumers continue deduplicating on the
-- original event identity after a controlled replay.

CREATE TABLE IF NOT EXISTS reservation_outbox_recovery_audit (
    audit_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    event_id UUID NOT NULL
        REFERENCES reservation_outbox_event (event_id),
    request_id UUID NOT NULL,
    actor_id VARCHAR(200) NOT NULL,
    action VARCHAR(32) NOT NULL
        CHECK (action IN ('REQUEUE')),
    reason VARCHAR(2000) NOT NULL,
    previous_status VARCHAR(20) NOT NULL
        CHECK (previous_status = 'DEAD'),
    new_status VARCHAR(20) NOT NULL
        CHECK (new_status = 'PENDING'),
    previous_attempts INTEGER NOT NULL
        CHECK (previous_attempts >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_reservation_outbox_recovery_request
        UNIQUE (tenant_id, event_id, request_id),
    CHECK (length(btrim(actor_id)) > 0),
    CHECK (length(btrim(reason)) > 0)
);

CREATE INDEX IF NOT EXISTS ix_reservation_outbox_recovery_audit_event
    ON reservation_outbox_recovery_audit (tenant_id, event_id, created_at DESC);

COMMENT ON TABLE reservation_outbox_recovery_audit IS
    'Append-only audit evidence for authorized operator recovery of dead-letter events.';
COMMENT ON COLUMN reservation_outbox_recovery_audit.event_id IS
    'Original outbox event identity; replay never creates a replacement event ID.';
COMMENT ON COLUMN reservation_outbox_recovery_audit.request_id IS
    'Caller-provided idempotency key for one operator recovery command.';
