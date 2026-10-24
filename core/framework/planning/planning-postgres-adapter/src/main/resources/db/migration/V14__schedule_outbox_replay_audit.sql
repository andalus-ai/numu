-- Auditable, operator-initiated recovery for exhausted schedule-change events.
CREATE TABLE IF NOT EXISTS schedule_change_outbox_replay_audit (
    replay_id UUID PRIMARY KEY,
    outbox_id UUID NOT NULL REFERENCES schedule_change_outbox(outbox_id) ON DELETE RESTRICT,
    replayed_at TIMESTAMPTZ NOT NULL,
    replayed_by VARCHAR(200) NOT NULL CHECK (length(trim(replayed_by)) > 0),
    reason VARCHAR(500) NOT NULL CHECK (length(trim(reason)) > 0),
    previous_attempt_count INTEGER NOT NULL CHECK (previous_attempt_count >= 10)
);

CREATE INDEX IF NOT EXISTS ix_schedule_change_outbox_replay_audit_event
    ON schedule_change_outbox_replay_audit(outbox_id, replayed_at DESC);
