CREATE TABLE historical_source_ingestion (
    tenant_id uuid NOT NULL,
    ingestion_id uuid NOT NULL,
    source_id uuid NOT NULL,
    input_kind varchar(32) NOT NULL CHECK (input_kind IN ('DOCUMENT_VERSION','CANONICAL_URI')),
    input_document_id uuid,
    input_document_version_id uuid,
    input_sha256 varchar(64),
    input_content_type varchar(255),
    input_uri varchar(2000),
    requested_by varchar(256) NOT NULL,
    requested_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, ingestion_id),
    FOREIGN KEY (tenant_id, source_id) REFERENCES historical_source(tenant_id, source_id),
    CHECK (
        (input_kind = 'DOCUMENT_VERSION'
            AND input_document_id IS NOT NULL
            AND input_document_version_id IS NOT NULL
            AND input_sha256 IS NOT NULL
            AND input_sha256 ~ '^[0-9a-f]{64}$'
            AND input_content_type IS NOT NULL
            AND btrim(input_content_type) <> ''
            AND input_uri IS NULL)
        OR
        (input_kind = 'CANONICAL_URI'
            AND input_document_id IS NULL
            AND input_document_version_id IS NULL
            AND input_sha256 IS NULL
            AND input_content_type IS NULL
            AND input_uri IS NOT NULL
            AND btrim(input_uri) <> ''
            AND input_uri ~* '^https?://')
    )
);

CREATE INDEX historical_source_ingestion_source_idx
    ON historical_source_ingestion(tenant_id, source_id, requested_at, ingestion_id);

CREATE TABLE historical_source_extraction (
    tenant_id uuid NOT NULL,
    extraction_id uuid NOT NULL,
    ingestion_id uuid NOT NULL,
    extraction_kind varchar(32) NOT NULL
        CHECK (extraction_kind IN ('TEXT_EXTRACTION','OCR_TEXT','TRANSCRIPTION','STRUCTURED_METADATA')),
    extractor_id varchar(200) NOT NULL,
    extractor_version varchar(100) NOT NULL,
    output_document_id uuid NOT NULL,
    output_document_version_id uuid NOT NULL,
    input_sha256 varchar(64) NOT NULL CHECK (input_sha256 ~ '^[0-9a-f]{64}$'),
    output_sha256 varchar(64) NOT NULL CHECK (output_sha256 ~ '^[0-9a-f]{64}$'),
    language_tag varchar(64),
    locator varchar(1000),
    worker_ref varchar(256) NOT NULL,
    extracted_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, extraction_id),
    FOREIGN KEY (tenant_id, ingestion_id)
        REFERENCES historical_source_ingestion(tenant_id, ingestion_id),
    UNIQUE (tenant_id, ingestion_id, extractor_id, extractor_version, output_document_version_id)
);

CREATE INDEX historical_source_extraction_ingestion_idx
    ON historical_source_extraction(tenant_id, ingestion_id, extracted_at, extraction_id);

CREATE TRIGGER historical_source_ingestion_append_only
    BEFORE UPDATE OR DELETE ON historical_source_ingestion
    FOR EACH ROW EXECUTE FUNCTION numou_history_reject_immutable_mutation();

CREATE TRIGGER historical_source_extraction_append_only
    BEFORE UPDATE OR DELETE ON historical_source_extraction
    FOR EACH ROW EXECUTE FUNCTION numou_history_reject_immutable_mutation();

ALTER TABLE historical_source_ingestion ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_source_ingestion FORCE ROW LEVEL SECURITY;
CREATE POLICY historical_source_ingestion_tenant_policy ON historical_source_ingestion
    USING (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid);

ALTER TABLE historical_source_extraction ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_source_extraction FORCE ROW LEVEL SECURITY;
CREATE POLICY historical_source_extraction_tenant_policy ON historical_source_extraction
    USING (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid);
