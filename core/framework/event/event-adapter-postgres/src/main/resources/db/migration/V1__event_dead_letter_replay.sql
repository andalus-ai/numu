CREATE TABLE IF NOT EXISTS numou_event_dead_letter_replay (
    tenant_id UUID NOT NULL,
    dead_letter_record_id VARCHAR(300) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (state IN ('PENDING', 'CLAIMED', 'FAILED', 'COMPLETED')),
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    attempt INTEGER NOT NULL DEFAULT 0 CHECK (attempt >= 0),
    requested_by VARCHAR(200),
    replay_reason VARCHAR(1000),
    requested_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_failure_code VARCHAR(120),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, dead_letter_record_id),
    CHECK ((state = 'CLAIMED' AND claim_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (state <> 'CLAIMED' AND claim_token IS NULL AND lease_until IS NULL))
);
CREATE INDEX IF NOT EXISTS ix_event_dlq_replay_lease
    ON numou_event_dead_letter_replay (lease_until) WHERE state = 'CLAIMED';
CREATE INDEX IF NOT EXISTS ix_event_dlq_replay_tenant_state
    ON numou_event_dead_letter_replay (tenant_id, state, updated_at DESC);

CREATE TABLE IF NOT EXISTS numou_event_dead_letter_replay_audit (
    audit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id UUID NOT NULL,
    dead_letter_record_id VARCHAR(300) NOT NULL,
    claim_token UUID NOT NULL,
    attempt INTEGER NOT NULL,
    actor_id VARCHAR(200) NOT NULL,
    action VARCHAR(20) NOT NULL CHECK (action IN ('CLAIMED', 'FAILED', 'COMPLETED')),
    reason VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (tenant_id, dead_letter_record_id)
        REFERENCES numou_event_dead_letter_replay (tenant_id, dead_letter_record_id)
);
CREATE INDEX IF NOT EXISTS ix_event_dlq_replay_audit_tenant_time
    ON numou_event_dead_letter_replay_audit (tenant_id, occurred_at DESC);
