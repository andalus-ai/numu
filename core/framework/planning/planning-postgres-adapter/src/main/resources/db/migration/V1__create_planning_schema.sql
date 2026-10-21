CREATE TABLE IF NOT EXISTS schedule_plan (
    plan_id UUID PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(300) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS schedule_activity (
    plan_id UUID NOT NULL REFERENCES schedule_plan(plan_id) ON DELETE CASCADE,
    activity_id UUID NOT NULL,
    code VARCHAR(120) NOT NULL,
    name VARCHAR(500) NOT NULL,
    planned_start DATE NOT NULL,
    planned_finish DATE NOT NULL,
    work_item_type VARCHAR(120),
    work_item_id VARCHAR(240),
    position INTEGER NOT NULL CHECK (position >= 0),
    PRIMARY KEY (plan_id, activity_id),
    CONSTRAINT uq_schedule_activity_code UNIQUE (plan_id, code),
    CONSTRAINT ck_schedule_activity_dates CHECK (planned_finish >= planned_start),
    CONSTRAINT ck_schedule_work_item_pair CHECK ((work_item_type IS NULL) = (work_item_id IS NULL))
);

CREATE TABLE IF NOT EXISTS schedule_dependency (
    plan_id UUID NOT NULL REFERENCES schedule_plan(plan_id) ON DELETE CASCADE,
    predecessor_id UUID NOT NULL,
    successor_id UUID NOT NULL,
    PRIMARY KEY (plan_id, predecessor_id, successor_id),
    CONSTRAINT ck_schedule_dependency_not_self CHECK (predecessor_id <> successor_id),
    CONSTRAINT fk_schedule_dependency_predecessor FOREIGN KEY (plan_id, predecessor_id)
        REFERENCES schedule_activity(plan_id, activity_id) ON DELETE CASCADE,
    CONSTRAINT fk_schedule_dependency_successor FOREIGN KEY (plan_id, successor_id)
        REFERENCES schedule_activity(plan_id, activity_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS schedule_baseline (
    plan_id UUID NOT NULL REFERENCES schedule_plan(plan_id) ON DELETE CASCADE,
    baseline_id UUID NOT NULL,
    sequence INTEGER NOT NULL CHECK (sequence > 0),
    name VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(240) NOT NULL,
    PRIMARY KEY (plan_id, baseline_id),
    CONSTRAINT uq_schedule_baseline_sequence UNIQUE (plan_id, sequence)
);

CREATE TABLE IF NOT EXISTS schedule_baseline_activity (
    plan_id UUID NOT NULL,
    baseline_id UUID NOT NULL,
    activity_id UUID NOT NULL,
    code VARCHAR(120) NOT NULL,
    name VARCHAR(500) NOT NULL,
    planned_start DATE NOT NULL,
    planned_finish DATE NOT NULL,
    work_item_type VARCHAR(120),
    work_item_id VARCHAR(240),
    position INTEGER NOT NULL CHECK (position >= 0),
    PRIMARY KEY (plan_id, baseline_id, activity_id),
    FOREIGN KEY (plan_id, baseline_id) REFERENCES schedule_baseline(plan_id, baseline_id),
    CONSTRAINT ck_baseline_work_item_pair CHECK ((work_item_type IS NULL) = (work_item_id IS NULL)),
    CONSTRAINT ck_baseline_activity_dates CHECK (planned_finish >= planned_start)
);

CREATE TABLE IF NOT EXISTS schedule_baseline_dependency (
    plan_id UUID NOT NULL,
    baseline_id UUID NOT NULL,
    predecessor_id UUID NOT NULL,
    successor_id UUID NOT NULL,
    PRIMARY KEY (plan_id, baseline_id, predecessor_id, successor_id),
    FOREIGN KEY (plan_id, baseline_id) REFERENCES schedule_baseline(plan_id, baseline_id),
    FOREIGN KEY (plan_id, baseline_id, predecessor_id)
        REFERENCES schedule_baseline_activity(plan_id, baseline_id, activity_id),
    FOREIGN KEY (plan_id, baseline_id, successor_id)
        REFERENCES schedule_baseline_activity(plan_id, baseline_id, activity_id),
    CONSTRAINT ck_baseline_dependency_not_self CHECK (predecessor_id <> successor_id)
);

-- Baselines are historical evidence. Enforce append-only behavior in PostgreSQL,
-- not merely in application code.
CREATE OR REPLACE FUNCTION reject_schedule_baseline_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Schedule baselines are append-only; UPDATE/DELETE is forbidden';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_schedule_baseline_immutable ON schedule_baseline;
CREATE TRIGGER trg_schedule_baseline_immutable
    BEFORE UPDATE OR DELETE ON schedule_baseline
    FOR EACH ROW EXECUTE FUNCTION reject_schedule_baseline_mutation();

DROP TRIGGER IF EXISTS trg_schedule_baseline_activity_immutable ON schedule_baseline_activity;
CREATE TRIGGER trg_schedule_baseline_activity_immutable
    BEFORE UPDATE OR DELETE ON schedule_baseline_activity
    FOR EACH ROW EXECUTE FUNCTION reject_schedule_baseline_mutation();

DROP TRIGGER IF EXISTS trg_schedule_baseline_dependency_immutable ON schedule_baseline_dependency;
CREATE TRIGGER trg_schedule_baseline_dependency_immutable
    BEFORE UPDATE OR DELETE ON schedule_baseline_dependency
    FOR EACH ROW EXECUTE FUNCTION reject_schedule_baseline_mutation();
