-- Durable, version-fenced lifecycle controls for historical graph projection rebuilds.
ALTER TABLE historical_relationship_projection_rebuild_run
    ADD COLUMN lifecycle_version BIGINT NOT NULL DEFAULT 0 CHECK (lifecycle_version >= 0),
    ADD COLUMN failure_detail VARCHAR(1000) NOT NULL DEFAULT '',
    ADD COLUMN paused_at TIMESTAMPTZ NULL,
    ADD COLUMN cancelled_at TIMESTAMPTZ NULL,
    ADD COLUMN failed_at TIMESTAMPTZ NULL;

DO $$
DECLARE constraint_row RECORD;
BEGIN
    FOR constraint_row IN
        SELECT conname, pg_get_constraintdef(oid) AS definition
          FROM pg_constraint
         WHERE conrelid = 'historical_relationship_projection_rebuild_run'::regclass
           AND contype = 'c'
    LOOP
        IF constraint_row.definition ILIKE '%status%' THEN
            EXECUTE format('ALTER TABLE historical_relationship_projection_rebuild_run DROP CONSTRAINT %I',
                           constraint_row.conname);
        ELSIF constraint_row.definition ILIKE '%completed_at%' THEN
            EXECUTE format('ALTER TABLE historical_relationship_projection_rebuild_run DROP CONSTRAINT %I',
                           constraint_row.conname);
        END IF;
    END LOOP;
END $$;

ALTER TABLE historical_relationship_projection_rebuild_run
    ADD CONSTRAINT historical_projection_rebuild_status_check
        CHECK (status IN ('RUNNING','PAUSED','CANCELLED','FAILED','COMPLETED')),
    ADD CONSTRAINT historical_projection_rebuild_terminal_timestamp_check
        CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)),
    ADD CONSTRAINT historical_projection_rebuild_failure_detail_check
        CHECK (status = 'FAILED' OR failure_detail = '');

ALTER TABLE historical_relationship_projection_rebuild_run ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_relationship_projection_rebuild_run FORCE ROW LEVEL SECURITY;
CREATE POLICY historical_projection_rebuild_run_scope
    ON historical_relationship_projection_rebuild_run FOR ALL
    USING (tenant_id IS NULL OR tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id IS NULL OR tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid);

CREATE TABLE historical_projection_rebuild_audit (
    audit_id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    tenant_id UUID NULL,
    actor_id UUID NOT NULL,
    action VARCHAR(16) NOT NULL CHECK (action IN ('PAUSE','RESUME','CANCEL','RETRY','FAIL')),
    previous_status VARCHAR(16) NOT NULL CHECK (previous_status IN ('RUNNING','PAUSED','CANCELLED','FAILED','COMPLETED')),
    resulting_status VARCHAR(16) NOT NULL CHECK (resulting_status IN ('RUNNING','PAUSED','CANCELLED','FAILED','COMPLETED')),
    lifecycle_version BIGINT NOT NULL CHECK (lifecycle_version >= 1),
    detail VARCHAR(1000) NOT NULL DEFAULT '',
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX historical_projection_rebuild_audit_run_idx
    ON historical_projection_rebuild_audit (run_id, occurred_at, audit_id);
CREATE INDEX historical_projection_rebuild_audit_scope_idx
    ON historical_projection_rebuild_audit (tenant_id, occurred_at DESC);

ALTER TABLE historical_projection_rebuild_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_projection_rebuild_audit FORCE ROW LEVEL SECURITY;
CREATE POLICY historical_projection_rebuild_audit_scope
    ON historical_projection_rebuild_audit FOR ALL
    USING (tenant_id IS NULL OR tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id IS NULL OR tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid);

CREATE FUNCTION reject_historical_projection_rebuild_audit_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'historical projection rebuild audit is append-only';
END;
$$;
CREATE TRIGGER historical_projection_rebuild_audit_immutable
    BEFORE UPDATE OR DELETE ON historical_projection_rebuild_audit
    FOR EACH ROW EXECUTE FUNCTION reject_historical_projection_rebuild_audit_mutation();
CREATE TRIGGER historical_projection_rebuild_audit_no_truncate
    BEFORE TRUNCATE ON historical_projection_rebuild_audit
    FOR EACH STATEMENT EXECUTE FUNCTION reject_historical_projection_rebuild_audit_mutation();
