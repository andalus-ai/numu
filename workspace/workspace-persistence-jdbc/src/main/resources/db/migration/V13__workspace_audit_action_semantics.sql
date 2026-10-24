-- Align persisted audit metadata with the mutations emitted by Workspace M2 services.
-- Unknown/custom actions remain available to host applications; known workspace actions
-- cannot be repurposed to describe a different resource or revision.
ALTER TABLE workspace_audit_events
  ADD CONSTRAINT ck_workspace_audit_known_action_semantics CHECK (
    CASE action
      WHEN 'WORKSPACE_CREATED' THEN resource_type = 'WORKSPACE' AND revision = 0
      WHEN 'SPACE_CREATED' THEN resource_type = 'SPACE' AND revision = 0
      WHEN 'PAGE_CREATED' THEN resource_type = 'PAGE' AND revision = 1
      WHEN 'PAGE_UPDATED' THEN resource_type = 'PAGE' AND revision >= 2
      WHEN 'PAGE_TEMPLATE_CREATED' THEN resource_type = 'PAGE_TEMPLATE' AND revision = 1
      WHEN 'PAGE_LINK_CREATED' THEN resource_type = 'PAGE_LINK' AND revision = 0
      WHEN 'PAGE_LINK_REMOVED' THEN resource_type = 'PAGE_LINK' AND revision = 0
      ELSE TRUE
    END
  );
