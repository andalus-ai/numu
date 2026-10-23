-- Directed page links power related pages and backlinks. Tenant is part of every FK and query.
CREATE TABLE workspace_page_links (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  source_page_id UUID NOT NULL,
  target_page_id UUID NOT NULL,
  created_by UUID NOT NULL,
  label VARCHAR(200) NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT ck_workspace_page_link_not_self CHECK (source_page_id <> target_page_id),
  CONSTRAINT uq_workspace_page_link UNIQUE (tenant_id, source_page_id, target_page_id),
  CONSTRAINT uq_workspace_page_link_tenant_id UNIQUE (tenant_id, id),
  CONSTRAINT fk_workspace_page_link_source FOREIGN KEY (tenant_id, source_page_id)
    REFERENCES workspace_pages (tenant_id, id) ON DELETE CASCADE,
  CONSTRAINT fk_workspace_page_link_target FOREIGN KEY (tenant_id, target_page_id)
    REFERENCES workspace_pages (tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX ix_workspace_page_links_backlinks ON workspace_page_links (tenant_id, target_page_id, created_at DESC);
CREATE INDEX ix_workspace_page_links_outgoing ON workspace_page_links (tenant_id, source_page_id, created_at DESC);
