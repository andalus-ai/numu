-- Durable orchestration journal for staged graph-generation rebuilds.
-- The Neo4j generation pointer and this PostgreSQL journal are separate transactions;
-- statuses are intentionally resumable so an interrupted cutover can be reconciled.
CREATE TABLE historical_projection_generation_run (
    scope_key VARCHAR(64) NOT NULL,
    operation_id UUID NOT NULL,
    tenant_id UUID NULL,
    generation_id UUID NOT NULL,
    rebuild_run_id UUID NOT NULL,
    expected_active_generation_id UUID NULL,
    status VARCHAR(16) NOT NULL CHECK (
        status IN ('PREPARING','REPLAYING','VALIDATING','READY','ACTIVATING','ACTIVE')
    ),
    failure_count INTEGER NOT NULL DEFAULT 0 CHECK (failure_count >= 0),
    last_error VARCHAR(1000) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ NULL,
    PRIMARY KEY (scope_key, operation_id),
    UNIQUE (scope_key, generation_id),
    UNIQUE (scope_key, rebuild_run_id),
    CHECK (scope_key = COALESCE(tenant_id::text, 'public')),
    CHECK (updated_at >= created_at),
    CHECK ((status = 'ACTIVE') = (activated_at IS NOT NULL))
);

CREATE INDEX historical_projection_generation_run_scope_status_idx
    ON historical_projection_generation_run (tenant_id, status, created_at);

ALTER TABLE historical_projection_generation_run ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_projection_generation_run FORCE ROW LEVEL SECURITY;
CREATE POLICY historical_projection_generation_run_scope
    ON historical_projection_generation_run FOR ALL
    USING (tenant_id IS NULL OR tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id IS NULL OR tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid);

COMMENT ON TABLE historical_projection_generation_run IS
    'Resumable orchestration journal linking a staged Neo4j generation to one immutable PostgreSQL rebuild run.';
