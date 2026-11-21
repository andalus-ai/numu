-- Relationship commands have their own replay/audit records so the
-- connection aggregate's approval lifecycle and foreign keys remain intact.
ALTER TABLE sg_outbox
    ADD COLUMN aggregate_type VARCHAR(48) NOT NULL DEFAULT 'CONNECTION';

ALTER TABLE sg_outbox
    DROP CONSTRAINT ux_sg_outbox_aggregate_event;

ALTER TABLE sg_outbox
    ADD CONSTRAINT ux_sg_outbox_aggregate_event
    UNIQUE (tenant_id, aggregate_type, aggregate_id, aggregate_version, event_type);

CREATE TABLE sg_relationship_command_idempotency (
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_fingerprint VARCHAR(256) NOT NULL,
    relationship_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, idempotency_key),
    CONSTRAINT fk_sg_relationship_idempotency
        FOREIGN KEY (tenant_id, relationship_id)
        REFERENCES sg_relationship (tenant_id, relationship_id)
);

CREATE TABLE sg_relationship_audit (
    audit_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    relationship_id UUID NOT NULL,
    actor_party_id UUID NOT NULL,
    action VARCHAR(48) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_sg_relationship_audit
        FOREIGN KEY (tenant_id, relationship_id)
        REFERENCES sg_relationship (tenant_id, relationship_id)
);

CREATE INDEX ix_sg_relationship_audit_history
    ON sg_relationship_audit (tenant_id, relationship_id, occurred_at, audit_id);

CREATE FUNCTION sg_reject_relationship_audit_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'social graph relationship audit is append-only';
END;
$$;

CREATE TRIGGER trg_sg_relationship_audit_immutable
    BEFORE UPDATE OR DELETE ON sg_relationship_audit
    FOR EACH ROW EXECUTE FUNCTION sg_reject_relationship_audit_mutation();
