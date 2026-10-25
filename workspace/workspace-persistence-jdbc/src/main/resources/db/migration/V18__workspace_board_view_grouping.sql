-- Persist the grouping key as part of the saved BOARD view rather than each query request.
ALTER TABLE workspace_database_views
  ADD COLUMN group_by_property VARCHAR(63);

ALTER TABLE workspace_database_views
  ADD CONSTRAINT ck_workspace_database_view_group_by_property
  CHECK (
    group_by_property IS NULL
    OR (view_type = 'BOARD' AND group_by_property ~ '^[a-z][a-z0-9_]{0,62}$')
  );
