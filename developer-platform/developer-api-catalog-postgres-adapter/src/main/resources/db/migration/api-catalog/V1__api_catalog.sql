-- API catalog metadata. OpenAPI documents remain in the configured artifact store.
CREATE TABLE developer_api_products (
 id UUID NOT NULL,
 provider_tenant_id UUID NOT NULL,
 slug VARCHAR(120) NOT NULL CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
 name VARCHAR(200) NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 200),
 description VARCHAR(2000) NOT NULL DEFAULT '',
 status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','SUSPENDED','RETIRED')),
 revision BIGINT NOT NULL CHECK (revision >= 1),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT pk_developer_api_products PRIMARY KEY(provider_tenant_id,id),
 CONSTRAINT uq_developer_api_products_tenant_slug UNIQUE(provider_tenant_id,slug),
 CONSTRAINT ck_developer_api_products_time CHECK(updated_at >= created_at)
);
CREATE INDEX ix_developer_api_products_listing
 ON developer_api_products(provider_tenant_id,updated_at DESC,id);

CREATE TABLE developer_api_versions (
 provider_tenant_id UUID NOT NULL,
 product_id UUID NOT NULL,
 version VARCHAR(80) NOT NULL CHECK(version ~ '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z.-]+)?$'),
 openapi_version VARCHAR(8) NOT NULL CHECK(openapi_version IN ('3.0','3.1')),
 artifact_reference VARCHAR(1024) NOT NULL CHECK(length(btrim(artifact_reference)) BETWEEN 1 AND 1024),
 sha256 CHAR(64) NOT NULL CHECK(sha256 ~ '^[a-f0-9]{64}$'),
 status VARCHAR(16) NOT NULL CHECK(status IN ('DRAFT','PUBLISHED','DEPRECATED','RETIRED')),
 CONSTRAINT pk_developer_api_versions PRIMARY KEY(provider_tenant_id,product_id,version),
 CONSTRAINT fk_developer_api_versions_product FOREIGN KEY(provider_tenant_id,product_id)
   REFERENCES developer_api_products(provider_tenant_id,id) ON DELETE RESTRICT
);
CREATE INDEX ix_developer_api_versions_status
 ON developer_api_versions(provider_tenant_id,product_id,status);

CREATE TABLE developer_api_scopes (
 provider_tenant_id UUID NOT NULL,
 product_id UUID NOT NULL,
 api_version VARCHAR(80) NOT NULL,
 scope_name VARCHAR(160) NOT NULL CHECK(scope_name ~ '^[a-z][a-z0-9]*([.:][a-z0-9][a-z0-9-]*)*$'),
 description VARCHAR(500) NOT NULL CHECK(length(btrim(description)) BETWEEN 1 AND 500),
 CONSTRAINT pk_developer_api_scopes PRIMARY KEY(provider_tenant_id,product_id,api_version,scope_name),
 CONSTRAINT fk_developer_api_scopes_version FOREIGN KEY(provider_tenant_id,product_id,api_version)
   REFERENCES developer_api_versions(provider_tenant_id,product_id,version) ON DELETE RESTRICT
);

CREATE FUNCTION reject_developer_api_product_identity_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.id<>OLD.id OR NEW.provider_tenant_id<>OLD.provider_tenant_id OR NEW.slug<>OLD.slug
    OR NEW.name<>OLD.name OR NEW.description<>OLD.description OR NEW.created_at<>OLD.created_at THEN
  RAISE EXCEPTION 'API product identity and descriptive metadata are immutable';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER trg_developer_api_product_identity_immutable
 BEFORE UPDATE ON developer_api_products FOR EACH ROW EXECUTE FUNCTION reject_developer_api_product_identity_change();

CREATE FUNCTION reject_developer_api_version_contract_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.provider_tenant_id<>OLD.provider_tenant_id OR NEW.product_id<>OLD.product_id OR NEW.version<>OLD.version
    OR NEW.openapi_version<>OLD.openapi_version OR NEW.artifact_reference<>OLD.artifact_reference OR NEW.sha256<>OLD.sha256 THEN
  RAISE EXCEPTION 'API version contract metadata is immutable';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER trg_developer_api_version_contract_immutable
 BEFORE UPDATE ON developer_api_versions FOR EACH ROW EXECUTE FUNCTION reject_developer_api_version_contract_change();

CREATE FUNCTION reject_developer_api_scope_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'API scope definitions are immutable'; END; $$;
CREATE TRIGGER trg_developer_api_scope_immutable
 BEFORE UPDATE OR DELETE ON developer_api_scopes FOR EACH ROW EXECUTE FUNCTION reject_developer_api_scope_mutation();
CREATE TRIGGER trg_developer_api_scope_no_truncate
 BEFORE TRUNCATE ON developer_api_scopes FOR EACH STATEMENT EXECUTE FUNCTION reject_developer_api_scope_mutation();

DO $$
DECLARE t TEXT;
BEGIN
 FOREACH t IN ARRAY ARRAY['developer_api_products','developer_api_versions','developer_api_scopes'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY',t);
  EXECUTE format('CREATE POLICY %I ON %I USING (provider_tenant_id=nullif(current_setting(''numou.tenant_id'',true),'''')::uuid) WITH CHECK (provider_tenant_id=nullif(current_setting(''numou.tenant_id'',true),'''')::uuid)','tenant_isolation_'||t,t);
 END LOOP;
END; $$;
