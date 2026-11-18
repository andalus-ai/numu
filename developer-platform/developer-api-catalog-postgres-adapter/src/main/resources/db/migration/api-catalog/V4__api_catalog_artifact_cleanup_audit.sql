-- M5j: immutable, tenant-scoped operational history for API artifact cleanup.
CREATE TABLE developer_api_artifact_cleanup_audit (
    audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_tenant_id UUID NOT NULL,
    product_id UUID NOT NULL,
    api_version VARCHAR(80) NOT NULL,
    artifact_reference VARCHAR(1024) NOT NULL,
    sha256 CHAR(64) NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
    action VARCHAR(32) NOT NULL CHECK (action IN (
        'CLEANUP_CLAIMED', 'CLEANUP_DELETED', 'RETRY_SCHEDULED', 'TERMINAL_FAILURE'
    )),
    actor_reference VARCHAR(160) NOT NULL CHECK (length(btrim(actor_reference)) BETWEEN 1 AND 160),
    attempt_number INTEGER NOT NULL CHECK (attempt_number >= 1),
    reason_code VARCHAR(120),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_developer_api_artifact_cleanup_audit_artifact
        FOREIGN KEY (provider_tenant_id, product_id, api_version)
        REFERENCES developer_api_artifacts(provider_tenant_id, product_id, api_version)
        ON DELETE RESTRICT
);

CREATE INDEX ix_developer_api_artifact_cleanup_audit_history
    ON developer_api_artifact_cleanup_audit
       (provider_tenant_id, product_id, api_version, occurred_at DESC, audit_id);

CREATE INDEX ix_developer_api_artifact_cleanup_audit_tenant_time
    ON developer_api_artifact_cleanup_audit (provider_tenant_id, occurred_at DESC);

CREATE FUNCTION reject_developer_api_artifact_cleanup_audit_mutation()
RETURNS trigger LANGUAGE plpgsql AS $body$
BEGIN
    RAISE EXCEPTION 'API artifact cleanup audit is append-only';
END;
$body$;

CREATE TRIGGER trg_developer_api_artifact_cleanup_audit_immutable
    BEFORE UPDATE OR DELETE ON developer_api_artifact_cleanup_audit
    FOR EACH ROW EXECUTE FUNCTION reject_developer_api_artifact_cleanup_audit_mutation();

CREATE TRIGGER trg_developer_api_artifact_cleanup_audit_no_truncate
    BEFORE TRUNCATE ON developer_api_artifact_cleanup_audit
    FOR EACH STATEMENT EXECUTE FUNCTION reject_developer_api_artifact_cleanup_audit_mutation();

ALTER TABLE developer_api_artifact_cleanup_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE developer_api_artifact_cleanup_audit FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_developer_api_artifact_cleanup_audit
    ON developer_api_artifact_cleanup_audit
    USING (provider_tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid)
    WITH CHECK (provider_tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid);
