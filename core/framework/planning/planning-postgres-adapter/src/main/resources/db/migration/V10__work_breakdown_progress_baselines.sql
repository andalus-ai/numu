-- Named baselines point to approved immutable snapshot evidence; the source snapshot is never copied or mutated.
-- Composite uniqueness lets the database enforce that baseline scope matches the referenced snapshot.
ALTER TABLE work_breakdown_progress_snapshot
    ADD CONSTRAINT uq_progress_snapshot_scope UNIQUE (tenant_id, snapshot_id, structure_id, node_id);
CREATE TABLE IF NOT EXISTS work_breakdown_progress_baseline (
    tenant_id UUID NOT NULL,
    baseline_id UUID NOT NULL,
    structure_id UUID NOT NULL,
    node_id UUID NOT NULL,
    source_snapshot_id UUID NOT NULL,
    baseline_code VARCHAR(100) NOT NULL,
    name VARCHAR(250) NOT NULL,
    effective_date DATE NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, baseline_id),
    CONSTRAINT uq_progress_baseline_code UNIQUE (tenant_id, baseline_code),
    CONSTRAINT fk_progress_baseline_snapshot
        FOREIGN KEY (tenant_id, source_snapshot_id, structure_id, node_id)
        REFERENCES work_breakdown_progress_snapshot (tenant_id, snapshot_id, structure_id, node_id) ON DELETE RESTRICT,
    CONSTRAINT ck_progress_baseline_code CHECK (length(trim(baseline_code)) > 0),
    CONSTRAINT ck_progress_baseline_name CHECK (length(trim(name)) > 0)
);

CREATE INDEX IF NOT EXISTS ix_progress_baseline_scope
    ON work_breakdown_progress_baseline (tenant_id, structure_id, node_id, effective_date DESC, created_at DESC);

CREATE OR REPLACE FUNCTION reject_progress_baseline_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Progress baselines are immutable; UPDATE/DELETE is forbidden';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_progress_baseline_immutable ON work_breakdown_progress_baseline;
CREATE TRIGGER trg_progress_baseline_immutable
    BEFORE UPDATE OR DELETE ON work_breakdown_progress_baseline
    FOR EACH ROW EXECUTE FUNCTION reject_progress_baseline_mutation();
