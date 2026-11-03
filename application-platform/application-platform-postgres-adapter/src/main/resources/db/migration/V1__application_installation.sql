-- Tenant-scoped application installation state and transactional transition outbox.
CREATE TABLE application_installations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    application_id VARCHAR(180) NOT NULL,
    package_version VARCHAR(256) NOT NULL,
    package_digest CHAR(64) NOT NULL CHECK (package_digest ~ '^[a-f0-9]{64}$'),
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'REQUESTED','VALIDATING','AWAITING_APPROVAL','PROVISIONING','INSTALLED',
        'ENABLED','SUSPENDED','FAILED','UNINSTALLING','UNINSTALLED')),
    revision BIGINT NOT NULL CHECK (revision >= 1),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(200) NOT NULL,
    updated_by VARCHAR(200) NOT NULL,
    failure_code VARCHAR(120),
    CONSTRAINT uq_application_installation_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_application_installation_tenant_app UNIQUE (tenant_id, application_id),
    CONSTRAINT ck_application_installation_failure CHECK (
        (status = 'FAILED' AND failure_code ~ '^[A-Z][A-Z0-9_]{1,119}$')
        OR (status <> 'FAILED' AND failure_code IS NULL))
);
CREATE INDEX ix_application_installations_tenant_status
    ON application_installations (tenant_id, status, updated_at DESC, id);

CREATE TABLE application_installation_idempotency (
    tenant_id UUID NOT NULL,
    idempotency_key_sha256 CHAR(64) NOT NULL CHECK (idempotency_key_sha256 ~ '^[a-f0-9]{64}$'),
    request_fingerprint CHAR(64) NOT NULL CHECK (request_fingerprint ~ '^[a-f0-9]{64}$'),
    installation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, idempotency_key_sha256),
    CONSTRAINT uq_application_installation_idempotency_install UNIQUE (tenant_id, installation_id),
    CONSTRAINT fk_application_installation_idempotency_install FOREIGN KEY (tenant_id, installation_id)
        REFERENCES application_installations (tenant_id, id)
);

CREATE TABLE application_installation_transitions (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    installation_id UUID NOT NULL,
    application_id VARCHAR(180) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 1),
    actor_id VARCHAR(200) NOT NULL,
    correlation_id VARCHAR(200) NOT NULL DEFAULT '',
    occurred_at TIMESTAMPTZ NOT NULL,
    reason_code VARCHAR(120) NOT NULL DEFAULT '',
    CONSTRAINT uq_application_installation_transition_revision UNIQUE (tenant_id, installation_id, revision),
    CONSTRAINT uq_application_installation_transition_tenant_event UNIQUE (tenant_id, event_id),
    CONSTRAINT fk_application_installation_transition_install FOREIGN KEY (tenant_id, installation_id)
        REFERENCES application_installations (tenant_id, id),
    CONSTRAINT ck_application_installation_transition_from CHECK (
        from_status IS NULL OR from_status IN (
            'REQUESTED','VALIDATING','AWAITING_APPROVAL','PROVISIONING','INSTALLED',
            'ENABLED','SUSPENDED','FAILED','UNINSTALLING','UNINSTALLED')),
    CONSTRAINT ck_application_installation_transition_to CHECK (to_status IN (
        'REQUESTED','VALIDATING','AWAITING_APPROVAL','PROVISIONING','INSTALLED',
        'ENABLED','SUSPENDED','FAILED','UNINSTALLING','UNINSTALLED'))
);
CREATE INDEX ix_application_installation_transitions_history
    ON application_installation_transitions (tenant_id, installation_id, revision);

CREATE FUNCTION reject_application_installation_transition_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'application installation transition history is append-only';
END;
$$;
CREATE TRIGGER trg_application_installation_transitions_immutable
    BEFORE UPDATE OR DELETE ON application_installation_transitions
    FOR EACH ROW EXECUTE FUNCTION reject_application_installation_transition_mutation();


CREATE TRIGGER trg_application_installation_transitions_no_truncate
    BEFORE TRUNCATE ON application_installation_transitions
    FOR EACH STATEMENT EXECUTE FUNCTION reject_application_installation_transition_mutation();

CREATE TRIGGER trg_application_installation_idempotency_immutable
    BEFORE UPDATE OR DELETE ON application_installation_idempotency
    FOR EACH ROW EXECUTE FUNCTION reject_application_installation_transition_mutation();
CREATE TRIGGER trg_application_installation_idempotency_no_truncate
    BEFORE TRUNCATE ON application_installation_idempotency
    FOR EACH STATEMENT EXECUTE FUNCTION reject_application_installation_transition_mutation();

CREATE TABLE application_installation_outbox (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    installation_id UUID NOT NULL,
    application_id VARCHAR(180) NOT NULL,
    package_version VARCHAR(256) NOT NULL,
    package_digest CHAR(64) NOT NULL CHECK (package_digest ~ '^[a-f0-9]{64}$'),
    event_type VARCHAR(100) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 1),
    actor_id VARCHAR(200) NOT NULL,
    correlation_id VARCHAR(200) NOT NULL DEFAULT '',
    reason_code VARCHAR(120) NOT NULL DEFAULT '',
    occurred_at TIMESTAMPTZ NOT NULL,
    delivery_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (delivery_status IN ('PENDING','LEASED','DELIVERED','DEAD')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at TIMESTAMPTZ NOT NULL,
    lease_owner VARCHAR(160),
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    last_failure_code VARCHAR(120),
    delivered_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT fk_application_installation_outbox_transition FOREIGN KEY (tenant_id, event_id)
        REFERENCES application_installation_transitions (tenant_id, event_id),
    CONSTRAINT fk_application_installation_outbox_install FOREIGN KEY (tenant_id, installation_id)
        REFERENCES application_installations (tenant_id, id),
    CONSTRAINT ck_application_installation_outbox_lease CHECK (
        (delivery_status = 'LEASED' AND lease_owner IS NOT NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (delivery_status <> 'LEASED' AND lease_owner IS NULL AND lease_token IS NULL AND lease_until IS NULL)),
    CONSTRAINT ck_application_installation_outbox_delivered CHECK (
        (delivery_status = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR (delivery_status <> 'DELIVERED' AND delivered_at IS NULL))
);
CREATE INDEX ix_application_installation_outbox_due
    ON application_installation_outbox (tenant_id, available_at, occurred_at, event_id)
    WHERE delivery_status IN ('PENDING','LEASED');
CREATE INDEX ix_application_installation_outbox_dead
    ON application_installation_outbox (tenant_id, occurred_at DESC, event_id DESC)
    WHERE delivery_status = 'DEAD';

-- RLS is forced even for table owners. Each adapter transaction must set the trusted
-- tenant using SET LOCAL via set_config('numou.tenant_id', tenant_uuid, true).
DO $$
DECLARE table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'application_installations',
        'application_installation_idempotency',
        'application_installation_transitions',
        'application_installation_outbox'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', table_name);
        EXECUTE format(
            'CREATE POLICY %I ON %I USING (tenant_id = nullif(current_setting(''numou.tenant_id'', true), '''')::uuid) WITH CHECK (tenant_id = nullif(current_setting(''numou.tenant_id'', true), '''')::uuid)',
            'tenant_isolation_' || table_name, table_name);
    END LOOP;
END;
$$;
