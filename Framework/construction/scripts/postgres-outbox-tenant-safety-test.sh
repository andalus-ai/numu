#!/usr/bin/env bash
set -euo pipefail

# Disposable PostgreSQL test for tenant-filtered outbox claims, ownership fencing,
# retry scheduling, and tenantless legacy-row exclusion. SQL protocol only.
CONTAINER_NAME="syirkah-pg-outbox-tenant-$$"
PORT="${SYIRKAH_TEST_PG_PORT:-55443}"
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
 ('81000000-0000-0000-0000-000000000001','tenant-a','BOQ','82000000-0000-0000-0000-000000000001','TenantEvent','{"tenantId":"tenant-a"}'::jsonb,now(),now()),
 ('81000000-0000-0000-0000-000000000002','tenant-b','BOQ','82000000-0000-0000-0000-000000000002','TenantEvent','{"tenantId":"tenant-b"}'::jsonb,now(),now()),
 ('81000000-0000-0000-0000-000000000003',NULL,'BOQ','82000000-0000-0000-0000-000000000003','LegacyTenantlessEvent','{}'::jsonb,now(),now());
SQL

# Claims must be tenant-filtered using the trusted tenant identity supplied by the caller.
CLAIM_A="$(psql <<'SQL'
WITH claimable AS (
 SELECT event_id FROM construction_outbox_event
 WHERE tenant_id='tenant-a' AND terminal=FALSE AND available_at<=now()
   AND (status IN ('PENDING','FAILED') OR (status='PUBLISHING' AND locked_until<=now()))
 ORDER BY available_at,occurred_at,event_id FOR UPDATE SKIP LOCKED LIMIT 10
)
UPDATE construction_outbox_event AS e
 SET status='PUBLISHING',worker_id='tenant-a-worker',attempts=attempts+1,
     lease_token='83000000-0000-0000-0000-000000000001',locked_until=now()+interval '5 minutes'
 FROM claimable WHERE e.event_id=claimable.event_id RETURNING e.event_id;
SQL
)"
[[ "$CLAIM_A" == '81000000-0000-0000-0000-000000000001' ]] || {
  echo "FAIL: tenant A claimed unexpected rows: $CLAIM_A" >&2; exit 1;
}

CLAIM_B="$(psql <<'SQL'
WITH claimable AS (
 SELECT event_id FROM construction_outbox_event
 WHERE tenant_id='tenant-b' AND terminal=FALSE AND available_at<=now()
   AND (status IN ('PENDING','FAILED') OR (status='PUBLISHING' AND locked_until<=now()))
 ORDER BY available_at,occurred_at,event_id FOR UPDATE SKIP LOCKED LIMIT 10
)
UPDATE construction_outbox_event AS e
 SET status='PUBLISHING',worker_id='tenant-b-worker',attempts=attempts+1,
     lease_token='83000000-0000-0000-0000-000000000002',locked_until=now()+interval '5 minutes'
 FROM claimable WHERE e.event_id=claimable.event_id RETURNING e.event_id;
SQL
)"
[[ "$CLAIM_B" == '81000000-0000-0000-0000-000000000002' ]] || {
  echo "FAIL: tenant B claimed unexpected rows: $CLAIM_B" >&2; exit 1;
}

# A tenant B worker cannot acknowledge tenant A's event, even if it knows the event ID.
CROSS_ACK="$(psql -c "UPDATE construction_outbox_event SET status='PUBLISHED',published_at=now(),worker_id=NULL,lease_token=NULL,locked_until=NULL WHERE event_id='81000000-0000-0000-0000-000000000001' AND tenant_id='tenant-b' AND status='PUBLISHING' AND lease_token='83000000-0000-0000-0000-000000000001' RETURNING event_id")"
[[ -z "$CROSS_ACK" ]] || { echo 'FAIL: cross-tenant acknowledgement changed event ownership' >&2; exit 1; }

# Retry scheduling: a failed event whose available_at is in the future is not claimable.
psql -c "UPDATE construction_outbox_event SET status='FAILED',available_at=now()+interval '1 hour',worker_id=NULL,lease_token=NULL,locked_until=NULL,last_error='simulated broker timeout' WHERE event_id='81000000-0000-0000-0000-000000000001' AND tenant_id='tenant-a'" >/dev/null
EARLY_RETRY="$(psql -c "WITH eligible AS (SELECT event_id FROM construction_outbox_event WHERE tenant_id='tenant-a' AND terminal=FALSE AND available_at<=now() AND (status IN ('PENDING','FAILED') OR (status='PUBLISHING' AND locked_until<=now())) FOR UPDATE SKIP LOCKED) SELECT count(*) FROM eligible")"
[[ "$EARLY_RETRY" == '0' ]] || { echo "FAIL: future retry was eligible too early: $EARLY_RETRY" >&2; exit 1; }

psql -c "UPDATE construction_outbox_event SET available_at=now()-interval '1 second' WHERE event_id='81000000-0000-0000-0000-000000000001' AND tenant_id='tenant-a'" >/dev/null
RETRY="$(psql <<'SQL'
WITH claimable AS (
 SELECT event_id FROM construction_outbox_event
 WHERE tenant_id='tenant-a' AND terminal=FALSE AND available_at<=now()
   AND (status IN ('PENDING','FAILED') OR (status='PUBLISHING' AND locked_until<=now()))
 ORDER BY available_at,occurred_at,event_id FOR UPDATE SKIP LOCKED LIMIT 1
)
UPDATE construction_outbox_event AS e
 SET status='PUBLISHING',worker_id='tenant-a-retry',attempts=attempts+1,
     lease_token='83000000-0000-0000-0000-000000000003',locked_until=now()+interval '5 minutes'
 FROM claimable WHERE e.event_id=claimable.event_id RETURNING e.event_id || ':' || e.attempts;
SQL
)"
[[ "$RETRY" == '81000000-0000-0000-0000-000000000001:2' ]] || {
  echo "FAIL: retry was not reclaimed with incremented attempt count: $RETRY" >&2; exit 1;
}

NULL_TENANT_CLAIMS="$(psql -c "SELECT count(*) FROM construction_outbox_event WHERE tenant_id IS NULL AND status IN ('PENDING','FAILED') AND available_at<=now()")"
[[ "$NULL_TENANT_CLAIMS" == '1' ]] || { echo "FAIL: expected legacy tenantless event fixture" >&2; exit 1; }
# Tenant-filtered claim predicate cannot match NULL tenant_id; it remains quarantined from delivery.
NULL_TENANT_DELIVERY="$(psql -c "WITH claimable AS (SELECT event_id FROM construction_outbox_event WHERE tenant_id='tenant-a' AND terminal=FALSE AND available_at<=now() AND status IN ('PENDING','FAILED') FOR UPDATE SKIP LOCKED) SELECT count(*) FROM claimable WHERE event_id='81000000-0000-0000-0000-000000000003'")"
[[ "$NULL_TENANT_DELIVERY" == '0' ]] || { echo 'FAIL: tenantless legacy event entered a tenant claim' >&2; exit 1; }

echo 'PASS: tenant A and tenant B each claimed only their own outbox event.'
echo 'PASS: cross-tenant acknowledgement was fenced; future retries were delayed and attempt count advanced.'
echo 'PASS: legacy tenantless events were excluded from tenant-scoped delivery.'
echo 'NOTE: SQL-level harness only; it does not invoke PgOutboxRelayStore, the broker, or production authorization.'
