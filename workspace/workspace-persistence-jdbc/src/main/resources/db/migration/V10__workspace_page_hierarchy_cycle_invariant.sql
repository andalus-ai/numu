-- Prevent multi-page cycles even when hierarchy changes bypass the application API.
-- Serialize parent changes within a space so concurrent updates cannot each validate
-- against the other's pre-update hierarchy and jointly create a cycle.
CREATE OR REPLACE FUNCTION workspace_prevent_page_hierarchy_cycles()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    creates_cycle BOOLEAN;
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.parent_page_id IS NOT DISTINCT FROM OLD.parent_page_id THEN
        RETURN NEW;
    END IF;

    IF NEW.parent_page_id IS NULL THEN
        RETURN NEW;
    END IF;

    PERFORM pg_advisory_xact_lock(
        hashtextextended(NEW.tenant_id::text || ':' || NEW.space_id::text, 0)
    );

    WITH RECURSIVE ancestors(id, parent_page_id) AS (
        SELECT p.id, p.parent_page_id
        FROM workspace_pages p
        WHERE p.tenant_id = NEW.tenant_id
          AND p.space_id = NEW.space_id
          AND p.id = NEW.parent_page_id
        UNION
        SELECT p.id, p.parent_page_id
        FROM workspace_pages p
        JOIN ancestors a ON p.id = a.parent_page_id
        WHERE p.tenant_id = NEW.tenant_id
          AND p.space_id = NEW.space_id
    )
    SELECT EXISTS (SELECT 1 FROM ancestors WHERE id = NEW.id)
    INTO creates_cycle;

    IF creates_cycle THEN
        RAISE EXCEPTION 'Page hierarchy cycle detected for page %', NEW.id
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_workspace_page_hierarchy_acyclic
BEFORE INSERT OR UPDATE
ON workspace_pages
FOR EACH ROW
EXECUTE FUNCTION workspace_prevent_page_hierarchy_cycles();
