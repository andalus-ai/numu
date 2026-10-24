-- Audit history is an append-only security record. Corrections must be represented by
-- new events, never by rewriting or deleting existing events.
CREATE OR REPLACE FUNCTION workspace_guard_audit_event_history()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Workspace audit events are append-only'
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_workspace_audit_events_append_only
BEFORE UPDATE OR DELETE
ON workspace_audit_events
FOR EACH ROW
EXECUTE FUNCTION workspace_guard_audit_event_history();
