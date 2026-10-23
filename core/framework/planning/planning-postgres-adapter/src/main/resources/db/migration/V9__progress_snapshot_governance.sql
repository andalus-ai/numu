-- Review decisions are separate append-only events; they never rewrite snapshots.
CREATE TABLE IF NOT EXISTS work_breakdown_progress_snapshot_decision (
    tenant_id UUID NOT NULL,
    decision_id UUID NOT NULL,
    snapshot_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    outcome VARCHAR(16) NOT NULL CHECK (outcome IN ('APPROVED', 'REJECTED')),
    reason VARCHAR(2000) NOT NULL CHECK (length(trim(reason)) > 0),
    decided_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, decision_id),
    CONSTRAINT fk_progress_decision_snapshot
        FOREIGN KEY (tenant_id, snapshot_id)
        REFERENCES work_breakdown_progress_snapshot (tenant_id, snapshot_id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_progress_decision_history
    ON work_breakdown_progress_snapshot_decision (tenant_id, snapshot_id, decided_at DESC, decision_id DESC);

CREATE OR REPLACE FUNCTION reject_progress_snapshot_decision_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Progress snapshot decisions are append-only; UPDATE/DELETE is forbidden';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_progress_snapshot_decision_immutable ON work_breakdown_progress_snapshot_decision;
CREATE TRIGGER trg_progress_snapshot_decision_immutable
    BEFORE UPDATE OR DELETE ON work_breakdown_progress_snapshot_decision
    FOR EACH ROW EXECUTE FUNCTION reject_progress_snapshot_decision_mutation();
