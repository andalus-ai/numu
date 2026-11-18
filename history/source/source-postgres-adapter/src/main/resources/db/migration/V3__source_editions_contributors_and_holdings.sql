CREATE TABLE historical_source_contributor (
 tenant_id uuid NOT NULL, contributor_id uuid NOT NULL, source_id uuid NOT NULL,
 display_name varchar(500) NOT NULL, authority_ref varchar(500), role varchar(32) NOT NULL
 CHECK (role IN ('AUTHOR','EDITOR','TRANSLATOR','COMPILER','ILLUSTRATOR','PHOTOGRAPHER','CARTOGRAPHER','ORAL_INFORMANT','INTERVIEWER','RECORDER','PUBLISHER','OTHER')),
 sequence_no integer NOT NULL CHECK (sequence_no >= 0), attribution_note varchar(4000),
 PRIMARY KEY (tenant_id,contributor_id),
 FOREIGN KEY (tenant_id,source_id) REFERENCES historical_source(tenant_id,source_id)
);
CREATE INDEX historical_source_contributor_source_idx ON historical_source_contributor(tenant_id,source_id,sequence_no,contributor_id);

CREATE TABLE historical_source_edition (
 tenant_id uuid NOT NULL, edition_id uuid NOT NULL, source_id uuid NOT NULL,
 edition_designation varchar(500) NOT NULL, publisher varchar(500), publication_place varchar(300),
 publication_date_text varchar(200), language_tag varchar(64), identifier varchar(300), canonical_uri varchar(2000),
 notes varchar(4000), recorded_at timestamptz NOT NULL,
 PRIMARY KEY (tenant_id,edition_id),
 FOREIGN KEY (tenant_id,source_id) REFERENCES historical_source(tenant_id,source_id)
);
CREATE INDEX historical_source_edition_source_idx ON historical_source_edition(tenant_id,source_id,recorded_at,edition_id);

CREATE TABLE historical_archival_holding (
 tenant_id uuid NOT NULL, holding_id uuid NOT NULL, source_id uuid NOT NULL,
 institution_ref varchar(500), institution_name varchar(500) NOT NULL, repository_name varchar(500),
 collection_name varchar(500), shelfmark varchar(500) NOT NULL,
 holding_type varchar(24) NOT NULL CHECK (holding_type IN ('PHYSICAL','DIGITAL','MICROFILM','MICROFICHE','REPRODUCTION','UNKNOWN')),
 catalog_uri varchar(2000), access_conditions varchar(2000), notes varchar(4000), recorded_at timestamptz NOT NULL,
 PRIMARY KEY (tenant_id,holding_id),
 FOREIGN KEY (tenant_id,source_id) REFERENCES historical_source(tenant_id,source_id)
);
CREATE INDEX historical_archival_holding_source_idx ON historical_archival_holding(tenant_id,source_id,recorded_at,holding_id);

CREATE TABLE historical_archival_catalog_identifier (
 tenant_id uuid NOT NULL, identifier_id uuid NOT NULL, holding_id uuid NOT NULL,
 scheme varchar(80) NOT NULL, identifier_value varchar(500) NOT NULL, assigning_authority varchar(500),
 recorded_at timestamptz NOT NULL,
 PRIMARY KEY (tenant_id,identifier_id),
 FOREIGN KEY (tenant_id,holding_id) REFERENCES historical_archival_holding(tenant_id,holding_id),
 UNIQUE (tenant_id,holding_id,scheme,identifier_value)
);
CREATE INDEX historical_archival_identifier_holding_idx ON historical_archival_catalog_identifier(tenant_id,holding_id,recorded_at,identifier_id);

CREATE TRIGGER historical_source_contributor_append_only BEFORE UPDATE OR DELETE ON historical_source_contributor FOR EACH ROW EXECUTE FUNCTION numou_history_reject_immutable_mutation();
CREATE TRIGGER historical_source_edition_append_only BEFORE UPDATE OR DELETE ON historical_source_edition FOR EACH ROW EXECUTE FUNCTION numou_history_reject_immutable_mutation();
CREATE TRIGGER historical_archival_holding_append_only BEFORE UPDATE OR DELETE ON historical_archival_holding FOR EACH ROW EXECUTE FUNCTION numou_history_reject_immutable_mutation();
CREATE TRIGGER historical_archival_identifier_append_only BEFORE UPDATE OR DELETE ON historical_archival_catalog_identifier FOR EACH ROW EXECUTE FUNCTION numou_history_reject_immutable_mutation();

ALTER TABLE historical_source_contributor ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_source_contributor FORCE ROW LEVEL SECURITY;
ALTER TABLE historical_source_edition ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_source_edition FORCE ROW LEVEL SECURITY;
ALTER TABLE historical_archival_holding ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_archival_holding FORCE ROW LEVEL SECURITY;
ALTER TABLE historical_archival_catalog_identifier ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_archival_catalog_identifier FORCE ROW LEVEL SECURITY;
CREATE POLICY historical_source_contributor_tenant_policy ON historical_source_contributor USING (tenant_id=NULLIF(current_setting('numou.tenant_id',true),'')::uuid) WITH CHECK (tenant_id=NULLIF(current_setting('numou.tenant_id',true),'')::uuid);
CREATE POLICY historical_source_edition_tenant_policy ON historical_source_edition USING (tenant_id=NULLIF(current_setting('numou.tenant_id',true),'')::uuid) WITH CHECK (tenant_id=NULLIF(current_setting('numou.tenant_id',true),'')::uuid);
CREATE POLICY historical_archival_holding_tenant_policy ON historical_archival_holding USING (tenant_id=NULLIF(current_setting('numou.tenant_id',true),'')::uuid) WITH CHECK (tenant_id=NULLIF(current_setting('numou.tenant_id',true),'')::uuid);
CREATE POLICY historical_archival_identifier_tenant_policy ON historical_archival_catalog_identifier USING (tenant_id=NULLIF(current_setting('numou.tenant_id',true),'')::uuid) WITH CHECK (tenant_id=NULLIF(current_setting('numou.tenant_id',true),'')::uuid);
