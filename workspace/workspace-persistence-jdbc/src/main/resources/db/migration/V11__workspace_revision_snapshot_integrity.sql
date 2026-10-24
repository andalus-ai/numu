-- A revision row must describe the current page state at the moment it is recorded.
-- Revision history is append-only: updates would silently rewrite historical snapshots.
CREATE OR REPLACE FUNCTION workspace_validate_page_revision_snapshot()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    current_revision BIGINT;
    current_title VARCHAR(500);
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'Workspace page revisions are immutable'
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
