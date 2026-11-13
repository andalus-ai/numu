-- Lease fencing and replay accounting are mutable delivery metadata; event content remains immutable.
ALTER TABLE historical_relationship_projection_outbox ADD COLUMN lease_token UUID,
 ADD COLUMN retry_attempts INTEGER NOT NULL DEFAULT 0 CHECK (retry_attempts >= 0);
UPDATE historical_relationship_projection_outbox SET lease_token=gen_random_uuid() WHERE lease_owner IS NOT NULL;
ALTER TABLE historical_relationship_projection_outbox ADD CONSTRAINT historical_relationship_projection_outbox_lease_token_check CHECK ((lease_owner IS NULL)=(lease_token IS NULL));
CREATE TABLE historical_relationship_projection_replay_audit (
 replay_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 event_id UUID NOT NULL REFERENCES historical_relationship_projection_outbox(event_id),
 actor_id UUID NOT NULL,
 lifetime_attempts INTEGER NOT NULL CHECK(lifetime_attempts>=0),
 replayed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX historical_relationship_projection_dead_idx ON historical_relationship_projection_outbox(dead_lettered_at,created_at,event_id) WHERE dead_lettered_at IS NOT NULL;
CREATE FUNCTION protect_historical_relationship_projection_replay_audit()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'historical relationship projection replay audit is append-only';
END;
$$;
CREATE TRIGGER historical_relationship_projection_replay_audit_immutable
 BEFORE UPDATE OR DELETE ON historical_relationship_projection_replay_audit
 FOR EACH ROW EXECUTE FUNCTION protect_historical_relationship_projection_replay_audit();
CREATE TRIGGER historical_relationship_projection_replay_audit_no_truncate
 BEFORE TRUNCATE ON historical_relationship_projection_replay_audit
 FOR EACH STATEMENT EXECUTE FUNCTION protect_historical_relationship_projection_replay_audit();