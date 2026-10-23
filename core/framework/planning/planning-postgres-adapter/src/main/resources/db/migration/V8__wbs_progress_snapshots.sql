-- Snapshots and their calculation inputs are immutable historical evidence.
CREATE TABLE IF NOT EXISTS work_breakdown_progress_snapshot (
    tenant_id UUID NOT NULL,
    snapshot_id UUID NOT NULL,
    structure_id UUID NOT NULL,
    node_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    plan_revision BIGINT NOT NULL CHECK (plan_revision >= 0),
    as_of_date DATE NOT NULL,
    weighting_policy_version VARCHAR(120) NOT NULL CHECK (length(trim(weighting_policy_version)) > 0),
    completion_percent NUMERIC(5,2) NOT NULL CHECK (completion_percent >= 0 AND completion_percent <= 100),
    total_weight NUMERIC(24,8) NOT NULL CHECK (total_weight > 0),
    included_activity_count INTEGER NOT NULL CHECK (included_activity_count > 0),
    recorded_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, snapshot_id),
    CONSTRAINT fk_progress_snapshot_wbs_node
        FOREIGN KEY (tenant_id, structure_id, node_id)
        REFERENCES work_breakdown_node (tenant_id, structure_id, node_id),
    CONSTRAINT fk_progress_snapshot_plan
        FOREIGN KEY (plan_id) REFERENCES schedule_plan (plan_id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS work_breakdown_progress_snapshot_activity (
    tenant_id UUID NOT NULL,
    snapshot_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    activity_id UUID NOT NULL,
    actual_percent NUMERIC(5,2) NOT NULL CHECK (actual_percent >= 0 AND actual_percent <= 100),
    weight NUMERIC(24,8) NOT NULL CHECK (weight > 0),
    PRIMARY KEY (tenant_id, snapshot_id, activity_id),
    CONSTRAINT fk_progress_snapshot_activity_snapshot
        FOREIGN KEY (tenant_id, snapshot_id)
        REFERENCES work_breakdown_progress_snapshot (tenant_id, snapshot_id),
    CONSTRAINT fk_progress_snapshot_activity_schedule
        FOREIGN KEY (plan_id, activity_id)
        REFERENCES schedule_activity (plan_id, activity_id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_progress_snapshot_history
    ON work_breakdown_progress_snapshot (tenant_id, structure_id, node_id, as_of_date DESC, recorded_at DESC);

CREATE OR REPLACE FUNCTION reject_wbs_progress_snapshot_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'WBS progress snapshots are append-only; UPDATE/DELETE is forbidden';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_wbs_progress_snapshot_immutable ON work_breakdown_progress_snapshot;
CREATE TRIGGER trg_wbs_progress_snapshot_immutable
    BEFORE UPDATE OR DELETE ON work_breakdown_progress_snapshot
    FOR EACH ROW EXECUTE FUNCTION reject_wbs_progress_snapshot_mutation();

DROP TRIGGER IF EXISTS trg_wbs_progress_snapshot_activity_immutable ON work_breakdown_progress_snapshot_activity;
CREATE TRIGGER trg_wbs_progress_snapshot_activity_immutable
    BEFORE UPDATE OR DELETE ON work_breakdown_progress_snapshot_activity
    FOR EACH ROW EXECUTE FUNCTION reject_wbs_progress_snapshot_mutation();
