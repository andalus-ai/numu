CREATE TABLE IF NOT EXISTS schedule_plan_scope (
    plan_id UUID PRIMARY KEY REFERENCES schedule_plan(plan_id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    structure_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_schedule_plan_scope UNIQUE (tenant_id, structure_id, plan_id)
);
CREATE TABLE IF NOT EXISTS schedule_change_execution (
    execution_id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    structure_id UUID NOT NULL,
    plan_id UUID NOT NULL REFERENCES schedule_plan(plan_id) ON DELETE RESTRICT,
    base_revision BIGINT NOT NULL CHECK (base_revision >= 0),
    target_revision BIGINT NOT NULL CHECK (target_revision > base_revision),
    initiated_by UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('APPLIED', 'FAILED', 'CONFLICT')),
    resulting_revision BIGINT,
    completed_at TIMESTAMPTZ NOT NULL,
    failure_reason VARCHAR(1000),
    payload_fingerprint CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_schedule_change_execution_terminal CHECK (
      (status = 'APPLIED' AND resulting_revision = target_revision AND failure_reason IS NULL)
      OR (status IN ('FAILED','CONFLICT') AND resulting_revision IS NULL
          AND failure_reason IS NOT NULL AND length(trim(failure_reason)) > 0)
    )
);
CREATE INDEX IF NOT EXISTS ix_schedule_change_execution_plan
 ON schedule_change_execution(tenant_id, structure_id, plan_id, completed_at DESC);
