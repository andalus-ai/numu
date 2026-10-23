CREATE TABLE IF NOT EXISTS schedule_change_outbox (
    outbox_id UUID PRIMARY KEY,
    execution_id UUID NOT NULL REFERENCES schedule_change_execution(execution_id) ON DELETE RESTRICT,
    event_type VARCHAR(64) NOT NULL CHECK (event_type IN ('ScheduleChangeApplied', 'ScheduleChangeConflicted')),
    tenant_id UUID NOT NULL,
    structure_id UUID NOT NULL,
    plan_id UUID NOT NULL REFERENCES schedule_plan(plan_id) ON DELETE RESTRICT,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    last_error VARCHAR(2000),
    claim_token UUID,
    claimed_until TIMESTAMPTZ,
    CONSTRAINT uq_schedule_change_outbox_execution_event UNIQUE (execution_id, event_type),
    CONSTRAINT ck_schedule_change_outbox_claim CHECK ((claim_token IS NULL) = (claimed_until IS NULL)),
    CONSTRAINT ck_schedule_change_outbox_published_unclaimed CHECK (published_at IS NULL OR claim_token IS NULL)
);

CREATE INDEX IF NOT EXISTS ix_schedule_change_outbox_pending
    ON schedule_change_outbox(available_at, created_at)
    WHERE published_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_schedule_change_outbox_expired_claim
    ON schedule_change_outbox(claimed_until)
    WHERE published_at IS NULL AND claim_token IS NOT NULL;
