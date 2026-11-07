CREATE TABLE historical_source (
 tenant_id uuid NOT NULL,
 source_id uuid NOT NULL,
 current_revision bigint NOT NULL CHECK (current_revision > 0),
 status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','ARCHIVED')),
 created_at timestamptz NOT NULL,
 updated_at timestamptz NOT NULL,
 PRIMARY KEY (tenant_id, source_id)
);
CREATE TABLE historical_source_revision (
 tenant_id uuid NOT NULL,
 source_id uuid NOT NULL,
 revision bigint NOT NULL CHECK (revision > 0),
 kind varchar(40) NOT NULL,
 title varchar(1000) NOT NULL,
 subtitle varchar(4000),
 description varchar(4000),
 canonical_uri varchar(2000),
 publisher varchar(4000),
 publication_date_text varchar(1000),
 language_tag varchar(64),
 created_at timestamptz NOT NULL,
 updated_at timestamptz NOT NULL,
 PRIMARY KEY (tenant_id, source_id, revision),
 FOREIGN KEY (tenant_id, source_id) REFERENCES historical_source(tenant_id, source_id)
);
ALTER TABLE historical_source ADD CONSTRAINT historical_source_current_revision_fk
 FOREIGN KEY (tenant_id, source_id, current_revision)
 REFERENCES historical_source_revision(tenant_id, source_id, revision)
 DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE historical_claim (
 tenant_id uuid NOT NULL,
 claim_id uuid NOT NULL,
 subject_kind varchar(80) NOT NULL,
 subject_id varchar(200) NOT NULL,
 statement varchar(8000) NOT NULL,
 created_at timestamptz NOT NULL,
 revision bigint NOT NULL CHECK (revision > 0),
 PRIMARY KEY (tenant_id, claim_id)
);
CREATE TABLE historical_claim_evidence (
 tenant_id uuid NOT NULL,
 evidence_id uuid NOT NULL,
 claim_id uuid NOT NULL,
 source_id uuid NOT NULL,
 relation varchar(24) NOT NULL CHECK (relation IN ('SUPPORTS','CONTRADICTS','CONTEXTUALIZES','MENTIONS')),
 locator_type varchar(24) NOT NULL CHECK (locator_type IN ('PAGE','PARAGRAPH','SECTION','TIMECODE','URL_FRAGMENT','COORDINATE','OBJECT_MARK','OTHER')),
 locator varchar(1000) NOT NULL,
 excerpt varchar(8000),
 recorded_at timestamptz NOT NULL,
 PRIMARY KEY (tenant_id, evidence_id),
 FOREIGN KEY (tenant_id, claim_id) REFERENCES historical_claim(tenant_id, claim_id),
 FOREIGN KEY (tenant_id, source_id) REFERENCES historical_source(tenant_id, source_id)
);
CREATE INDEX historical_source_tenant_updated_idx ON historical_source(tenant_id, updated_at DESC, source_id);
CREATE INDEX historical_claim_evidence_claim_idx ON historical_claim_evidence(tenant_id, claim_id, recorded_at, evidence_id);

CREATE FUNCTION numou_history_reject_immutable_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'historical source revisions, claims and evidence are append-only'; END;
$$;
CREATE TRIGGER historical_source_revision_append_only BEFORE UPDATE OR DELETE ON historical_source_revision
 FOR EACH ROW EXECUTE FUNCTION numou_history_reject_immutable_mutation();
CREATE TRIGGER historical_claim_append_only BEFORE UPDATE OR DELETE ON historical_claim
 FOR EACH ROW EXECUTE FUNCTION numou_history_reject_immutable_mutation();
CREATE TRIGGER historical_claim_evidence_append_only BEFORE UPDATE OR DELETE ON historical_claim_evidence
 FOR EACH ROW EXECUTE FUNCTION numou_history_reject_immutable_mutation();

ALTER TABLE historical_source ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_source FORCE ROW LEVEL SECURITY;
ALTER TABLE historical_source_revision ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_source_revision FORCE ROW LEVEL SECURITY;
ALTER TABLE historical_claim ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_claim FORCE ROW LEVEL SECURITY;
ALTER TABLE historical_claim_evidence ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_claim_evidence FORCE ROW LEVEL SECURITY;
CREATE POLICY historical_source_tenant_policy ON historical_source
 USING (tenant_id = NULLIF(current_setting('numou.tenant_id', true),'')::uuid)
 WITH CHECK (tenant_id = NULLIF(current_setting('numou.tenant_id', true),'')::uuid);
CREATE POLICY historical_source_revision_tenant_policy ON historical_source_revision
 USING (tenant_id = NULLIF(current_setting('numou.tenant_id', true),'')::uuid)
 WITH CHECK (tenant_id = NULLIF(current_setting('numou.tenant_id', true),'')::uuid);
CREATE POLICY historical_claim_tenant_policy ON historical_claim
 USING (tenant_id = NULLIF(current_setting('numou.tenant_id', true),'')::uuid)
 WITH CHECK (tenant_id = NULLIF(current_setting('numou.tenant_id', true),'')::uuid);
CREATE POLICY historical_claim_evidence_tenant_policy ON historical_claim_evidence
 USING (tenant_id = NULLIF(current_setting('numou.tenant_id', true),'')::uuid)
 WITH CHECK (tenant_id = NULLIF(current_setting('numou.tenant_id', true),'')::uuid);
