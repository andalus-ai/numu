-- GPS traces, retention and privacy functions for the mobility suite.

-- ---------------------------------------------------------------------------
-- Trip traces: insert-only, one small row per accepted GPS fix (the evidence behind fares and safety incidents).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trip_trace_point (
    tenant_id  uuid             NOT NULL,
    ride_id    uuid             NOT NULL,
    at         timestamptz      NOT NULL,
    lat        double precision NOT NULL,
    lng        double precision NOT NULL
);
CREATE INDEX IF NOT EXISTS trip_trace_point_ride_idx ON trip_trace_point (tenant_id, ride_id, at);
CREATE INDEX IF NOT EXISTS trip_trace_point_age_idx ON trip_trace_point (at);       -- retention purge

ALTER TABLE trip_trace_point ENABLE ROW LEVEL SECURITY;
ALTER TABLE trip_trace_point FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS trip_trace_point_tenant ON trip_trace_point;
CREATE POLICY trip_trace_point_tenant ON trip_trace_point
    USING      (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);

-- ---------------------------------------------------------------------------
-- Retention. Run from a scheduled job using a dedicated maintenance role with BYPASSRLS (it spans all tenants);
-- the application role must NOT have that attribute. Returns what it removed, as JSON.
--   SELECT mobility_purge_expired();
-- Defaults: GPS traces 90 days, demand buckets 2 hours, idempotency keys 48 hours, published outbox and inbox rows 14 days.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION mobility_purge_expired(
    trace_older        interval DEFAULT interval '90 days',
    demand_older       interval DEFAULT interval '2 hours',
    idempotency_older  interval DEFAULT interval '48 hours',
    outbox_older       interval DEFAULT interval '14 days',
    inbox_older        interval DEFAULT interval '14 days') RETURNS jsonb AS $$
DECLARE
    n_trace bigint; n_demand bigint; n_idem bigint; n_outbox bigint; n_inbox bigint;
BEGIN
    DELETE FROM trip_trace_point WHERE at < now() - trace_older;                          GET DIAGNOSTICS n_trace = ROW_COUNT;
    DELETE FROM ride_demand WHERE bucket < now() - demand_older;                          GET DIAGNOSTICS n_demand = ROW_COUNT;
    DELETE FROM platform_idempotency WHERE first_seen_at < now() - idempotency_older;     GET DIAGNOSTICS n_idem = ROW_COUNT;
    DELETE FROM platform_outbox WHERE published_at IS NOT NULL AND published_at < now() - outbox_older;   GET DIAGNOSTICS n_outbox = ROW_COUNT;
    DELETE FROM platform_inbox WHERE received_at < now() - inbox_older;                   GET DIAGNOSTICS n_inbox = ROW_COUNT;
    RETURN jsonb_build_object('trace_points', n_trace, 'demand_buckets', n_demand, 'idempotency_keys', n_idem,
                              'outbox_rows', n_outbox, 'inbox_rows', n_inbox);
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------------
-- Rider erasure (data-subject request under the Indonesian PDP Law / GDPR). For every FINISHED ride of the rider:
--   * addresses removed and exact pickup/drop-off coordinates coarsened to ~1 km (kept for aggregate analytics only);
--   * GPS traces deleted;
--   * chat message text replaced with '[erased]'.
-- Rides still in progress are left alone (erase again once they end). Wallet ledger entries are financial records and are kept
-- (they carry no name or address, only the pseudonymous rider id). Idempotent. Same maintenance-role requirement as above.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION mobility_erase_rider(p_tenant uuid, p_rider uuid) RETURNS jsonb AS $$
DECLARE
    n_rides bigint; n_traces bigint; n_chats bigint;
BEGIN
    CREATE TEMP TABLE _erase_rides ON COMMIT DROP AS
        SELECT id FROM aggregate_document
        WHERE tenant_id = p_tenant AND kind = 'ride' AND ref_id = p_rider
          AND status IN ('COMPLETED', 'CANCELLED', 'NO_DRIVERS');

    UPDATE aggregate_document d SET doc =
        jsonb_set(jsonb_set(jsonb_set(jsonb_set(d.doc,
            '{pickup,label}', '""'::jsonb),
            '{dropoff,label}', '""'::jsonb),
            '{pickup,point}', jsonb_build_object('lat', round((d.doc #>> '{pickup,point,lat}')::numeric, 2), 'lng', round((d.doc #>> '{pickup,point,lng}')::numeric, 2))),
            '{dropoff,point}', jsonb_build_object('lat', round((d.doc #>> '{dropoff,point,lat}')::numeric, 2), 'lng', round((d.doc #>> '{dropoff,point,lng}')::numeric, 2))),
        updated_at = now()
    WHERE d.tenant_id = p_tenant AND d.kind = 'ride' AND d.id IN (SELECT id FROM _erase_rides);
    GET DIAGNOSTICS n_rides = ROW_COUNT;

    DELETE FROM trip_trace_point WHERE tenant_id = p_tenant AND ride_id IN (SELECT id FROM _erase_rides);
    GET DIAGNOSTICS n_traces = ROW_COUNT;

    UPDATE aggregate_document c SET doc = jsonb_set(c.doc, '{messages}',
            (SELECT coalesce(jsonb_agg(jsonb_set(m, '{text}', '"[erased]"'::jsonb)), '[]'::jsonb) FROM jsonb_array_elements(c.doc -> 'messages') m)),
        updated_at = now()
    WHERE c.tenant_id = p_tenant AND c.kind = 'ride-chat' AND c.id IN (SELECT id FROM _erase_rides);
    GET DIAGNOSTICS n_chats = ROW_COUNT;

    RETURN jsonb_build_object('rides_pseudonymised', n_rides, 'trace_points_deleted', n_traces, 'chats_erased', n_chats);
END;
$$ LANGUAGE plpgsql;

-- The maintenance functions are for the maintenance role only. (Even if the application role could call them, row-level security would make
-- them touch nothing; revoking makes that explicit.)  Grant EXECUTE to your maintenance role in your environment setup:
--   GRANT EXECUTE ON FUNCTION mobility_purge_expired(interval, interval, interval, interval, interval), mobility_erase_rider(uuid, uuid) TO maintenance;
REVOKE ALL ON FUNCTION mobility_purge_expired(interval, interval, interval, interval, interval) FROM PUBLIC;
REVOKE ALL ON FUNCTION mobility_erase_rider(uuid, uuid) FROM PUBLIC;
