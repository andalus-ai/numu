#!/usr/bin/env bash
set -euo pipefail

# Disposable PostgreSQL test for outbox claim exclusivity, lease expiry and token fencing.
# Requires Docker; does not connect to or mutate an existing database.
CONTAINER_NAME="numou-pg-outbox-$$"
PORT="${NUMOU_TEST_PG_PORT:-55441}"
PASSWORD="numou_test_only"
TMP_DIR="$(mktemp -d)"
cleanup() {
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

docker run -d --rm --name "$CONTAINER_NAME" \
  -e POSTGRES_PASSWORD="$PASSWORD" -e POSTGRES_DB=numou_test \
  -p "127.0.0.1:${PORT}:5432" postgres:16-alpine >/dev/null
for _ in $(seq 1 60); do
  if docker exec "$CONTAINER_NAME" pg_isready -U postgres -d numou_test >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$CONTAINER_NAME" pg_isready -U postgres -d numou_test >/dev/null
for migration in "$(dirname "$0")"/../db/migration/*.sql; do
  docker exec -i "$CONTAINER_NAME" psql -q -v ON_ERROR_STOP=1 -U postgres -d numou_test < "$migration"
done

psql() {
  docker exec -i "$CONTAINER_NAME" psql -Atq -v ON_ERROR_STOP=1 -U postgres -d numou_test "$@"
}

# Fixed IDs and timestamps make the ordering assertion deterministic.
psql <<'SQL'
INSERT INTO construction_outbox_event
 (event_id, tenant_id, aggregate_type, aggregate_id, event_type, payload, occurred_at, available_at)
VALUES
 ('71000000-0000-0000-0000-000000000001','tenant-outbox-test','BOQ','72000000-0000-0000-0000-000000000001','TestEvent','{}'::jsonb,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z'),
 ('71000000-0000-0000-0000-000000000002','tenant-outbox-test','BOQ','72000000-0000-0000-0000-000000000002','TestEvent','{}'::jsonb,'2026-01-01T00:00:01Z','2026-01-01T00:00:00Z');
SQL

# Worker A holds its claimed row lock briefly. Worker B runs the production-shaped
# claim CTE while A is locked and must skip A to claim the second event.
(
  psql <<'SQL' > "$TMP_DIR/worker-a"
BEGIN;
WITH claimable AS (
 SELECT event_id FROM construction_outbox_event
 WHERE terminal=FALSE AND available_at <= now()
   AND (status IN ('PENDING','FAILED') OR (status='PUBLISHING' AND locked_until <= now()))
 ORDER BY available_at, occurred_at, event_id FOR UPDATE SKIP LOCKED LIMIT 1
)
UPDATE construction_outbox_event AS event
 SET status='PUBLISHING', worker_id='worker-A', attempts=attempts+1,
     lease_token='73000000-0000-0000-0000-000000000001', locked_until=now()+interval '5 minutes'
 FROM claimable WHERE event.event_id=claimable.event_id RETURNING event.event_id;
SELECT pg_sleep(2);
COMMIT;
SQL
) &
WORKER_A_PID=$!
sleep 0.4
psql <<'SQL' > "$TMP_DIR/worker-b"
WITH claimable AS (
 SELECT event_id FROM construction_outbox_event
 WHERE terminal=FALSE AND available_at <= now()
   AND (status IN ('PENDING','FAILED') OR (status='PUBLISHING' AND locked_until <= now()))
 ORDER BY available_at, occurred_at, event_id FOR UPDATE SKIP LOCKED LIMIT 1
)
UPDATE construction_outbox_event AS event
 SET status='PUBLISHING', worker_id='worker-B', attempts=attempts+1,
     lease_token='73000000-0000-0000-0000-000000000002', locked_until=now()+interval '5 minutes'
 FROM claimable WHERE event.event_id=claimable.event_id RETURNING event.event_id;
SQL
wait "$WORKER_A_PID"
A_ID="$(grep -E '^71000000-' "$TMP_DIR/worker-a" | head -1)"
B_ID="$(grep -E '^71000000-' "$TMP_DIR/worker-b" | head -1)"
[[ "$A_ID" == '71000000-0000-0000-0000-000000000001' ]] || { echo "FAIL: worker A unexpected claim: $A_ID" >&2; exit 1; }
[[ "$B_ID" == '71000000-0000-0000-0000-000000000002' ]] || { echo "FAIL: worker B did not skip A's locked row: $B_ID" >&2; exit 1; }

# Force A's lease to expire, reclaim it under a new token, and prove that the
# old token cannot acknowledge the reclaimed event while the new token can.
psql <<'SQL' > "$TMP_DIR/recovery"
UPDATE construction_outbox_event
   SET locked_until=now()-interval '1 second'
 WHERE event_id='71000000-0000-0000-0000-000000000001';
WITH claimable AS (
 SELECT event_id FROM construction_outbox_event
 WHERE terminal=FALSE AND available_at <= now()
   AND (status IN ('PENDING','FAILED') OR (status='PUBLISHING' AND locked_until <= now()))
 ORDER BY available_at, occurred_at, event_id FOR UPDATE SKIP LOCKED LIMIT 1
)
UPDATE construction_outbox_event AS event
 SET status='PUBLISHING', worker_id='worker-recovery', attempts=attempts+1,
     lease_token='73000000-0000-0000-0000-000000000003', locked_until=now()+interval '5 minutes'
 FROM claimable WHERE event.event_id=claimable.event_id RETURNING event.event_id;
SQL
RECLAIMED="$(grep -E '^71000000-' "$TMP_DIR/recovery" | head -1)"
[[ "$RECLAIMED" == '71000000-0000-0000-0000-000000000001' ]] || { echo "FAIL: expired lease was not reclaimed: $RECLAIMED" >&2; exit 1; }

OLD_ACK="$(psql -c "UPDATE construction_outbox_event SET status='PUBLISHED',published_at=now(),worker_id=NULL,lease_token=NULL,locked_until=NULL WHERE event_id='71000000-0000-0000-0000-000000000001' AND status='PUBLISHING' AND lease_token='73000000-0000-0000-0000-000000000001' RETURNING event_id")"
[[ -z "$OLD_ACK" ]] || { echo "FAIL: stale lease token acknowledged a reclaimed event" >&2; exit 1; }
NEW_ACK="$(psql -c "UPDATE construction_outbox_event SET status='PUBLISHED',published_at=now(),worker_id=NULL,lease_token=NULL,locked_until=NULL WHERE event_id='71000000-0000-0000-0000-000000000001' AND status='PUBLISHING' AND lease_token='73000000-0000-0000-0000-000000000003' RETURNING event_id")"
[[ "$NEW_ACK" == '71000000-0000-0000-0000-000000000001' ]] || { echo "FAIL: current lease token could not acknowledge event" >&2; exit 1; }

# Terminal failures are excluded from future claims even after availability time.
psql -c "UPDATE construction_outbox_event SET status='FAILED',terminal=TRUE,available_at=now()-interval '1 minute',worker_id=NULL,lease_token=NULL,locked_until=NULL WHERE event_id='71000000-0000-0000-0000-000000000002'" >/dev/null
TERMINAL_CLAIMS="$(psql -c "WITH claimable AS (SELECT event_id FROM construction_outbox_event WHERE terminal=FALSE AND available_at<=now() AND (status IN ('PENDING','FAILED') OR (status='PUBLISHING' AND locked_until<=now())) ORDER BY available_at,occurred_at,event_id FOR UPDATE SKIP LOCKED LIMIT 10) SELECT count(*) FROM claimable")"
[[ "$TERMINAL_CLAIMS" == "0" ]] || { echo "FAIL: terminal event remained claimable (or unexpected eligible rows): $TERMINAL_CLAIMS" >&2; exit 1; }

echo "PASS: concurrent claimers selected distinct events; SKIP LOCKED avoided the held row."
echo "PASS: expired lease was reclaimed and stale lease token could not acknowledge it."
echo "PASS: current lease token acknowledged successfully and terminal event was excluded."
echo "NOTE: SQL-level harness; it does not execute PgOutboxRelayStore or broker delivery code."
