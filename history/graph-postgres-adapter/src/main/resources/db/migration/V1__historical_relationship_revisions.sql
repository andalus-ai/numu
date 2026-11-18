-- Historical relationship assertions are append-only revision records.
-- tenant_id NULL denotes the shared public corpus; a UUID denotes tenant-private research.
CREATE TABLE historical_relationship_revision (
    relationship_id UUID NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 1),
    tenant_id UUID NULL,
    subject_kind VARCHAR(24) NOT NULL CHECK (subject_kind IN ('PERSON', 'COLLECTIVE', 'PLACE', 'EVENT')),
    subject_id UUID NOT NULL,
    object_kind VARCHAR(24) NOT NULL CHECK (object_kind IN ('PERSON', 'COLLECTIVE', 'PLACE', 'EVENT')),
    object_id UUID NOT NULL,
    relationship_kind VARCHAR(64) NOT NULL CHECK (relationship_kind ~ '^[a-z][a-z0-9._-]{1,63}$'),
    validity_calendar VARCHAR(64),
    valid_from_year BIGINT,
    valid_from_month SMALLINT,
    valid_from_day SMALLINT,
    valid_from_precision VARCHAR(8),
    valid_from_approximate BOOLEAN,
    valid_from_expression TEXT,
    valid_through_year BIGINT,
    valid_through_month SMALLINT,
    valid_through_day SMALLINT,
    valid_through_precision VARCHAR(8),
    valid_through_approximate BOOLEAN,
    valid_through_expression TEXT,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    recorded_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (relationship_id, revision),
    CHECK (subject_id <> object_id OR subject_kind <> object_kind),
    CHECK (updated_at >= recorded_at),
    CHECK (
        (validity_calendar IS NULL AND valid_from_year IS NULL AND valid_from_month IS NULL
         AND valid_from_day IS NULL AND valid_from_precision IS NULL AND valid_from_approximate IS NULL
         AND valid_through_year IS NULL AND valid_through_month IS NULL AND valid_through_day IS NULL
         AND valid_through_precision IS NULL AND valid_through_approximate IS NULL)
        OR
        (validity_calendar IS NOT NULL AND valid_from_year IS NOT NULL AND valid_from_month IS NOT NULL
         AND valid_from_day IS NOT NULL AND valid_from_precision IS NOT NULL AND valid_from_approximate IS NOT NULL
         AND valid_through_year IS NOT NULL AND valid_through_month IS NOT NULL AND valid_through_day IS NOT NULL
         AND valid_through_precision IS NOT NULL AND valid_through_approximate IS NOT NULL)
    ),
    CHECK (valid_from_precision IS NULL OR valid_from_precision IN ('YEAR', 'MONTH', 'DAY')),
    CHECK (valid_through_precision IS NULL OR valid_through_precision IN ('YEAR', 'MONTH', 'DAY')),
    CHECK (validity_calendar IS NULL OR validity_calendar ~ '^[a-z][a-z0-9._-]{1,63}$'),
    CHECK (valid_from_year IS NULL OR valid_from_year <> -9223372036854775808),
    CHECK (valid_through_year IS NULL OR valid_through_year <> -9223372036854775808),
    CHECK (valid_from_precision IS NULL OR (
        (valid_from_precision = 'YEAR' AND valid_from_month = 0 AND valid_from_day = 0)
        OR (valid_from_precision = 'MONTH' AND valid_from_month BETWEEN 1 AND 13 AND valid_from_day = 0)
        OR (valid_from_precision = 'DAY' AND valid_from_month BETWEEN 1 AND 13 AND valid_from_day BETWEEN 1 AND 31)
    )),
    CHECK (valid_through_precision IS NULL OR (
        (valid_through_precision = 'YEAR' AND valid_through_month = 0 AND valid_through_day = 0)
        OR (valid_through_precision = 'MONTH' AND valid_through_month BETWEEN 1 AND 13 AND valid_through_day = 0)
        OR (valid_through_precision = 'DAY' AND valid_through_month BETWEEN 1 AND 13 AND valid_through_day BETWEEN 1 AND 31)
    )),
    CHECK (validity_calendar IS NULL OR
        (valid_from_year, valid_from_month, valid_from_day)
        <= (valid_through_year, valid_through_month, valid_through_day))
);

