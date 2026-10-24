-- Revision block rows are append-only snapshots, not mutable live content.
-- Only blocks matching the current live page may be inserted into its current revision.
CREATE OR REPLACE FUNCTION workspace_guard_page_revision_blocks()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    current_revision BIGINT;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'Workspace page revision blocks are immutable'
            USING ERRCODE = '23514';
    END IF;

    -- Allow foreign-key cascades when a page (or revision header) is deleted,
    -- while rejecting direct deletion of retained history.
    IF TG_OP = 'DELETE' THEN
        IF pg_trigger_depth() > 1 THEN
            RETURN OLD;
        END IF;
        RAISE EXCEPTION 'Workspace page revision blocks cannot be deleted directly'
            USING ERRCODE = '23514';
    END IF;

    SELECT p.revision
      INTO current_revision
      FROM workspace_pages p
     WHERE p.tenant_id = NEW.tenant_id
       AND p.id = NEW.page_id
     FOR UPDATE;

    IF NOT FOUND OR NEW.revision IS DISTINCT FROM current_revision THEN
        RAISE EXCEPTION
            'Revision blocks may only be added to the current page revision'
            USING ERRCODE = '23514';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM workspace_page_blocks b
         WHERE b.tenant_id = NEW.tenant_id
           AND b.page_id = NEW.page_id
           AND b.block_id = NEW.block_id
           AND b.block_type = NEW.block_type
           AND b.text_content = NEW.text_content
           AND b.position = NEW.position
           AND b.attributes_encoded = NEW.attributes_encoded
    ) THEN
        RAISE EXCEPTION
            'Revision block does not match the current page block snapshot'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_workspace_page_revision_blocks_immutable
BEFORE INSERT OR UPDATE OR DELETE
ON workspace_page_revision_blocks
FOR EACH ROW
EXECUTE FUNCTION workspace_guard_page_revision_blocks();
