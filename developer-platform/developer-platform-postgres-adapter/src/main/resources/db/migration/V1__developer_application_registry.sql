-- Tenant-owned developer application registry. Set numou.tenant_id transaction-locally from verified context.
CREATE TABLE developer_applications (
 id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
 slug VARCHAR(63) NOT NULL CHECK (slug ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$'),
 name VARCHAR(120) NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 120),
 description VARCHAR(2000) NOT NULL DEFAULT '',
 status VARCHAR(16) NOT NULL CHECK(status IN ('ACTIVE','SUSPENDED','ARCHIVED')),
 revision BIGINT NOT NULL CHECK(revision>=1), created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 created_by VARCHAR(200) NOT NULL, updated_by VARCHAR(200) NOT NULL,
 CONSTRAINT uq_developer_application_tenant_id UNIQUE(tenant_id,id),
 CONSTRAINT uq_developer_application_tenant_slug UNIQUE(tenant_id,slug),
 CONSTRAINT ck_developer_application_time CHECK(updated_at>=created_at)
);
CREATE INDEX ix_developer_application_tenant_listing ON developer_applications(tenant_id,id);
CREATE TABLE developer_application_idempotency(
 tenant_id UUID NOT NULL,idempotency_key_sha256 CHAR(64) NOT NULL CHECK(idempotency_key_sha256~'^[a-f0-9]{64}$'),
 request_fingerprint CHAR(64) NOT NULL CHECK(request_fingerprint~'^[a-f0-9]{64}$'),application_id UUID NOT NULL,created_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(tenant_id,idempotency_key_sha256),
 CONSTRAINT fk_developer_application_idempotency FOREIGN KEY(tenant_id,application_id) REFERENCES developer_applications(tenant_id,id)
);
CREATE TABLE developer_application_events(
 event_id UUID PRIMARY KEY,tenant_id UUID NOT NULL,application_id UUID NOT NULL,event_type VARCHAR(100) NOT NULL,
 revision BIGINT NOT NULL CHECK(revision>=1),actor_id VARCHAR(200) NOT NULL,correlation_id VARCHAR(200) NOT NULL DEFAULT '',occurred_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uq_developer_application_event_revision UNIQUE(tenant_id,application_id,revision),
 CONSTRAINT uq_developer_application_event_tenant_id UNIQUE(tenant_id,event_id),
 CONSTRAINT fk_developer_application_event FOREIGN KEY(tenant_id,application_id) REFERENCES developer_applications(tenant_id,id)
);
CREATE FUNCTION reject_developer_application_event_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'developer application event history is append-only'; END; $$;
CREATE TRIGGER trg_developer_application_events_immutable BEFORE UPDATE OR DELETE ON developer_application_events FOR EACH ROW EXECUTE FUNCTION reject_developer_application_event_mutation();
CREATE TRIGGER trg_developer_application_events_no_truncate BEFORE TRUNCATE ON developer_application_events FOR EACH STATEMENT EXECUTE FUNCTION reject_developer_application_event_mutation();
CREATE TABLE developer_application_outbox(
 event_id UUID PRIMARY KEY,tenant_id UUID NOT NULL,application_id UUID NOT NULL,event_type VARCHAR(100) NOT NULL,
 revision BIGINT NOT NULL CHECK(revision>=1),actor_id VARCHAR(200) NOT NULL,correlation_id VARCHAR(200) NOT NULL DEFAULT '',
 occurred_at TIMESTAMPTZ NOT NULL,delivery_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(delivery_status IN('PENDING','LEASED','DELIVERED','DEAD')),
 attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts>=0),available_at TIMESTAMPTZ NOT NULL,lease_owner VARCHAR(160),lease_token UUID,lease_until TIMESTAMPTZ,delivered_at TIMESTAMPTZ,
 CONSTRAINT fk_developer_application_outbox_event FOREIGN KEY(tenant_id,event_id) REFERENCES developer_application_events(tenant_id,event_id),
 CONSTRAINT fk_developer_application_outbox_app FOREIGN KEY(tenant_id,application_id) REFERENCES developer_applications(tenant_id,id),
 CONSTRAINT ck_developer_application_outbox_lease CHECK((delivery_status='LEASED' AND lease_owner IS NOT NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL) OR (delivery_status<>'LEASED' AND lease_owner IS NULL AND lease_token IS NULL AND lease_until IS NULL)),
 CONSTRAINT ck_developer_application_outbox_delivered CHECK((delivery_status='DELIVERED' AND delivered_at IS NOT NULL) OR (delivery_status<>'DELIVERED' AND delivered_at IS NULL))
);
CREATE INDEX ix_developer_application_outbox_due ON developer_application_outbox(tenant_id,available_at,occurred_at,event_id) WHERE delivery_status IN('PENDING','LEASED');
DO $$
DECLARE t TEXT;
BEGIN
 FOREACH t IN ARRAY ARRAY['developer_applications','developer_application_idempotency','developer_application_events','developer_application_outbox'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('CREATE POLICY %I ON %I USING (tenant_id=nullif(current_setting(''numou.tenant_id'',true),'''')::uuid) WITH CHECK (tenant_id=nullif(current_setting(''numou.tenant_id'',true),'''')::uuid)','tenant_isolation_'||t,t);
 END LOOP;
END; $$;
