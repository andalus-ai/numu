-- Enforce that staged migration rows belong to both the migration's table and the source record's table.
-- Application checks remain useful for clear errors; these constraints protect direct SQL and future adapters.
ALTER TABLE workspace_database_records
  ADD CONSTRAINT uq_workspace_database_record_scope
  UNIQUE (tenant_id, database_id, table_id, id);

ALTER TABLE workspace_database_schema_migrations
  ADD CONSTRAINT uq_workspace_schema_migration_scope
  UNIQUE (tenant_id, database_id, table_id, id);

ALTER TABLE workspace_database_schema_migration_records
  ADD COLUMN database_id UUID,
  ADD COLUMN table_id UUID;

UPDATE workspace_database_schema_migration_records staged
   SET database_id = migration.database_id,
       table_id = migration.table_id
  FROM workspace_database_schema_migrations migration
 WHERE migration.tenant_id = staged.tenant_id
   AND migration.id = staged.migration_id;

ALTER TABLE workspace_database_schema_migration_records
  ALTER COLUMN database_id SET NOT NULL,
  ALTER COLUMN table_id SET NOT NULL,
  DROP CONSTRAINT fk_workspace_schema_migration_record_job,
  DROP CONSTRAINT fk_workspace_schema_migration_record_source,
  ADD CONSTRAINT fk_workspace_schema_migration_record_job_scope
    FOREIGN KEY (tenant_id, database_id, table_id, migration_id)
    REFERENCES workspace_database_schema_migrations (tenant_id, database_id, table_id, id)
    ON DELETE CASCADE,
  ADD CONSTRAINT fk_workspace_schema_migration_record_source_scope
    FOREIGN KEY (tenant_id, database_id, table_id, record_id)
    REFERENCES workspace_database_records (tenant_id, database_id, table_id, id)
    ON DELETE CASCADE;

CREATE INDEX ix_workspace_schema_migration_records_table
  ON workspace_database_schema_migration_records (tenant_id, database_id, table_id, migration_id, record_id);
