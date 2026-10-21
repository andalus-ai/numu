-- Actual progress is append-only evidence, separate from mutable schedule forecasts.
CREATE TABLE IF NOT EXISTS schedule_activity_progress_observation (
    plan_id UUID NOT NULL,
    observation_id UUID NOT NULL,
    activity_id UUID NOT NULL,
    as_of_date DATE NOT NULL,
    percent_complete NUMERIC(5,2) NOT NULL CHECK (percent_complete >= 0 AND percent_complete <= 100),
    recorded_at TIMESTAMPTZ NOT NULL,
    recorded_by VARCHAR(240) NOT NULL,
    idempotency_key VARCHAR(240) NOT NULL,
    source_reference VARCHAR(500),
    PRIMARY KEY (plan_id, observation_id),
    CONSTRAINT fk_progress_observation_activity
        FOREIGN KEY (plan_id, activity_id)
        REFERENCES schedule_activity(plan_id, activity_id) ON DELETE RESTRICT,
    CONSTRAINT uq_progress_observation_idempotency UNIQUE (plan_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS ix_progress_observation_activity_date
    ON schedule_activity_progress_observation(plan_id, activity_id, as_of_date DESC, recorded_at DESC);

CREATE OR REPLACE FUNCTION reject_schedule_progress_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Progress observations are append-only; UPDATE/DELETE is forbidden';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_schedule_progress_immutable ON schedule_activity_progress_observation;
CREATE TRIGGER trg_schedule_progress_immutable
    BEFORE UPDATE OR DELETE ON schedule_activity_progress_observation
    FOR EACH ROW EXECUTE FUNCTION reject_schedule_progress_mutation();
