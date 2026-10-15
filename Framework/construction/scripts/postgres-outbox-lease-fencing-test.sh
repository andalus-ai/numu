#!/usr/bin/env bash
set -euo pipefail

# Disposable PostgreSQL protocol test for stale-worker fencing.
# This verifies SQL predicates, not the production relay implementation.
CONTAINER_NAME="syirkah-pg-outbox-fencing-$$"
PORT="${SYIRKAH_TEST_PG_PORT:-55445}"
PASSWORD="syirkah_test_only"
cleanup() { docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker run -d --rm --name "$CONTAINER_NAME" \
  -e POSTGRES_PASSWORD="$PASSWORD" -e POSTGRES_DB=syirkah_test \
  -p "127.0.0.1:${PORT}:5432" postgres:16-alpine >/dev/null
for _ in $(seq 1 60); do
  if docker exec "$CONTAINER_NAME" pg_isready -U postgres -d syirkah_test >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$CONTAINER_NAME" pg_isready -U postgres -d syirkah_test >/dev/null
for migration in "$(dirname "$0")"/../db/migration/*.sql; do
  docker exec -i "$CONTAINER_NAME" psql -q -v ON_ERROR_STOP=1 -U postgres -d syirkah_test < "$migration"
done

psql() {
  docker exec -i "$CONTAINER_NAME" psql -Atq -v ON_ERROR_STOP=1 -U postgres -d syirkah_test "$@"
}

psql <<'SQL'
INSERT INTO construction_outbox_event
 (event_id, tenant_id, aggregate_type, aggregate_id, event_type, payload, occurred_at, available_at)
VALUES
 ('94000000-0000-0000-0000-000000000001','tenant-fence','BOQ','95000000-0000-0000-0000-000000000001',
  'BoqRevisionApproved','{"tenantId":"tenant-fence","revisionNumber":1}'::jsonb,now(),now());
SQL

claim_event() {
  local worker="$1" token="$2"
  psql <<SQL
WITH claimable AS (
 SELECT event_id FROM construction_outbox_event
 WHERE tenant_id='tenant-fence' AND terminal=FALSE AND available_at<=now()
   AND (status IN ('PENDING','FAILED') OR (status='PUBLISHING' AND locked_until<=now()))
 ORDER BY available_at,occurred_at,event_id FOR UPDATE SKIP LOCKED LIMIT 1
)
UPDATE construction_outbox_event AS e
 SET status='PUBLISHING',worker_id='$worker',attempts=attempts+1,
     lease_token='$token',locked_until=now()+interval '5 minutes'
 FROM claimable WHERE e.event_id=claimable.event_id RETURNING e.event_id || ':' || e.attempts;
SQL
}

FIRST="$(claim_event 'worker-stale' '96000000-0000-0000-0000-000000000001')"
[[ "$FIRST" == '94000000-0000-0000-0000-000000000001:1' ]] || {
  echo "FAIL: initial claim unexpected: $FIRST" >&2; exit 1;
}
# Expire the first lease and let a recovery worker reclaim the event with a new token.
psql -c "UPDATE construction_outbox_event SET locked_until=now()-interval '1 second' WHERE tenant_id='tenant-fence' AND event_id='94000000-0000-0000-0000-000000000001' AND lease_token='96000000-0000-0000-0000-000000000001'" >/dev/null
SECOND="$(claim_event 'worker-current' '96000000-0000-0000-0000-000000000002')"
[[ "$SECOND" == '94000000-0000-0000-0000-000000000001:2' ]] || {
  echo "FAIL: recovery claim unexpected: $SECOND" >&2; exit 1;
}

# A stale worker must not acknowledge, reschedule, or renew the recovered event.
STALE_ACK="$(psql -c "UPDATE construction_outbox_event SET status='PUBLISHED',published_at=now(),worker_id=NULL,lease_token=NULL,locked_until=NULL WHERE tenant_id='tenant-fence' AND event_id='94000000-0000-0000-0000-000000000001' AND status='PUBLISHING' AND lease_token='96000000-0000-0000-0000-000000000001' RETURNING event_id")"
STALE_FAIL="$(psql -c "UPDATE construction_outbox_event SET status='FAILED',available_at=now()+interval '1 hour',worker_id=NULL,lease_token=NULL,locked_until=NULL WHERE tenant_id='tenant-fence' AND event_id='94000000-0000-0000-0000-000000000001' AND status='PUBLISHING' AND lease_token='96000000-0000-0000-0000-000000000001' RETURNING event_id")"
STALE_RENEW="$(psql -c "UPDATE construction_outbox_event SET locked_until=now()+interval '10 minutes' WHERE tenant_id='tenant-fence' AND event_id='94000000-0000-0000-0000-000000000001' AND status='PUBLISHING' AND lease_token='96000000-0000-0000-0000-000000000001' RETURNING event_id")"
[[ -z "$STALE_ACK" ]] || { echo 'FAIL: stale worker acknowledged reclaimed event' >&2; exit 1; }
[[ -z "$STALE_FAIL" ]] || { echo 'FAIL: stale worker overwrote retry state' >&2; exit 1; }
[[ -z "$STALE_RENEW" ]] || { echo 'FAIL: stale worker renewed another worker lease' >&2; exit 1; }

CURRENT="$(psql -c "SELECT status || ':' || worker_id || ':' || lease_token::text || ':' || attempts || ':' || (published_at IS NULL)::text FROM construction_outbox_event WHERE tenant_id='tenant-fence' AND event_id='94000000-0000-0000-0000-000000000001'")"
EXPECTED="PUBLISHING:worker-current:96000000-0000-0000-0000-000000000002:2:true"
[[ "$CURRENT" == "$EXPECTED" ]] || { echo "FAIL: current lease state was changed: $CURRENT" >&2; exit 1; }

CURRENT_ACK="$(psql -c "UPDATE construction_outbox_event SET status='PUBLISHED',published_at=now(),worker_id=NULL,lease_token=NULL,locked_until=NULL WHERE tenant_id='tenant-fence' AND event_id='94000000-0000-0000-0000-000000000001' AND status='PUBLISHING' AND lease_token='96000000-0000-0000-0000-000000000002' RETURNING event_id")"
[[ "$CURRENT_ACK" == '94000000-0000-0000-0000-000000000001' ]] || {
  echo "FAIL: current lease holder could not acknowledge event: $CURRENT_ACK" >&2; exit 1;
}
FINAL="$(psql -c "SELECT status || ':' || (published_at IS NOT NULL)::text || ':' || attempts FROM construction_outbox_event WHERE tenant_id='tenant-fence' AND event_id='94000000-0000-0000-0000-000000000001'")"
[[ "$FINAL" == 'PUBLISHED:true:2' ]] || { echo "FAIL: final state unexpected: $FINAL" >&2; exit 1; }

echo 'PASS: stale lease token cannot acknowledge, reschedule, or renew a reclaimed event.'
echo 'PASS: current lease holder can acknowledge; attempt count remains two.'
echo 'NOTE: disposable SQL-protocol test; it does not invoke the production relay adapter.'