CREATE INDEX historical_relationship_id_revision_idx
    ON historical_relationship_revision (relationship_id, revision DESC);
CREATE INDEX historical_relationship_subject_latest_idx
    ON historical_relationship_revision (subject_kind, subject_id, relationship_id, revision DESC);
CREATE INDEX historical_relationship_object_latest_idx
    ON historical_relationship_revision (object_kind, object_id, relationship_id, revision DESC);
CREATE INDEX historical_relationship_subject_idx
    ON historical_relationship_revision (tenant_id, subject_kind, subject_id, relationship_id, revision DESC);
CREATE INDEX historical_relationship_object_idx
    ON historical_relationship_revision (tenant_id, object_kind, object_id, relationship_id, revision DESC);
CREATE INDEX historical_relationship_kind_idx
    ON historical_relationship_revision (tenant_id, relationship_kind, relationship_id, revision DESC);

-- Enforce monotonic append and immutable scope even for callers bypassing the SPI.
-- RLS limits this check to public plus current-tenant rows.
CREATE FUNCTION enforce_historical_relationship_revision_sequence()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    current_revision BIGINT;
    same_scope BOOLEAN;
    original_recorded_at TIMESTAMPTZ;
    previous_updated_at TIMESTAMPTZ;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended(NEW.relationship_id::text, 0));
    SELECT COALESCE(MAX(revision), 0),
           COALESCE(bool_and(tenant_id IS NOT DISTINCT FROM NEW.tenant_id), TRUE),
           MIN(recorded_at),
           MAX(updated_at)
      INTO current_revision, same_scope, original_recorded_at, previous_updated_at
      FROM historical_relationship_revision
     WHERE relationship_id = NEW.relationship_id;

    IF NOT same_scope THEN
        RAISE EXCEPTION 'historical relationship scope cannot change';
    END IF;
    IF current_revision > 0 AND NEW.recorded_at IS DISTINCT FROM original_recorded_at THEN
        RAISE EXCEPTION 'historical relationship recorded_at is immutable';
    END IF;
    IF current_revision > 0 AND NEW.updated_at < previous_updated_at THEN
        RAISE EXCEPTION 'historical relationship updated_at cannot move backwards';
    END IF;
    IF NEW.revision <> current_revision + 1 THEN
        RAISE EXCEPTION 'historical relationship revisions must be appended sequentially';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER historical_relationship_revision_sequence
    BEFORE INSERT ON historical_relationship_revision
    FOR EACH ROW EXECUTE FUNCTION enforce_historical_relationship_revision_sequence();

-- The authenticated application role can read public corpus rows and its own tenant rows.
-- It can insert only tenant-private research. Public corpus writes are reserved for ingestion roles.
ALTER TABLE historical_relationship_revision ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_relationship_revision FORCE ROW LEVEL SECURITY;
CREATE POLICY historical_relationship_read_scope
    ON historical_relationship_revision FOR SELECT
    USING (tenant_id IS NULL OR tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid);
CREATE POLICY historical_relationship_insert_scope
    ON historical_relationship_revision FOR INSERT
    WITH CHECK (tenant_id = nullif(current_setting('numou.tenant_id', true), '')::uuid);

CREATE FUNCTION reject_historical_relationship_revision_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'historical relationship revisions are append-only';
END;
$$;

CREATE TRIGGER historical_relationship_revision_immutable
    BEFORE UPDATE OR DELETE ON historical_relationship_revision
    FOR EACH ROW EXECUTE FUNCTION reject_historical_relationship_revision_mutation();
CREATE TRIGGER historical_relationship_revision_no_truncate
    BEFORE TRUNCATE ON historical_relationship_revision
    FOR EACH STATEMENT EXECUTE FUNCTION reject_historical_relationship_revision_mutation();
