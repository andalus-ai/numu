CREATE TABLE IF NOT EXISTS numou_omnichannel_dead_letter_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL,
    provider_id VARCHAR(100) NOT NULL,
    provider_account_id VARCHAR(200) NOT NULL,
    external_event_id VARCHAR(250) NOT NULL,
    actor_id VARCHAR(200) NOT NULL,
    action VARCHAR(40) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    attempt_count INTEGER NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_numou_omnichannel_dead_letter_audit_action
        CHECK (action IN ('REPLAY_REQUESTED_AND_QUEUED'))
);

CREATE INDEX IF NOT EXISTS ix_numou_omnichannel_dead_letter_audit_event
    ON numou_omnichannel_dead_letter_audit
       (tenant_id, provider_id, provider_account_id, external_event_id, requested_at DESC);
