CREATE TABLE content_type (
 tenant_id UUID NOT NULL, content_type_id UUID NOT NULL, type_key VARCHAR(63) NOT NULL,
 display_name VARCHAR(200) NOT NULL, created_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY (tenant_id, content_type_id), CONSTRAINT uq_content_type_tenant_key UNIQUE (tenant_id, type_key)
);
CREATE TABLE content_type_schema (
 tenant_id UUID NOT NULL, content_type_id UUID NOT NULL, schema_version INTEGER NOT NULL,
 status VARCHAR(16) NOT NULL, created_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ,
 PRIMARY KEY (tenant_id, content_type_id, schema_version),
 CONSTRAINT fk_content_type_schema_type FOREIGN KEY (tenant_id, content_type_id)
  REFERENCES content_type (tenant_id, content_type_id) ON DELETE RESTRICT,
 CONSTRAINT ck_content_type_schema_version CHECK (schema_version >= 1),
 CONSTRAINT ck_content_type_schema_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
 CONSTRAINT ck_content_type_schema_publication CHECK (
  (status = 'DRAFT' AND published_at IS NULL) OR (status = 'PUBLISHED' AND published_at IS NOT NULL)
 )
);
CREATE TABLE content_type_field (
 tenant_id UUID NOT NULL, content_type_id UUID NOT NULL, schema_version INTEGER NOT NULL,
 field_name VARCHAR(63) NOT NULL, field_type VARCHAR(16) NOT NULL, required BOOLEAN NOT NULL, nullable BOOLEAN NOT NULL,
 min_length INTEGER, max_length INTEGER, min_number NUMERIC, max_number NUMERIC, validation_pattern TEXT,
 PRIMARY KEY (tenant_id, content_type_id, schema_version, field_name),
 CONSTRAINT fk_content_type_field_schema FOREIGN KEY (tenant_id, content_type_id, schema_version)
  REFERENCES content_type_schema (tenant_id, content_type_id, schema_version) ON DELETE RESTRICT,
 CONSTRAINT ck_content_type_field_type CHECK (field_type IN ('STRING','TEXT','INTEGER','DECIMAL','BOOLEAN','DATE','DATETIME','EMAIL','URL','JSON')),
 CONSTRAINT ck_content_type_field_length CHECK ((min_length IS NULL OR min_length >= 0) AND (max_length IS NULL OR max_length >= 0)
  AND (min_length IS NULL OR max_length IS NULL OR min_length <= max_length)),
 CONSTRAINT ck_content_type_field_number CHECK (min_number IS NULL OR max_number IS NULL OR min_number <= max_number)
);
CREATE TABLE content_type_field_option (
 tenant_id UUID NOT NULL, content_type_id UUID NOT NULL, schema_version INTEGER NOT NULL,
 field_name VARCHAR(63) NOT NULL, option_value TEXT NOT NULL, option_order INTEGER NOT NULL,
 PRIMARY KEY (tenant_id, content_type_id, schema_version, field_name, option_order),
 CONSTRAINT fk_content_type_field_option_field FOREIGN KEY (tenant_id, content_type_id, schema_version, field_name)
  REFERENCES content_type_field (tenant_id, content_type_id, schema_version, field_name) ON DELETE RESTRICT
);
CREATE INDEX ix_content_type_schema_status ON content_type_schema (tenant_id, content_type_id, status, schema_version DESC);
