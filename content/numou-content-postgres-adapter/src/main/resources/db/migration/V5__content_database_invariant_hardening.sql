-- M4: enforce content history, schema immutability and lifecycle rules at the database boundary.

-- Every content head must point at an existing revision. Deferred because creation and
-- revision append intentionally update the head and insert the immutable revision atomically.
ALTER TABLE content_entry
    ADD CONSTRAINT fk_content_entry_current_revision
    FOREIGN KEY (tenant_id, content_id, current_revision)
    REFERENCES content_revision (tenant_id, content_id, revision_number)
    DEFERRABLE INITIALLY DEFERRED;

CREATE OR REPLACE FUNCTION content_guard_entry_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.current_revision IS DISTINCT FROM OLD.current_revision
       AND NEW.current_revision <> OLD.current_revision + 1 THEN
        RAISE EXCEPTION 'content revision pointer must advance exactly one revision'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
        (OLD.status = 'DRAFT' AND NEW.status IN ('IN_REVIEW', 'ARCHIVED')) OR
        (OLD.status = 'IN_REVIEW' AND NEW.status IN ('DRAFT', 'APPROVED', 'ARCHIVED')) OR
        (OLD.status = 'APPROVED' AND NEW.status IN ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'ARCHIVED')) OR
        (OLD.status = 'SCHEDULED' AND NEW.status IN ('APPROVED', 'PUBLISHED', 'ARCHIVED')) OR
        (OLD.status = 'PUBLISHED' AND NEW.status IN ('UNPUBLISHED', 'ARCHIVED')) OR
        (OLD.status = 'UNPUBLISHED' AND NEW.status IN ('DRAFT', 'IN_REVIEW', 'ARCHIVED')) OR
        (OLD.status = 'ARCHIVED' AND NEW.status = 'DRAFT')
    ) THEN
        RAISE EXCEPTION 'invalid content lifecycle transition: % -> %', OLD.status, NEW.status
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_content_entry_guard_update
BEFORE UPDATE ON content_entry
FOR EACH ROW EXECUTE FUNCTION content_guard_entry_update();

CREATE OR REPLACE FUNCTION content_guard_entry_insert()
RETURNS trigger
LANGUAGE plpgsql
AS $
BEGIN
    IF NEW.status <> 'DRAFT' OR NEW.current_revision <> 1 THEN
        RAISE EXCEPTION 'new content entries must begin as draft at revision one'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$;

CREATE TRIGGER trg_content_entry_initial_state
BEFORE INSERT ON content_entry
FOR EACH ROW EXECUTE FUNCTION content_guard_entry_insert();

CREATE OR REPLACE FUNCTION content_guard_revision_insert()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    expected_revision INTEGER;
    previous_exists BOOLEAN;
    schema_status VARCHAR(16);
BEGIN
    SELECT current_revision INTO expected_revision
      FROM content_entry
     WHERE tenant_id = NEW.tenant_id AND content_id = NEW.content_id
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'content entry does not exist for revision'
            USING ERRCODE = '23503';
    END IF;

    IF NEW.content_type_id IS NULL OR NEW.schema_version IS NULL THEN
        RAISE EXCEPTION 'new content revisions must reference a published schema version'
            USING ERRCODE = '23514';
    END IF;

    SELECT status INTO schema_status
      FROM content_type_schema
     WHERE tenant_id = NEW.tenant_id
       AND content_type_id = NEW.content_type_id
       AND content_type_schema.schema_version = NEW.schema_version;
    IF NOT FOUND OR schema_status <> 'PUBLISHED' THEN
        RAISE EXCEPTION 'content revisions must reference an existing published schema version'
            USING ERRCODE = '23503';
    END IF;

    IF NEW.revision_number <> expected_revision THEN
        RAISE EXCEPTION 'revision number must match the content head pointer'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.revision_number = 1 THEN
        IF EXISTS (SELECT 1 FROM content_revision
                    WHERE tenant_id = NEW.tenant_id AND content_id = NEW.content_id) THEN
            RAISE EXCEPTION 'initial content revision already exists'
                USING ERRCODE = '23505';
        END IF;
    ELSE
        SELECT EXISTS (SELECT 1 FROM content_revision
                        WHERE tenant_id = NEW.tenant_id AND content_id = NEW.content_id
                          AND revision_number = NEW.revision_number - 1)
          INTO previous_exists;
        IF NOT previous_exists THEN
            RAISE EXCEPTION 'content revisions must be appended contiguously'
                USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_content_revision_insert_guard
BEFORE INSERT ON content_revision
FOR EACH ROW EXECUTE FUNCTION content_guard_revision_insert();

CREATE OR REPLACE FUNCTION content_reject_revision_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'content revisions are append-only'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_content_revision_immutable
BEFORE UPDATE OR DELETE ON content_revision
FOR EACH ROW EXECUTE FUNCTION content_reject_revision_mutation();

-- A published schema version is a permanent contract. Only a one-way DRAFT -> PUBLISHED
-- transition is permitted on its schema row; field and option rows then become immutable.
CREATE OR REPLACE FUNCTION content_guard_schema_version()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'DRAFT' OR NEW.published_at IS NOT NULL THEN
            RAISE EXCEPTION 'new content schema versions must begin as draft'
                USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;

    IF TG_OP = 'DELETE' THEN
        IF OLD.status = 'PUBLISHED' THEN
            RAISE EXCEPTION 'published content schema versions cannot be deleted'
                USING ERRCODE = '55000';
        END IF;
        RETURN OLD;
    END IF;

    IF OLD.status <> 'DRAFT'
       OR NEW.status <> 'PUBLISHED'
       OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
       OR NEW.content_type_id IS DISTINCT FROM OLD.content_type_id
       OR NEW.schema_version IS DISTINCT FROM OLD.schema_version
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR NEW.published_at IS NULL THEN
        RAISE EXCEPTION 'content schema versions are immutable except for draft publication'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_content_schema_version_guard
BEFORE INSERT OR UPDATE OR DELETE ON content_type_schema
FOR EACH ROW EXECUTE FUNCTION content_guard_schema_version();

CREATE OR REPLACE FUNCTION content_guard_schema_child()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    old_status VARCHAR(16);
    new_status VARCHAR(16);
BEGIN
    IF TG_OP <> 'INSERT' THEN
        SELECT status INTO old_status
          FROM content_type_schema
         WHERE tenant_id = OLD.tenant_id
           AND content_type_id = OLD.content_type_id
           AND content_type_schema.schema_version = OLD.schema_version;
        IF old_status = 'PUBLISHED' THEN
            RAISE EXCEPTION 'fields and options of a published schema are immutable'
                USING ERRCODE = '55000';
        END IF;
    END IF;

    IF TG_OP <> 'DELETE' THEN
        SELECT status INTO new_status
          FROM content_type_schema
         WHERE tenant_id = NEW.tenant_id
           AND content_type_id = NEW.content_type_id
           AND content_type_schema.schema_version = NEW.schema_version;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'content schema version does not exist'
                USING ERRCODE = '23503';
        END IF;
        IF new_status = 'PUBLISHED' THEN
            RAISE EXCEPTION 'fields and options of a published schema are immutable'
                USING ERRCODE = '55000';
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_content_type_field_immutable_after_publish
BEFORE INSERT OR UPDATE OR DELETE ON content_type_field
FOR EACH ROW EXECUTE FUNCTION content_guard_schema_child();

CREATE TRIGGER trg_content_type_field_option_immutable_after_publish
BEFORE INSERT OR UPDATE OR DELETE ON content_type_field_option
FOR EACH ROW EXECUTE FUNCTION content_guard_schema_child();
