-- M26: preserve operational replay history without deleting failed delivery rows.
ALTER TABLE courier_event_publications
    ADD COLUMN replay_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_replayed_at TIMESTAMPTZ,
    ADD COLUMN last_replayed_by VARCHAR(200);

ALTER TABLE courier_event_publications
    ADD CONSTRAINT ck_courier_publication_replay_count CHECK (replay_count >= 0),
    ADD CONSTRAINT ck_courier_publication_replay_actor CHECK (
        (last_replayed_at IS NULL AND last_replayed_by IS NULL)
        OR (last_replayed_at IS NOT NULL AND length(trim(last_replayed_by)) BETWEEN 1 AND 200)
    );

CREATE TABLE courier_event_publication_audit (
    audit_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    event_id UUID NOT NULL,
    destination VARCHAR(120) NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_reference VARCHAR(200) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    previous_status VARCHAR(16) NOT NULL,
    previous_attempt_count INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_courier_publication_audit_action CHECK (action IN ('REPLAY_REQUESTED')),
    CONSTRAINT ck_courier_publication_audit_actor CHECK (length(trim(actor_reference)) BETWEEN 1 AND 200),
    CONSTRAINT ck_courier_publication_audit_reason CHECK (length(trim(reason)) BETWEEN 1 AND 1000),
    CONSTRAINT ck_courier_publication_audit_attempts CHECK (previous_attempt_count >= 0),
    FOREIGN KEY (tenant_id, event_id, destination)
        REFERENCES courier_event_publications (tenant_id, event_id, destination)
);

CREATE INDEX ix_courier_publication_audit_history
    ON courier_event_publication_audit (tenant_id, event_id, destination, occurred_at DESC);

CREATE FUNCTION prevent_courier_publication_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'courier publication audit is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_courier_publication_audit_immutable
    BEFORE UPDATE OR DELETE ON courier_event_publication_audit
    FOR EACH ROW EXECUTE FUNCTION prevent_courier_publication_audit_mutation();
