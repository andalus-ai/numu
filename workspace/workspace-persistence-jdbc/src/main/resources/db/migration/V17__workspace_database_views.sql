-- Saved views are generic presentation/query configuration, not authoritative domain data.
CREATE TABLE workspace_database_views (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  database_id UUID NOT NULL,
  table_id UUID NOT NULL,
  created_by UUID NOT NULL,
  name VARCHAR(200) NOT NULL,
  view_type VARCHAR(16) NOT NULL CHECK (view_type IN ('TABLE','BOARD','LIST','GALLERY','CALENDAR')),
  filters JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(filters) = 'object'),
  sorts JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(sorts) = 'array'),
  visible_properties JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(visible_properties) = 'array'),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_workspace_database_view_tenant_id UNIQUE (tenant_id, id),
  CONSTRAINT uq_workspace_database_view_name UNIQUE (tenant_id, table_id, created_by, name),
  CONSTRAINT fk_workspace_database_view_table FOREIGN KEY (tenant_id, database_id, table_id)
    REFERENCES workspace_database_tables (tenant_id, database_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_workspace_database_views_owner
  ON workspace_database_views (tenant_id, database_id, table_id, created_by, name);
