CREATE TABLE IF NOT EXISTS numou_event_dead_letter_replay (
    dead_letter_record_id VARCHAR(500) PRIMARY KEY,
    tenant_id UUID NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (state IN ('PENDING', 'IN_PROGRESS', 'FAILED', 'COMPLETED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    actor_id VARCHAR(200),
    replay_reason VARCHAR(1000),
    requested_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failure_code VARCHAR(120),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_numou_event_dead_letter_lease
        CHECK ((state = 'IN_PROGRESS' AND claim_token IS NOT NULL AND lease_until IS NOT NULL)
            OR (state <> 'IN_PROGRESS' AND claim_token IS NULL AND lease_until IS NULL))
);
CREATE INDEX IF NOT EXISTS ix_numou_event_dead_letter_replay_lease
    ON numou_event_dead_letter_replay (lease_until) WHERE state = 'IN_PROGRESS';
CREATE INDEX IF NOT EXISTS ix_numou_event_dead_letter_replay_tenant_state
    ON numou_event_dead_letter_replay (tenant_id, state, updated_at DESC);
CREATE TABLE IF NOT EXISTS numou_event_dead_letter_replay_audit (
    audit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dead_letter_record_id VARCHAR(500) NOT NULL
        REFERENCES numou_event_dead_letter_replay (dead_letter_record_id),
    tenant_id UUID NOT NULL,
    claim_token UUID,
    actor_id VARCHAR(200) NOT NULL,
    action VARCHAR(40) NOT NULL
        CHECK (action IN ('CLAIMED', 'IN_PROGRESS', 'ALREADY_COMPLETED', 'COMPLETED', 'FAILED')),
    detail VARCHAR(1000),
    attempt_count INTEGER NOT NULL CHECK (attempt_count >= 0),
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_numou_event_dead_letter_replay_audit_tenant
    ON numou_event_dead_letter_replay_audit (tenant_id, occurred_at DESC);

CREATE OR REPLACE FUNCTION numou_event_dead_letter_audit_immutable()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'numou_event_dead_letter_replay_audit is append-only';
END;
$$;
CREATE TRIGGER trg_numou_event_dead_letter_audit_no_update_delete
    BEFORE UPDATE OR DELETE ON numou_event_dead_letter_replay_audit
    FOR EACH ROW EXECUTE FUNCTION numou_event_dead_letter_audit_immutable();
CREATE TRIGGER trg_numou_event_dead_letter_audit_no_truncate
    BEFORE TRUNCATE ON numou_event_dead_letter_replay_audit
    FOR EACH STATEMENT EXECUTE FUNCTION numou_event_dead_letter_audit_immutable();
