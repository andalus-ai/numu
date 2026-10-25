-- Add recoverable worker leases to durable staged schema migrations.
-- A worker may reclaim a RUNNING job only after the prior lease expires.
ALTER TABLE workspace_database_schema_migrations
  ADD COLUMN lease_token UUID NULL,
  ADD COLUMN lease_until TIMESTAMPTZ NULL;

ALTER TABLE workspace_database_schema_migrations
  ADD CONSTRAINT ck_workspace_schema_migration_lease_pair
  CHECK ((lease_token IS NULL AND lease_until IS NULL)
      OR (lease_token IS NOT NULL AND lease_until IS NOT NULL));

CREATE INDEX ix_workspace_schema_migrations_expired_lease
  ON workspace_database_schema_migrations (status, lease_until, updated_at)
  WHERE status = 'RUNNING';
