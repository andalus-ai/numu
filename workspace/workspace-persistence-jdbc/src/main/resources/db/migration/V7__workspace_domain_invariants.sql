-- Align PostgreSQL's storage boundary with the required/canonical values enforced by domain records.
-- This migration deliberately fails rather than rewriting existing rows that violate these invariants.
ALTER TABLE workspace_workspaces
  ADD CONSTRAINT ck_workspace_name_nonblank CHECK (length(btrim(name)) > 0);

ALTER TABLE workspace_spaces
  ADD CONSTRAINT ck_workspace_space_name_nonblank CHECK (length(btrim(name)) > 0),
  ADD CONSTRAINT ck_workspace_space_slug_canonical
    CHECK (slug = lower(btrim(slug)) AND slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$');

ALTER TABLE workspace_pages
  ADD CONSTRAINT ck_workspace_page_title_nonblank CHECK (length(btrim(title)) > 0);

ALTER TABLE workspace_page_templates
  ADD CONSTRAINT ck_workspace_template_name_nonblank CHECK (length(btrim(name)) > 0),
  ADD CONSTRAINT ck_workspace_template_title_nonblank CHECK (length(btrim(title)) > 0);
