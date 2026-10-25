-- Persist a monotonic revision for compare-and-swap saved-view edits.
ALTER TABLE workspace_database_views
  ADD COLUMN revision BIGINT NOT NULL DEFAULT 1;

ALTER TABLE workspace_database_views
  ADD CONSTRAINT ck_workspace_database_view_revision_positive
  CHECK (revision > 0);
