-- Keep audit metadata valid even when writes bypass the application adapter.
-- This migration deliberately fails if existing rows violate the constraints.
ALTER TABLE workspace_audit_events
  ADD CONSTRAINT ck_workspace_audit_action_nonblank CHECK (length(btrim(action)) > 0),
  ADD CONSTRAINT ck_workspace_audit_resource_type_nonblank CHECK (length(btrim(resource_type)) > 0),
  ADD CONSTRAINT ck_workspace_audit_revision_nonnegative CHECK (revision >= 0);
