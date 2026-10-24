-- Revision headers are append-only and must describe the current page when recorded.
-- Deferred checks ensure a page and its live blocks match the current revision at commit,
-- so direct SQL cannot silently move the page ahead of its immutable history.
CREATE OR REPLACE FUNCTION workspace_validate_page_revision_snapshot()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    current_revision BIGINT;
    current_title VARCHAR(500);
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'Workspace page revision headers are immutable'
            USING ERRCODE = '23514';
    END IF;

    SELECT p.revision, p.title
      INTO current_revision, current_title
      FROM workspace_pages p
     WHERE p.tenant_id = NEW.tenant_id
       AND p.id = NEW.page_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Workspace page revision references a missing page'
            USING ERRCODE = '23503';
    END IF;

    IF NEW.revision IS DISTINCT FROM current_revision
       OR NEW.title IS DISTINCT FROM current_title THEN
        RAISE EXCEPTION
            'Workspace page revision snapshot does not match current page state for page %',
            NEW.page_id
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_workspace_page_revision_snapshot_integrity
BEFORE INSERT OR UPDATE
ON workspace_page_revisions
FOR EACH ROW
EXECUTE FUNCTION workspace_validate_page_revision_snapshot();

CREATE OR REPLACE FUNCTION workspace_assert_current_page_revision_snapshot()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    target_tenant UUID;
    target_page UUID;
    current_revision BIGINT;
    current_title VARCHAR(500);
BEGIN
    IF TG_TABLE_NAME = 'workspace_pages' THEN
        target_tenant := NEW.tenant_id;
        target_page := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN
        target_tenant := OLD.tenant_id;
        target_page := OLD.page_id;
    ELSE
        target_tenant := NEW.tenant_id;
        target_page := NEW.page_id;
    END IF;

    SELECT p.revision, p.title
      INTO current_revision, current_title
      FROM workspace_pages p
     WHERE p.tenant_id = target_tenant
       AND p.id = target_page;

    -- Page deletion cascades to its blocks and revisions; no live snapshot remains to check.
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM workspace_page_revisions r
         WHERE r.tenant_id = target_tenant
           AND r.page_id = target_page
           AND r.revision = current_revision
           AND r.title = current_title
    ) THEN
        RAISE EXCEPTION
            'Current page state has no matching revision snapshot for page %',
            target_page
            USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM workspace_page_blocks b
         WHERE b.tenant_id = target_tenant
           AND b.page_id = target_page
           AND NOT EXISTS (
               SELECT 1
                 FROM workspace_page_revision_blocks rb
                WHERE rb.tenant_id = b.tenant_id
                  AND rb.page_id = b.page_id
                  AND rb.revision = current_revision
                  AND rb.block_id = b.block_id
                  AND rb.block_type = b.block_type
                  AND rb.text_content = b.text_content
                  AND rb.position = b.position
                  AND rb.attributes_encoded = b.attributes_encoded
           )
    ) OR EXISTS (
        SELECT 1
          FROM workspace_page_revision_blocks rb
         WHERE rb.tenant_id = target_tenant
           AND rb.page_id = target_page
           AND rb.revision = current_revision
           AND NOT EXISTS (
               SELECT 1
                 FROM workspace_page_blocks b
                WHERE b.tenant_id = rb.tenant_id
                  AND b.page_id = rb.page_id
                  AND b.block_id = rb.block_id
                  AND b.block_type = rb.block_type
                  AND b.text_content = rb.text_content
                  AND b.position = rb.position
                  AND b.attributes_encoded = rb.attributes_encoded
           )
    ) THEN
        RAISE EXCEPTION
            'Current page blocks do not match the revision snapshot for page %',
            target_page
            USING ERRCODE = '23514';
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_workspace_page_current_revision_snapshot
AFTER INSERT OR UPDATE
ON workspace_pages
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION workspace_assert_current_page_revision_snapshot();

CREATE CONSTRAINT TRIGGER trg_workspace_page_blocks_current_revision_snapshot
AFTER INSERT OR UPDATE OR DELETE
ON workspace_page_blocks
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION workspace_assert_current_page_revision_snapshot();
