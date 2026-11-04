-- Tenant-local immutable policy revisions. Revision zero means no policy has been published.
CREATE TABLE IF NOT EXISTS vault_policy_head (
    tenant_id UUID PRIMARY KEY,
    active_revision BIGINT NOT NULL DEFAULT 0 CHECK (active_revision >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vault_policy_revision (
    tenant_id UUID NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    published_by VARCHAR(200) NOT NULL,
    approved_by VARCHAR(200) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    rule_count INTEGER NOT NULL CHECK (rule_count >= 0),
    change_type VARCHAR(16) NOT NULL DEFAULT 'PUBLISH' CHECK (change_type IN ('PUBLISH', 'ROLLBACK')),
    source_revision BIGINT,
    PRIMARY KEY (tenant_id, revision),
    CHECK (published_by <> approved_by),
    FOREIGN KEY (tenant_id, source_revision) REFERENCES vault_policy_revision (tenant_id, revision) ON DELETE RESTRICT,
    CHECK ((change_type = 'ROLLBACK' AND source_revision IS NOT NULL AND source_revision < revision)
        OR (change_type = 'PUBLISH' AND source_revision IS NULL))
);

CREATE TABLE IF NOT EXISTS vault_policy_rule (
    tenant_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    rule_id UUID NOT NULL,
    subject_type VARCHAR(32) NOT NULL CHECK (subject_type IN ('PRINCIPAL_ID', 'ROLE', 'WORKLOAD_SUBJECT')),
    subject_value VARCHAR(256) NOT NULL,
    workload_issuer VARCHAR(256),
    workload_audience VARCHAR(256),
    vault_id UUID,
    secret_id UUID,
    operation VARCHAR(32) NOT NULL CHECK (operation IN ('CREATE', 'READ', 'ROTATE', 'REVOKE')),
    effect VARCHAR(8) NOT NULL CHECK (effect IN ('ALLOW', 'DENY')),
    valid_from TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, revision, rule_id),
    FOREIGN KEY (tenant_id, revision) REFERENCES vault_policy_revision (tenant_id, revision) ON DELETE RESTRICT,
    CHECK (valid_until IS NULL OR valid_until > valid_from),
    CHECK ((subject_type = 'WORKLOAD_SUBJECT' AND workload_issuer IS NOT NULL AND workload_audience IS NOT NULL)
        OR (subject_type <> 'WORKLOAD_SUBJECT' AND workload_issuer IS NULL AND workload_audience IS NULL))
);

CREATE INDEX IF NOT EXISTS ix_vault_policy_rule_eval
    ON vault_policy_rule (tenant_id, revision, operation, vault_id, secret_id);

-- Policy publication history is append-only and contains no secret material.
CREATE TABLE IF NOT EXISTS vault_policy_admin_audit (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    actor VARCHAR(200) NOT NULL,
    approved_by VARCHAR(200) NOT NULL,
    event_type VARCHAR(40) NOT NULL CHECK (event_type IN ('POLICY_PUBLISHED', 'POLICY_ROLLED_BACK')),
    occurred_at TIMESTAMPTZ NOT NULL,
    rule_count INTEGER NOT NULL CHECK (rule_count >= 0),
    FOREIGN KEY (tenant_id, revision) REFERENCES vault_policy_revision (tenant_id, revision) ON DELETE RESTRICT
);

-- Every evaluated decision, including default-deny and explicit deny, is durably recorded.
-- subject_reference is a platform principal/workload identifier, never a credential or token.
CREATE TABLE IF NOT EXISTS vault_policy_decision_audit (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    policy_revision BIGINT NOT NULL CHECK (policy_revision >= 0),
    subject_reference VARCHAR(256) NOT NULL,
    vault_id UUID NOT NULL,
    secret_id UUID NOT NULL,
    operation VARCHAR(32) NOT NULL CHECK (operation IN ('CREATE', 'READ', 'ROTATE', 'REVOKE')),
    effect VARCHAR(8) NOT NULL CHECK (effect IN ('ALLOW', 'DENY')),
    matched_rule_id UUID,
    reason_code VARCHAR(80) NOT NULL,
    correlation_id VARCHAR(160),
    evaluated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_vault_policy_decision_tenant_time
    ON vault_policy_decision_audit (tenant_id, evaluated_at DESC);

-- Published revisions and their rules are immutable. Corrections publish a new revision.
CREATE OR REPLACE FUNCTION reject_vault_policy_revision_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'published vault policy revisions are immutable';
END;
$$;

DROP TRIGGER IF EXISTS trg_vault_policy_revision_immutable ON vault_policy_revision;
CREATE TRIGGER trg_vault_policy_revision_immutable
    BEFORE UPDATE OR DELETE ON vault_policy_revision
    FOR EACH ROW EXECUTE FUNCTION reject_vault_policy_revision_mutation();

CREATE OR REPLACE FUNCTION reject_insert_into_published_vault_policy() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    active BIGINT;
BEGIN
    SELECT active_revision INTO active FROM vault_policy_head WHERE tenant_id = NEW.tenant_id FOR UPDATE;
    IF active IS NOT NULL AND active >= NEW.revision THEN
        RAISE EXCEPTION 'published vault policy revisions are immutable';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_vault_policy_rule_insert_guard ON vault_policy_rule;
CREATE TRIGGER trg_vault_policy_rule_insert_guard
    BEFORE INSERT ON vault_policy_rule
    FOR EACH ROW EXECUTE FUNCTION reject_insert_into_published_vault_policy();

DROP TRIGGER IF EXISTS trg_vault_policy_rule_immutable ON vault_policy_rule;
CREATE TRIGGER trg_vault_policy_rule_immutable
    BEFORE UPDATE OR DELETE ON vault_policy_rule
    FOR EACH ROW EXECUTE FUNCTION reject_vault_policy_revision_mutation();

DROP TRIGGER IF EXISTS trg_vault_policy_admin_audit_immutable ON vault_policy_admin_audit;
CREATE TRIGGER trg_vault_policy_admin_audit_immutable
    BEFORE UPDATE OR DELETE ON vault_policy_admin_audit
    FOR EACH ROW EXECUTE FUNCTION reject_vault_policy_revision_mutation();

DROP TRIGGER IF EXISTS trg_vault_policy_decision_audit_immutable ON vault_policy_decision_audit;
CREATE TRIGGER trg_vault_policy_decision_audit_immutable
    BEFORE UPDATE OR DELETE ON vault_policy_decision_audit
    FOR EACH ROW EXECUTE FUNCTION reject_vault_policy_revision_mutation();
