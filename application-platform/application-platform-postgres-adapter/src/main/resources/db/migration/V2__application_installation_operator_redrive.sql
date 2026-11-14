-- Stable host-provisioning operation identity and immutable operator redrive audit.
ALTER TABLE application_installation_outbox
    ADD COLUMN operation_id UUID;
UPDATE application_installation_outbox SET operation_id = event_id WHERE operation_id IS NULL;
ALTER TABLE application_installation_outbox ALTER COLUMN operation_id SET NOT NULL;
ALTER TABLE application_installation_outbox ALTER COLUMN operation_id SET DEFAULT gen_random_uuid();
CREATE INDEX ix_application_installation_outbox_operation
    ON application_installation_outbox (tenant_id, operation_id, occurred_at);

CREATE TABLE application_installation_redrive_audit (
    audit_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    installation_id UUID NOT NULL,
    dead_event_id UUID NOT NULL,
    redrive_event_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    idempotency_key_sha256 CHAR(64) NOT NULL CHECK (idempotency_key_sha256 ~ '^[a-f0-9]{64}$'),
    request_fingerprint CHAR(64) NOT NULL CHECK (request_fingerprint ~ '^[a-f0-9]{64}$'),
    actor_id VARCHAR(200) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_application_installation_redrive_idempotency
        UNIQUE (tenant_id, idempotency_key_sha256),
    CONSTRAINT fk_application_installation_redrive_install
        FOREIGN KEY (tenant_id, installation_id)
        REFERENCES application_installations (tenant_id, id),
    CONSTRAINT fk_application_installation_redrive_dead_event
        FOREIGN KEY (tenant_id, dead_event_id)
        REFERENCES application_installation_outbox (tenant_id, event_id),
    CONSTRAINT fk_application_installation_redrive_new_event
        FOREIGN KEY (tenant_id, redrive_event_id)
        REFERENCES application_installation_transitions (tenant_id, event_id)
);
CREATE INDEX ix_application_installation_redrive_history
    ON application_installation_redrive_audit (tenant_id, installation_id, occurred_at DESC, audit_id DESC);

CREATE TRIGGER trg_application_installation_redrive_immutable
    BEFORE UPDATE OR DELETE ON application_installation_redrive_audit
    FOR EACH ROW EXECUTE FUNCTION reject_application_installation_transition_mutation();
CREATE TRIGGER trg_application_installation_redrive_no_truncate
    BEFORE TRUNCATE ON application_installation_redrive_audit
    FOR EACH STATEMENT EXECUTE FUNCTION reject_application_installation_transition_mutation();

ALTER TABLE application_installation_redrive_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE application_installation_redrive_audit FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_application_installation_redrive_audit
    ON application_installation_redrive_audit
    USING (tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid);
