-- Tenant-scoped application configuration with optimistic concurrency and immutable history.
CREATE TABLE application_configurations (
    tenant_id UUID NOT NULL,
    application_id VARCHAR(180) NOT NULL,
    schema_version VARCHAR(32) NOT NULL CHECK (schema_version ~ '^[0-9]+\.[0-9]+(\.[0-9]+)?$'),
    revision BIGINT NOT NULL CHECK (revision >= 1),
    payload_json TEXT NOT NULL CHECK (jsonb_typeof(payload_json::jsonb) = 'object'),
    payload_sha256 CHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[a-f0-9]{64}$'),
    updated_by VARCHAR(200) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, application_id),
    CONSTRAINT fk_application_configuration_installation
        FOREIGN KEY (tenant_id, application_id)
        REFERENCES application_installations (tenant_id, application_id)
);

CREATE TABLE application_configuration_revisions (
    tenant_id UUID NOT NULL,
    application_id VARCHAR(180) NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 1),
    schema_version VARCHAR(32) NOT NULL CHECK (schema_version ~ '^[0-9]+\.[0-9]+(\.[0-9]+)?$'),
    payload_json TEXT NOT NULL CHECK (jsonb_typeof(payload_json::jsonb) = 'object'),
    payload_sha256 CHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[a-f0-9]{64}$'),
    updated_by VARCHAR(200) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, application_id, revision),
    CONSTRAINT fk_application_configuration_revision_installation
        FOREIGN KEY (tenant_id, application_id)
        REFERENCES application_installations (tenant_id, application_id)
);

CREATE FUNCTION reject_application_configuration_revision_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'application configuration revision history is append-only';
END;
$$;

CREATE TRIGGER trg_application_configuration_revisions_immutable
    BEFORE UPDATE OR DELETE ON application_configuration_revisions
    FOR EACH ROW EXECUTE FUNCTION reject_application_configuration_revision_mutation();
CREATE TRIGGER trg_application_configuration_revisions_no_truncate
    BEFORE TRUNCATE ON application_configuration_revisions
    FOR EACH STATEMENT EXECUTE FUNCTION reject_application_configuration_revision_mutation();

ALTER TABLE application_configurations ENABLE ROW LEVEL SECURITY;
ALTER TABLE application_configurations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_application_configurations ON application_configurations
    USING (tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid);

ALTER TABLE application_configuration_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE application_configuration_revisions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_application_configuration_revisions ON application_configuration_revisions
    USING (tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid);
