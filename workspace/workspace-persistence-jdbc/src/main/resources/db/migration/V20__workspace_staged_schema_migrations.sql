-- Durable control plane for resumable, staged structured-database schema migrations.
-- Transformed values are staged separately; active record data and schema remain unchanged
-- until the migration is validated and atomically cut over.
CREATE TABLE workspace_database_schema_migrations (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  database_id UUID NOT NULL,
  table_id UUID NOT NULL,
  requested_by UUID NOT NULL,
  expected_schema_version BIGINT NOT NULL CHECK (expected_schema_version > 0),
  target_schema_version BIGINT NOT NULL CHECK (target_schema_version = expected_schema_version + 1),
  target_table_name VARCHAR(200) NOT NULL,
  target_properties JSONB NOT NULL CHECK (jsonb_typeof(target_properties) = 'array'),
  status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','RUNNING','READY','APPLIED','FAILED','CANCELLED')),
  batch_size INTEGER NOT NULL CHECK (batch_size BETWEEN 1 AND 500),
  total_records BIGINT NOT NULL DEFAULT 0 CHECK (total_records >= 0),
  staged_records BIGINT NOT NULL DEFAULT 0 CHECK (staged_records >= 0),
  last_record_id UUID NULL,
  revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
  failure_code VARCHAR(80) NULL,
  failure_message VARCHAR(1000) NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ NULL,
  CONSTRAINT uq_workspace_schema_migration_tenant_id UNIQUE (tenant_id, id),
  CONSTRAINT fk_workspace_schema_migration_table
    FOREIGN KEY (tenant_id, database_id, table_id)
    REFERENCES workspace_database_tables (tenant_id, database_id, id) ON DELETE CASCADE,
  CONSTRAINT ck_workspace_schema_migration_progress CHECK (staged_records <= total_records)
);

CREATE INDEX ix_workspace_schema_migrations_resume
  ON workspace_database_schema_migrations (tenant_id, status, updated_at, id);

CREATE TABLE workspace_database_schema_migration_records (
  tenant_id UUID NOT NULL,
  migration_id UUID NOT NULL,
  record_id UUID NOT NULL,
  expected_record_revision BIGINT NOT NULL CHECK (expected_record_revision > 0),
  migrated_record_data JSONB NOT NULL CHECK (jsonb_typeof(migrated_record_data) = 'object'),
  staged_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (tenant_id, migration_id, record_id),
  CONSTRAINT fk_workspace_schema_migration_record_job
    FOREIGN KEY (tenant_id, migration_id)
    REFERENCES workspace_database_schema_migrations (tenant_id, id) ON DELETE CASCADE,
  CONSTRAINT fk_workspace_schema_migration_record_source
    FOREIGN KEY (tenant_id, record_id)
    REFERENCES workspace_database_records (tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX ix_workspace_schema_migration_records_source
  ON workspace_database_schema_migration_records (tenant_id, record_id);
