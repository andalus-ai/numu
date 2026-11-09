-- M5h: durable lifecycle metadata for immutable API specification artifacts.
-- Existing catalog versions are backfilled as registered or published. No existing object
-- is made cleanup-eligible by age or by this migration.
CREATE TABLE developer_api_artifacts (
 provider_tenant_id UUID NOT NULL,
 product_id UUID NOT NULL,
 api_version VARCHAR(80) NOT NULL,
 artifact_reference VARCHAR(1024) NOT NULL CHECK(length(btrim(artifact_reference)) BETWEEN 1 AND 1024),
 sha256 CHAR(64) NOT NULL CHECK(sha256 ~ '^[a-f0-9]{64}$'),
 lifecycle_state VARCHAR(20) NOT NULL CHECK(lifecycle_state IN ('REGISTERED','PUBLISHED','ABANDONED','DELETE_PENDING','DELETED')),
 content_length BIGINT CHECK(content_length IS NULL OR content_length BETWEEN 1 AND 10485760),
 content_type VARCHAR(160),
 retention_until TIMESTAMPTZ,
 legal_hold BOOLEAN NOT NULL DEFAULT FALSE,
 cleanup_attempts INTEGER NOT NULL DEFAULT 0 CHECK(cleanup_attempts >= 0),
 next_cleanup_at TIMESTAMPTZ,
 cleanup_lease_owner VARCHAR(160),
 cleanup_lease_until TIMESTAMPTZ,
 deleted_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT pk_developer_api_artifacts PRIMARY KEY(provider_tenant_id,product_id,api_version),
 CONSTRAINT uq_developer_api_artifacts_reference UNIQUE(provider_tenant_id,artifact_reference),
 CONSTRAINT fk_developer_api_artifacts_version FOREIGN KEY(provider_tenant_id,product_id,api_version)
   REFERENCES developer_api_versions(provider_tenant_id,product_id,version) ON DELETE RESTRICT,
 CONSTRAINT ck_developer_api_artifacts_lease CHECK((cleanup_lease_owner IS NULL)=(cleanup_lease_until IS NULL)),
 CONSTRAINT ck_developer_api_artifacts_deleted CHECK((lifecycle_state='DELETED')=(deleted_at IS NOT NULL)),
 CONSTRAINT ck_developer_api_artifacts_no_deleted_hold CHECK(NOT (lifecycle_state='DELETED' AND legal_hold))
);

INSERT INTO developer_api_artifacts(provider_tenant_id,product_id,api_version,artifact_reference,sha256,lifecycle_state)
SELECT provider_tenant_id,product_id,version,artifact_reference,sha256,
       CASE WHEN status='PUBLISHED' THEN 'PUBLISHED' ELSE 'REGISTERED' END
FROM developer_api_versions;

CREATE INDEX ix_developer_api_artifacts_cleanup
 ON developer_api_artifacts(provider_tenant_id,next_cleanup_at,retention_until,created_at)
 WHERE lifecycle_state IN ('ABANDONED','DELETE_PENDING') AND legal_hold=FALSE;

CREATE FUNCTION guard_developer_api_artifact_lifecycle() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN
  RAISE EXCEPTION 'API artifact lifecycle rows are tombstoned, never physically deleted';
 END IF;
 IF NEW.provider_tenant_id<>OLD.provider_tenant_id OR NEW.product_id<>OLD.product_id
    OR NEW.api_version<>OLD.api_version OR NEW.artifact_reference<>OLD.artifact_reference
    OR NEW.sha256<>OLD.sha256 THEN
  RAISE EXCEPTION 'API artifact identity and content digest are immutable';
 END IF;
 IF OLD.lifecycle_state='PUBLISHED' AND NEW.lifecycle_state<>'PUBLISHED' THEN
  RAISE EXCEPTION 'Published API artifacts are protected from lifecycle cleanup';
 END IF;
 IF OLD.lifecycle_state='DELETED' AND NEW.lifecycle_state<>'DELETED' THEN
  RAISE EXCEPTION 'Deleted API artifact tombstones are immutable';
 END IF;
 IF NOT (
   (OLD.lifecycle_state='REGISTERED' AND NEW.lifecycle_state IN ('REGISTERED','PUBLISHED','ABANDONED'))
   OR (OLD.lifecycle_state='PUBLISHED' AND NEW.lifecycle_state='PUBLISHED')
   OR (OLD.lifecycle_state='ABANDONED' AND NEW.lifecycle_state IN ('ABANDONED','DELETE_PENDING'))
   OR (OLD.lifecycle_state='DELETE_PENDING' AND NEW.lifecycle_state IN ('DELETE_PENDING','ABANDONED','DELETED'))
   OR (OLD.lifecycle_state='DELETED' AND NEW.lifecycle_state='DELETED')
 ) THEN
  RAISE EXCEPTION 'Invalid API artifact lifecycle transition';
 END IF;
 IF NEW.legal_hold AND NEW.lifecycle_state IN ('DELETE_PENDING','DELETED') THEN
  RAISE EXCEPTION 'Artifacts under legal hold cannot be deleted';
 END IF;
 NEW.updated_at=now();
 RETURN NEW;
END; $$;

CREATE TRIGGER trg_developer_api_artifact_lifecycle_guard
 BEFORE UPDATE OR DELETE ON developer_api_artifacts
 FOR EACH ROW EXECUTE FUNCTION guard_developer_api_artifact_lifecycle();
CREATE FUNCTION reject_developer_api_artifact_truncate() RETURNS trigger LANGUAGE plpgsql AS $body$
BEGIN
 RAISE EXCEPTION 'API artifact lifecycle registry cannot be truncated';
END; $body$;
CREATE TRIGGER trg_developer_api_artifact_no_truncate
 BEFORE TRUNCATE ON developer_api_artifacts
 FOR EACH STATEMENT EXECUTE FUNCTION reject_developer_api_artifact_truncate();

ALTER TABLE developer_api_artifacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE developer_api_artifacts FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_developer_api_artifacts ON developer_api_artifacts
 USING (provider_tenant_id=nullif(current_setting('numou.tenant_id',true),'')::uuid)
 WITH CHECK (provider_tenant_id=nullif(current_setting('numou.tenant_id',true),'')::uuid);
