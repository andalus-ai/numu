CREATE TABLE historical_source_relationship (
 tenant_id uuid NOT NULL,
 relationship_id uuid NOT NULL,
 source_id uuid NOT NULL,
 related_source_id uuid NOT NULL,
 relation varchar(24) NOT NULL CHECK (relation IN ('CITES','TRANSLATES','EDITS','DERIVES_FROM','REPRODUCES','RESPONDS_TO','CONTRADICTS','SUPERSEDES','RELATED_TO')),
 locator_type varchar(24) CHECK (locator_type IN ('PAGE','PARAGRAPH','SECTION','TIMECODE','URL_FRAGMENT','COORDINATE','OBJECT_MARK','OTHER')),
 locator varchar(1000),
 note varchar(4000),
 recorded_at timestamptz NOT NULL,
 PRIMARY KEY (tenant_id,relationship_id),
 FOREIGN KEY (tenant_id,source_id) REFERENCES historical_source(tenant_id,source_id),
 FOREIGN KEY (tenant_id,related_source_id) REFERENCES historical_source(tenant_id,source_id),
 CHECK (source_id <> related_source_id),
 CHECK ((locator_type IS NULL) = (locator IS NULL OR btrim(locator) = ''))
);
CREATE INDEX historical_source_relationship_outgoing_idx ON historical_source_relationship(tenant_id,source_id,recorded_at,relationship_id);
CREATE INDEX historical_source_relationship_incoming_idx ON historical_source_relationship(tenant_id,related_source_id,recorded_at,relationship_id);
CREATE TRIGGER historical_source_relationship_append_only BEFORE UPDATE OR DELETE ON historical_source_relationship FOR EACH ROW EXECUTE FUNCTION numou_history_reject_immutable_mutation();
ALTER TABLE historical_source_relationship ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_source_relationship FORCE ROW LEVEL SECURITY;
CREATE POLICY historical_source_relationship_tenant_policy ON historical_source_relationship
 USING (tenant_id=NULLIF(current_setting('numou.tenant_id',true),'')::uuid)
 WITH CHECK (tenant_id=NULLIF(current_setting('numou.tenant_id',true),'')::uuid);
