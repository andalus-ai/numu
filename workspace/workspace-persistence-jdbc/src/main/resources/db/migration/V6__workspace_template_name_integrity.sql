-- Keep the denormalized lookup key honest even when rows are written outside the JDBC adapter.
-- The unique expression index is a second line of defense against case/whitespace-only duplicates.
ALTER TABLE workspace_page_templates
  ADD CONSTRAINT ck_workspace_template_name_normalized
  CHECK (name_normalized = lower(btrim(name)));

CREATE UNIQUE INDEX uq_workspace_template_name_ci
  ON workspace_page_templates (tenant_id, workspace_id, lower(btrim(name)));
