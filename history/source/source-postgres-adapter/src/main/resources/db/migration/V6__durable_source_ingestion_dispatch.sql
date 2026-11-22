CREATE TABLE historical_source_ingestion_dispatch (
    tenant_id uuid NOT NULL,
    ingestion_id uuid NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','LEASED','COMPLETED','DEAD_LETTERED')),
    available_at timestamptz NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    worker_ref varchar(256),
    lease_token uuid,
    lease_until timestamptz,
    completed_at timestamptz,
    last_error varchar(2000),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, ingestion_id),
    FOREIGN KEY (tenant_id, ingestion_id)
        REFERENCES historical_source_ingestion(tenant_id, ingestion_id),
    CHECK (
        (status = 'LEASED' AND worker_ref IS NOT NULL AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR
        (status <> 'LEASED' AND worker_ref IS NULL AND lease_token IS NULL AND lease_until IS NULL)
    ),
    CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);

CREATE INDEX historical_source_ingestion_dispatch_ready_idx
    ON historical_source_ingestion_dispatch(tenant_id, available_at, created_at, ingestion_id)
    WHERE status = 'PENDING';

CREATE INDEX historical_source_ingestion_dispatch_expired_idx
    ON historical_source_ingestion_dispatch(tenant_id, lease_until, created_at, ingestion_id)
    WHERE status = 'LEASED';

CREATE TABLE historical_source_ingestion_dispatch_event (
    tenant_id uuid NOT NULL,
    event_id uuid NOT NULL,
    ingestion_id uuid NOT NULL,
    event_type varchar(32) NOT NULL
        CHECK (event_type IN ('ENQUEUED','CLAIMED','COMPLETED','RETRY_SCHEDULED','DEAD_LETTERED')),
    attempt integer NOT NULL CHECK (attempt >= 0),
    worker_ref varchar(256),
    lease_token uuid,
    occurred_at timestamptz NOT NULL,
    error varchar(2000),
    PRIMARY KEY (tenant_id, event_id),
    FOREIGN KEY (tenant_id, ingestion_id)
        REFERENCES historical_source_ingestion(tenant_id, ingestion_id)
);

CREATE INDEX historical_source_ingestion_dispatch_event_idx
    ON historical_source_ingestion_dispatch_event(tenant_id, ingestion_id, occurred_at, event_id);

-- Existing ingestion requests predate the dispatch table. Queue only those
-- that do not already have a recorded extraction result.
INSERT INTO historical_source_ingestion_dispatch (tenant_id, ingestion_id, available_at)
SELECT i.tenant_id, i.ingestion_id, i.requested_at
FROM historical_source_ingestion i
WHERE NOT EXISTS (
    SELECT 1 FROM historical_source_extraction e
    WHERE e.tenant_id = i.tenant_id AND e.ingestion_id = i.ingestion_id
)
ON CONFLICT (tenant_id, ingestion_id) DO NOTHING;

INSERT INTO historical_source_ingestion_dispatch_event
    (tenant_id, event_id, ingestion_id, event_type, attempt, occurred_at)
SELECT d.tenant_id, gen_random_uuid(), d.ingestion_id, 'ENQUEUED', 0, i.requested_at
FROM historical_source_ingestion_dispatch d
JOIN historical_source_ingestion i
  ON i.tenant_id = d.tenant_id AND i.ingestion_id = d.ingestion_id
WHERE NOT EXISTS (
    SELECT 1 FROM historical_source_ingestion_dispatch_event e
    WHERE e.tenant_id = d.tenant_id AND e.ingestion_id = d.ingestion_id
)
ON CONFLICT (tenant_id, event_id) DO NOTHING;

CREATE FUNCTION numou_history_enqueue_source_ingestion()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO historical_source_ingestion_dispatch (tenant_id, ingestion_id, available_at)
    VALUES (NEW.tenant_id, NEW.ingestion_id, NEW.requested_at);

    INSERT INTO historical_source_ingestion_dispatch_event
        (tenant_id, event_id, ingestion_id, event_type, attempt, occurred_at)
    VALUES (NEW.tenant_id, gen_random_uuid(), NEW.ingestion_id, 'ENQUEUED', 0, NEW.requested_at);
    RETURN NEW;
END;
$$;

CREATE TRIGGER historical_source_ingestion_enqueue
    AFTER INSERT ON historical_source_ingestion
    FOR EACH ROW EXECUTE FUNCTION numou_history_enqueue_source_ingestion();

CREATE TRIGGER historical_source_ingestion_dispatch_event_append_only
    BEFORE UPDATE OR DELETE ON historical_source_ingestion_dispatch_event
    FOR EACH ROW EXECUTE FUNCTION numou_history_reject_immutable_mutation();

ALTER TABLE historical_source_ingestion_dispatch ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_source_ingestion_dispatch FORCE ROW LEVEL SECURITY;
CREATE POLICY historical_source_ingestion_dispatch_tenant_policy
    ON historical_source_ingestion_dispatch
    USING (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid);

ALTER TABLE historical_source_ingestion_dispatch_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE historical_source_ingestion_dispatch_event FORCE ROW LEVEL SECURITY;
CREATE POLICY historical_source_ingestion_dispatch_event_tenant_policy
    ON historical_source_ingestion_dispatch_event
    USING (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('numou.tenant_id', true), '')::uuid);
