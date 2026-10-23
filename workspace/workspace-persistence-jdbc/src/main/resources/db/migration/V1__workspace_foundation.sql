-- PostgreSQL schema for the optional Numou workspace module.
-- The host application supplies tenant identity and resource-level authorization.
CREATE TABLE workspace_workspaces (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  name VARCHAR(200) NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_workspace_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE workspace_spaces (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  workspace_id UUID NOT NULL,
  name VARCHAR(200) NOT NULL,
  slug VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT fk_workspace_space_owner FOREIGN KEY (tenant_id, workspace_id)
    REFERENCES workspace_workspaces (tenant_id, id),
  CONSTRAINT uq_workspace_space_slug UNIQUE (tenant_id, workspace_id, slug),
  CONSTRAINT uq_workspace_space_tenant_id UNIQUE (tenant_id, id)
);

CREATE TABLE workspace_pages (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  workspace_id UUID NOT NULL,
  space_id UUID NOT NULL,
  parent_page_id UUID NULL,
  title VARCHAR(500) NOT NULL,
  revision BIGINT NOT NULL CHECK (revision >= 1),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT fk_workspace_page_space FOREIGN KEY (tenant_id, space_id)
    REFERENCES workspace_spaces (tenant_id, id),
  CONSTRAINT fk_workspace_page_parent FOREIGN KEY (tenant_id, parent_page_id)
    REFERENCES workspace_pages (tenant_id, id),
  CONSTRAINT uq_workspace_page_tenant_id UNIQUE (tenant_id, id)
);
CREATE INDEX ix_workspace_pages_parent ON workspace_pages (tenant_id, space_id, parent_page_id, updated_at DESC);

CREATE TABLE workspace_page_blocks (
  tenant_id UUID NOT NULL,
  page_id UUID NOT NULL,
  block_id UUID NOT NULL,
  block_type VARCHAR(40) NOT NULL,
  text_content TEXT NOT NULL DEFAULT '',
  position INTEGER NOT NULL CHECK (position >= 0),
  attributes_encoded TEXT NOT NULL DEFAULT '',
  PRIMARY KEY (tenant_id, page_id, block_id),
  CONSTRAINT uq_workspace_page_block_position UNIQUE (tenant_id, page_id, position),
  CONSTRAINT fk_workspace_block_page FOREIGN KEY (tenant_id, page_id)
    REFERENCES workspace_pages (tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE workspace_page_revisions (
  tenant_id UUID NOT NULL,
  page_id UUID NOT NULL,
  revision BIGINT NOT NULL CHECK (revision >= 1),
  title VARCHAR(500) NOT NULL,
  actor_id UUID NOT NULL,
  recorded_at TIMESTAMPTZ NOT NULL,
  change_summary TEXT NOT NULL DEFAULT '',
  PRIMARY KEY (tenant_id, page_id, revision),
  CONSTRAINT fk_workspace_revision_page FOREIGN KEY (tenant_id, page_id)
    REFERENCES workspace_pages (tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE workspace_page_revision_blocks (
  tenant_id UUID NOT NULL,
  page_id UUID NOT NULL,
  revision BIGINT NOT NULL,
  block_id UUID NOT NULL,
  block_type VARCHAR(40) NOT NULL,
  text_content TEXT NOT NULL DEFAULT '',
  position INTEGER NOT NULL CHECK (position >= 0),
  attributes_encoded TEXT NOT NULL DEFAULT '',
  PRIMARY KEY (tenant_id, page_id, revision, block_id),
  CONSTRAINT uq_workspace_revision_block_position UNIQUE (tenant_id, page_id, revision, position),
  CONSTRAINT fk_workspace_revision_block FOREIGN KEY (tenant_id, page_id, revision)
    REFERENCES workspace_page_revisions (tenant_id, page_id, revision) ON DELETE CASCADE
);

CREATE TABLE workspace_audit_events (
  id BIGSERIAL PRIMARY KEY,
  tenant_id UUID NOT NULL,
  actor_id UUID NOT NULL,
  action VARCHAR(100) NOT NULL,
  resource_type VARCHAR(100) NOT NULL,
  resource_id UUID NOT NULL,
  revision BIGINT NOT NULL,
  summary TEXT NOT NULL DEFAULT '',
  occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_workspace_audit_resource ON workspace_audit_events (tenant_id, resource_type, resource_id, occurred_at DESC);
