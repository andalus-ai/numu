-- Preserve operational replay history independently from mutable delivery state.
ALTER TABLE pos_outbox_event
    ADD COLUMN IF NOT EXISTS replay_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_replayed_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS pos_outbox_replay_audit (
    replay_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    event_id UUID NOT NULL REFERENCES pos_outbox_event(event_id),
    actor VARCHAR(500) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    replayed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_pos_outbox_replay_actor_nonblank CHECK (length(trim(actor)) > 0),
    CONSTRAINT ck_pos_outbox_replay_reason_nonblank CHECK (length(trim(reason)) > 0)
);

CREATE INDEX IF NOT EXISTS ix_pos_outbox_replay_audit_tenant_event
    ON pos_outbox_replay_audit (tenant_id, event_id, replayed_at DESC);
