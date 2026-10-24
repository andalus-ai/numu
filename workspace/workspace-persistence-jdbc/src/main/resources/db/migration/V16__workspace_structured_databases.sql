-- Generic workspace tables. Business-domain aggregates remain owned by their own modules.
CREATE TABLE workspace_databases (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  workspace_id UUID NOT NULL,
  name VARCHAR(200) NOT NULL,
  created_by UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_workspace_database_tenant_id UNIQUE (tenant_id, id),
  CONSTRAINT uq_workspace_database_owner UNIQUE (tenant_id, workspace_id, id),
  CONSTRAINT uq_workspace_database_name UNIQUE (tenant_id, workspace_id, name),
  CONSTRAINT fk_workspace_database_workspace FOREIGN KEY (tenant_id, workspace_id)
    REFERENCES workspace_workspaces (tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE workspace_database_tables (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  database_id UUID NOT NULL,
  name VARCHAR(200) NOT NULL,
  schema_version BIGINT NOT NULL CHECK (schema_version >= 1),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_workspace_database_table_tenant_id UNIQUE (tenant_id, id),
  CONSTRAINT uq_workspace_database_table_owner UNIQUE (tenant_id, database_id, id),
  CONSTRAINT uq_workspace_database_table_name UNIQUE (tenant_id, database_id, name),
  CONSTRAINT fk_workspace_database_table_owner FOREIGN KEY (tenant_id, database_id)
    REFERENCES workspace_databases (tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE workspace_database_properties (
  tenant_id UUID NOT NULL,
  database_id UUID NOT NULL,
  table_id UUID NOT NULL,
  property_id UUID NOT NULL,
  property_key VARCHAR(63) NOT NULL,
  label VARCHAR(200) NOT NULL,
  property_type VARCHAR(32) NOT NULL CHECK (property_type IN
    ('TEXT', 'NUMBER', 'BOOLEAN', 'DATE', 'DATE_TIME', 'SELECT', 'MULTI_SELECT', 'RELATION')),
  required BOOLEAN NOT NULL DEFAULT FALSE,
  options JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(options) = 'array'),
  related_table_id UUID NULL,
  PRIMARY KEY (tenant_id, table_id, property_id),
  CONSTRAINT uq_workspace_database_property_key UNIQUE (tenant_id, table_id, property_key),
  CONSTRAINT fk_workspace_database_property_table FOREIGN KEY (tenant_id, database_id, table_id)
    REFERENCES workspace_database_tables (tenant_id, database_id, id) ON DELETE CASCADE,
  CONSTRAINT fk_workspace_database_property_relation FOREIGN KEY (tenant_id, database_id, related_table_id)
    REFERENCES workspace_database_tables (tenant_id, database_id, id)
);

CREATE TABLE workspace_database_records (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  database_id UUID NOT NULL,
  table_id UUID NOT NULL,
  revision BIGINT NOT NULL CHECK (revision >= 1),
  values JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(values) = 'object'),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  archived_at TIMESTAMPTZ NULL,
  CONSTRAINT uq_workspace_database_record_tenant_id UNIQUE (tenant_id, id),
  CONSTRAINT fk_workspace_database_record_table FOREIGN KEY (tenant_id, database_id, table_id)
    REFERENCES workspace_database_tables (tenant_id, database_id, id) ON DELETE CASCADE
);

CREATE INDEX ix_workspace_database_records_table
  ON workspace_database_records (tenant_id, database_id, table_id, created_at, id)
  WHERE archived_at IS NULL;
CREATE INDEX ix_workspace_database_records_values
  ON workspace_database_records USING GIN (values jsonb_path_ops);
