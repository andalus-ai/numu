-- Known audit actions must refer to an existing tenant-owned resource. Page audit actors
-- must also match the immutable revision snapshot, even for direct SQL writers.
CREATE OR REPLACE FUNCTION workspace_validate_audit_resource_integrity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    revision_actor UUID;
BEGIN
    CASE NEW.action
      WHEN 'WORKSPACE_CREATED' THEN
        IF NOT EXISTS (SELECT 1 FROM workspace_workspaces w WHERE w.tenant_id = NEW.tenant_id AND w.id = NEW.resource_id) THEN
          RAISE EXCEPTION 'Workspace audit event references a missing or cross-tenant workspace'
            USING ERRCODE = '23503';
        END IF;
      WHEN 'SPACE_CREATED' THEN
        IF NOT EXISTS (SELECT 1 FROM workspace_spaces s WHERE s.tenant_id = NEW.tenant_id AND s.id = NEW.resource_id) THEN
          RAISE EXCEPTION 'Space audit event references a missing or cross-tenant space'
            USING ERRCODE = '23503';
        END IF;
      WHEN 'PAGE_CREATED', 'PAGE_UPDATED' THEN
        SELECT r.actor_id INTO revision_actor
          FROM workspace_page_revisions r
         WHERE r.tenant_id = NEW.tenant_id
           AND r.page_id = NEW.resource_id
           AND r.revision = NEW.revision;
        IF NOT FOUND THEN
          RAISE EXCEPTION 'Page audit event references a missing or cross-tenant revision'
            USING ERRCODE = '23503';
        END IF;
        IF revision_actor IS DISTINCT FROM NEW.actor_id THEN
          RAISE EXCEPTION 'Page audit actor does not match the revision actor'
            USING ERRCODE = '23514';
        END IF;
      WHEN 'PAGE_TEMPLATE_CREATED' THEN
        IF NOT EXISTS (SELECT 1 FROM workspace_page_templates t WHERE t.tenant_id = NEW.tenant_id AND t.id = NEW.resource_id AND t.created_by = NEW.actor_id) THEN
          RAISE EXCEPTION 'Template audit event does not match the tenant-owned template creator'
            USING ERRCODE = '23514';
        END IF;
      WHEN 'PAGE_LINK_CREATED' THEN
        IF NOT EXISTS (SELECT 1 FROM workspace_page_links l WHERE l.tenant_id = NEW.tenant_id AND l.id = NEW.resource_id AND l.created_by = NEW.actor_id) THEN
          RAISE EXCEPTION 'Page-link audit event does not match the tenant-owned link creator'
            USING ERRCODE = '23514';
        END IF;
      ELSE
        -- PAGE_LINK_REMOVED intentionally references a link that no longer exists.
        NULL;
    END CASE;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_workspace_audit_resource_integrity
BEFORE INSERT ON workspace_audit_events
FOR EACH ROW
EXECUTE FUNCTION workspace_validate_audit_resource_integrity();
