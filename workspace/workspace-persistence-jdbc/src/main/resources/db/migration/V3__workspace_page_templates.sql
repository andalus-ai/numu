-- Reusable page blueprints are scoped to a tenant and workspace.
CREATE TABLE workspace_page_templates (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  workspace_id UUID NOT NULL,
  created_by UUID NOT NULL,
  name VARCHAR(200) NOT NULL,
  name_normalized VARCHAR(200) NOT NULL,
  title VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_workspace_template_tenant_id UNIQUE (tenant_id, id),
  CONSTRAINT uq_workspace_template_name UNIQUE (tenant_id, workspace_id, name_normalized),
  CONSTRAINT fk_workspace_template_workspace FOREIGN KEY (tenant_id, workspace_id)
    REFERENCES workspace_workspaces (tenant_id, id) ON DELETE CASCADE
);

CREATE INDEX ix_workspace_templates_listing
  ON workspace_page_templates (tenant_id, workspace_id, name_normalized, id);

CREATE TABLE workspace_page_template_blocks (
  tenant_id UUID NOT NULL,
  template_id UUID NOT NULL,
  block_id UUID NOT NULL,
  block_type VARCHAR(40) NOT NULL,
  text_content TEXT NOT NULL DEFAULT '',
  position INTEGER NOT NULL CHECK (position >= 0),
  attributes_encoded TEXT NOT NULL DEFAULT '',
  PRIMARY KEY (tenant_id, template_id, block_id),
  CONSTRAINT uq_workspace_template_block_position UNIQUE (tenant_id, template_id, position),
  CONSTRAINT fk_workspace_template_block_template FOREIGN KEY (tenant_id, template_id)
    REFERENCES workspace_page_templates (tenant_id, id) ON DELETE CASCADE
);
