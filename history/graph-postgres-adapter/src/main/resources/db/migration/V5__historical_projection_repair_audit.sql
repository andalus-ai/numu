-- Append-only operator audit for graph reconciliation and repair operations.
CREATE TABLE historical_projection_repair_audit (
    audit_id UUID PRIMARY KEY,
    operation_id UUID NOT NULL,
    tenant_id UUID NULL,
    actor_id UUID NOT NULL,
    phase VARCHAR(16) NOT NULL CHECK (phase IN ('STARTED','COMPLETED','FAILED')),
    page_size INTEGER NOT NULL CHECK (page_size BETWEEN 1 AND 200),
    canonical_checked BIGINT NOT NULL DEFAULT 0 CHECK (canonical_checked >= 0),
    missing BIGINT NOT NULL DEFAULT 0 CHECK (missing >= 0),
    stale BIGINT NOT NULL DEFAULT 0 CHECK (stale >= 0),
    divergent BIGINT NOT NULL DEFAULT 0 CHECK (divergent >= 0),
    orphaned BIGINT NOT NULL DEFAULT 0 CHECK (orphaned >= 0),
    repaired BIGINT NOT NULL DEFAULT 0 CHECK (repaired >= 0),
    detail VARCHAR(1000) NOT NULL DEFAULT '',
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX historical_projection_repair_audit_operation_idx
    ON historical_projection_repair_audit (operation_id, occurred_at, audit_id);
CREATE INDEX historical_projection_repair_audit_scope_idx
    ON historical_projection_repair_audit (tenant_id, occurred_at DESC);

ALTER TABLE historical_projection_repair_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_projection_repair_audit FORCE ROW LEVEL SECURITY;
CREATE POLICY historical_projection_repair_audit_scope
    ON historical_projection_repair_audit FOR ALL
    USING (tenant_id IS NULL OR tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id IS NULL OR tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid);

CREATE FUNCTION reject_historical_projection_repair_audit_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'historical projection repair audit is append-only';
END;
$$;

CREATE TRIGGER historical_projection_repair_audit_immutable
    BEFORE UPDATE OR DELETE ON historical_projection_repair_audit
    FOR EACH ROW EXECUTE FUNCTION reject_historical_projection_repair_audit_mutation();
CREATE TRIGGER historical_projection_repair_audit_no_truncate
    BEFORE TRUNCATE ON historical_projection_repair_audit
    FOR EACH STATEMENT EXECUTE FUNCTION reject_historical_projection_repair_audit_mutation();
