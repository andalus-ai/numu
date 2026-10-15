#!/usr/bin/env bash
set -euo pipefail

# Disposable SQL-protocol acceptance test for the at-least-once delivery boundary.
# Simulates broker acceptance followed by a lost DB acknowledgement, lease expiry,
# redelivery, and an idempotent consumer transaction. It does not invoke a real broker.
CONTAINER_NAME="syirkah-pg-outbox-consumer-idem-$$"
PORT="${SYIRKAH_TEST_PG_PORT:-55444}"
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
 ('91000000-0000-0000-0000-000000000001','tenant-idem','BOQ','92000000-0000-0000-0000-000000000001',
  'BoqRevisionApproved','{"tenantId":"tenant-idem","revisionNumber":1}'::jsonb,now(),now());

-- Test-only consumer inbox and effect ledger. The inbox key is tenant + event identity.
CREATE TABLE test_consumer_inbox (
  tenant_id VARCHAR(100) NOT NULL,
  event_id UUID NOT NULL,
  first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id,event_id)
);
CREATE TABLE test_business_effect (
  tenant_id VARCHAR(100) NOT NULL,
  event_id UUID NOT NULL,
  effect_type VARCHAR(100) NOT NULL,
  applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id,event_id)
);
CREATE TABLE test_delivery_log (
  delivery_number BIGSERIAL PRIMARY KEY,
  tenant_id VARCHAR(100) NOT NULL,
  event_id UUID NOT NULL,
  delivered_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
SQL

FIRST_CLAIM="$(psql <<'SQL'
WITH claimable AS (
 SELECT event_id FROM construction_outbox_event
 WHERE tenant_id='tenant-idem' AND terminal=FALSE AND available_at<=now()
   AND (status IN ('PENDING','FAILED') OR (status='PUBLISHING' AND locked_until<=now()))
 ORDER BY available_at,occurred_at,event_id FOR UPDATE SKIP LOCKED LIMIT 1
)
UPDATE construction_outbox_event AS e
 SET status='PUBLISHING',worker_id='worker-first',attempts=attempts+1,
     lease_token='93000000-0000-0000-0000-000000000001',locked_until=now()+interval '5 minutes'
 FROM claimable WHERE e.event_id=claimable.event_id RETURNING e.event_id || ':' || e.attempts;
SQL
)"
[[ "$FIRST_CLAIM" == '91000000-0000-0000-0000-000000000001:1' ]] || {
  echo "FAIL: first claim unexpected: $FIRST_CLAIM" >&2; exit 1;
}

# First broker delivery is accepted, but the relay crashes before DB acknowledgement.
# The lease expires, allowing the same event to be published again (at-least-once).
psql -c "INSERT INTO test_delivery_log (tenant_id,event_id) VALUES ('tenant-idem','91000000-0000-0000-0000-000000000001')" >/dev/null
psql -c "UPDATE construction_outbox_event SET locked_until=now()-interval '1 second' WHERE tenant_id='tenant-idem' AND event_id='91000000-0000-0000-0000-000000000001' AND status='PUBLISHING'" >/dev/null
SECOND_CLAIM="$(psql <<'SQL'
WITH claimable AS (
 SELECT event_id FROM construction_outbox_event
 WHERE tenant_id='tenant-idem' AND terminal=FALSE AND available_at<=now()
   AND (status IN ('PENDING','FAILED') OR (status='PUBLISHING' AND locked_until<=now()))
 ORDER BY available_at,occurred_at,event_id FOR UPDATE SKIP LOCKED LIMIT 1
)
UPDATE construction_outbox_event AS e
 SET status='PUBLISHING',worker_id='worker-recovery',attempts=attempts+1,
     lease_token='93000000-0000-0000-0000-000000000002',locked_until=now()+interval '5 minutes'
 FROM claimable WHERE e.event_id=claimable.event_id RETURNING e.event_id || ':' || e.attempts;
SQL
)"
[[ "$SECOND_CLAIM" == '91000000-0000-0000-0000-000000000001:2' ]] || {
  echo "FAIL: expired lease was not reclaimed: $SECOND_CLAIM" >&2; exit 1;
}
psql -c "INSERT INTO test_delivery_log (tenant_id,event_id) VALUES ('tenant-idem','91000000-0000-0000-0000-000000000001')" >/dev/null

# Consumer transaction: only a newly inserted inbox key is allowed to create the effect.
# Running this block for both deliveries leaves exactly one inbox row and one effect row.
apply_consumer_event() {
  psql <<'SQL' >/dev/null
BEGIN;
WITH accepted AS (
  INSERT INTO test_consumer_inbox (tenant_id,event_id)
  VALUES ('tenant-idem','91000000-0000-0000-0000-000000000001')
  ON CONFLICT (tenant_id,event_id) DO NOTHING
  RETURNING tenant_id,event_id
)
INSERT INTO test_business_effect (tenant_id,event_id,effect_type)
SELECT tenant_id,event_id,'BOQ_REVISION_APPROVED' FROM accepted
ON CONFLICT (tenant_id,event_id) DO NOTHING;
COMMIT;
SQL
}
apply_consumer_event
apply_consumer_event

DELIVERIES="$(psql -c "SELECT count(*) FROM test_delivery_log WHERE tenant_id='tenant-idem' AND event_id='91000000-0000-0000-0000-000000000001'")"
INBOX="$(psql -c "SELECT count(*) FROM test_consumer_inbox WHERE tenant_id='tenant-idem' AND event_id='91000000-0000-0000-0000-000000000001'")"
EFFECTS="$(psql -c "SELECT count(*) FROM test_business_effect WHERE tenant_id='tenant-idem' AND event_id='91000000-0000-0000-0000-000000000001'")"
ATTEMPTS="$(psql -c "SELECT attempts FROM construction_outbox_event WHERE tenant_id='tenant-idem' AND event_id='91000000-0000-0000-0000-000000000001'")"
[[ "$DELIVERIES" == '2' ]] || { echo "FAIL: expected two deliveries, got $DELIVERIES" >&2; exit 1; }
[[ "$INBOX" == '1' ]] || { echo "FAIL: expected one consumer inbox record, got $INBOX" >&2; exit 1; }
[[ "$EFFECTS" == '1' ]] || { echo "FAIL: duplicate delivery repeated business effect: $EFFECTS" >&2; exit 1; }
[[ "$ATTEMPTS" == '2' ]] || { echo "FAIL: expected two relay attempts, got $ATTEMPTS" >&2; exit 1; }

echo 'PASS: simulated broker acceptance + lost acknowledgement caused redelivery after lease expiry.'
echo 'PASS: two deliveries produced one tenant-scoped inbox record and one business effect.'
echo 'NOTE: SQL-only consumer contract; it does not invoke the production relay, broker, or consumer adapter.'
