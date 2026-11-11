CREATE TABLE historical_source_assessment (
 tenant_id uuid NOT NULL, assessment_id uuid NOT NULL, source_id uuid NOT NULL,
 dimension varchar(32) NOT NULL CHECK (dimension IN ('AUTHENTICITY','RELIABILITY','INDEPENDENCE','PROVENANCE','COMPLETENESS','BIAS','CONDITION')),
 conclusion varchar(24) NOT NULL CHECK (conclusion IN ('VERIFIED','PLAUSIBLE','UNCERTAIN','DOUBTFUL','UNVERIFIED','CONFLICTING')),
 rationale varchar(8000) NOT NULL, methodology_id varchar(200) NOT NULL, methodology_version varchar(80) NOT NULL,
 assessor_ref varchar(200) NOT NULL, assessed_at timestamptz NOT NULL,
 PRIMARY KEY (tenant_id, assessment_id),
 FOREIGN KEY (tenant_id, source_id) REFERENCES historical_source(tenant_id, source_id)
);
CREATE INDEX historical_source_assessment_source_idx ON historical_source_assessment(tenant_id, source_id, assessed_at, assessment_id);
CREATE TABLE historical_source_assessment_review (
 tenant_id uuid NOT NULL, review_id uuid NOT NULL, assessment_id uuid NOT NULL,
 decision varchar(24) NOT NULL CHECK (decision IN ('ACCEPT','REQUEST_CHANGES','REJECT')),
 rationale varchar(8000) NOT NULL, reviewer_ref varchar(200) NOT NULL, reviewed_at timestamptz NOT NULL,
 PRIMARY KEY (tenant_id, review_id),
 FOREIGN KEY (tenant_id, assessment_id) REFERENCES historical_source_assessment(tenant_id, assessment_id)
);
CREATE INDEX historical_source_assessment_review_idx ON historical_source_assessment_review(tenant_id, assessment_id, reviewed_at, review_id);
CREATE TRIGGER historical_source_assessment_append_only BEFORE UPDATE OR DELETE ON historical_source_assessment FOR EACH ROW EXECUTE FUNCTION numou_history_reject_immutable_mutation();
CREATE TRIGGER historical_source_assessment_review_append_only BEFORE UPDATE OR DELETE ON historical_source_assessment_review FOR EACH ROW EXECUTE FUNCTION numou_history_reject_immutable_mutation();
ALTER TABLE historical_source_assessment ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_source_assessment FORCE ROW LEVEL SECURITY;
ALTER TABLE historical_source_assessment_review ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_source_assessment_review FORCE ROW LEVEL SECURITY;
CREATE POLICY historical_source_assessment_tenant_policy ON historical_source_assessment USING (tenant_id = NULLIF(current_setting('numou.tenant_id', true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('numou.tenant_id', true),'')::uuid);
CREATE POLICY historical_source_assessment_review_tenant_policy ON historical_source_assessment_review USING (tenant_id = NULLIF(current_setting('numou.tenant_id', true),'')::uuid) WITH CHECK (tenant_id = NULLIF(current_setting('numou.tenant_id', true),'')::uuid);
