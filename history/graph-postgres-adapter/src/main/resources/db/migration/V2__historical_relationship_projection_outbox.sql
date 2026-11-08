-- Durable graph-projection events are created in the same transaction as each
-- relationship revision. A committed revision can therefore never be invisible
-- to downstream projection workers.
CREATE TABLE historical_relationship_projection_outbox (
    event_id UUID PRIMARY KEY,
    event_key VARCHAR(160) NOT NULL UNIQUE,
    event_type VARCHAR(96) NOT NULL,
    schema_version SMALLINT NOT NULL CHECK (schema_version >= 1),
    tenant_id UUID,
    aggregate_id UUID NOT NULL,
    aggregate_revision BIGINT NOT NULL CHECK (aggregate_revision >= 1),
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    occurred_at TIMESTAMPTZ NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    lease_owner VARCHAR(160),
    lease_until TIMESTAMPTZ,
    last_error VARCHAR(2000),
    published_at TIMESTAMPTZ,
    dead_lettered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK ((lease_owner IS NULL) = (lease_until IS NULL)),
    CHECK (published_at IS NULL OR dead_lettered_at IS NULL),
    UNIQUE (aggregate_id, aggregate_revision)
);

CREATE INDEX historical_relationship_projection_outbox_ready_idx
    ON historical_relationship_projection_outbox (available_at, created_at, event_id)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;

CREATE INDEX historical_relationship_projection_outbox_lease_idx
    ON historical_relationship_projection_outbox (lease_until)
    WHERE lease_owner IS NOT NULL AND published_at IS NULL AND dead_lettered_at IS NULL;

CREATE INDEX historical_relationship_projection_outbox_aggregate_idx
    ON historical_relationship_projection_outbox (aggregate_id, aggregate_revision);

-- Only delivery metadata may be changed after insertion. The event identity,
-- scope, version and payload are immutable and remain auditable.
CREATE FUNCTION protect_historical_relationship_projection_outbox()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'historical relationship projection events cannot be deleted';
    END IF;
    IF NEW.event_id IS DISTINCT FROM OLD.event_id
       OR NEW.event_key IS DISTINCT FROM OLD.event_key
       OR NEW.event_type IS DISTINCT FROM OLD.event_type
       OR NEW.schema_version IS DISTINCT FROM OLD.schema_version
       OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
       OR NEW.aggregate_id IS DISTINCT FROM OLD.aggregate_id
       OR NEW.aggregate_revision IS DISTINCT FROM OLD.aggregate_revision
       OR NEW.payload IS DISTINCT FROM OLD.payload
       OR NEW.occurred_at IS DISTINCT FROM OLD.occurred_at
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'historical relationship projection event content is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER historical_relationship_projection_outbox_immutable
    BEFORE UPDATE OR DELETE ON historical_relationship_projection_outbox
    FOR EACH ROW EXECUTE FUNCTION protect_historical_relationship_projection_outbox();

-- The trigger executes in the same PostgreSQL transaction as the revision insert.
-- Its deterministic event key makes replay and duplicate-trigger protection explicit.
CREATE FUNCTION enqueue_historical_relationship_projection_event()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public AS $$
BEGIN
    INSERT INTO historical_relationship_projection_outbox (
        event_id, event_key, event_type, schema_version, tenant_id,
        aggregate_id, aggregate_revision, payload, occurred_at, available_at
    ) VALUES (
        gen_random_uuid(),
        'historical-relationship:' || NEW.relationship_id::text || ':' || NEW.revision::text,
        'history.relationship.revision-appended',
        1,
        NEW.tenant_id,
        NEW.relationship_id,
        NEW.revision,
        jsonb_build_object(
            'relationshipId', NEW.relationship_id,
            'revision', NEW.revision,
            'tenantId', NEW.tenant_id,
            'subject', jsonb_build_object('kind', NEW.subject_kind, 'id', NEW.subject_id),
            'object', jsonb_build_object('kind', NEW.object_kind, 'id', NEW.object_id),
            'relationshipKind', NEW.relationship_kind,
            'description', NEW.description,
            'validity', CASE WHEN NEW.validity_calendar IS NULL THEN NULL ELSE jsonb_build_object(
                'calendar', NEW.validity_calendar,
                'from', jsonb_build_object(
                    'year', NEW.valid_from_year, 'month', NEW.valid_from_month, 'day', NEW.valid_from_day,
                    'precision', NEW.valid_from_precision, 'approximate', NEW.valid_from_approximate,
                    'expression', NEW.valid_from_expression
                ),
                'through', jsonb_build_object(
                    'year', NEW.valid_through_year, 'month', NEW.valid_through_month, 'day', NEW.valid_through_day,
                    'precision', NEW.valid_through_precision, 'approximate', NEW.valid_through_approximate,
                    'expression', NEW.valid_through_expression
                )
            ) END
        ),
        NEW.updated_at,
        clock_timestamp()
    );
    RETURN NEW;
END;
$$;

CREATE TRIGGER historical_relationship_revision_projection_outbox
    AFTER INSERT ON historical_relationship_revision
    FOR EACH ROW EXECUTE FUNCTION enqueue_historical_relationship_projection_event();

