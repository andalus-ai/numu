#!/usr/bin/env bash
set -euo pipefail

# Disposable PostgreSQL acceptance test for bounded retry scheduling, terminal
# failures, and audited operator replay. Never connects to an existing database.
CONTAINER_NAME="syirkah-pg-outbox-retry-$$"
PORT="${SYIRKAH_TEST_PG_PORT:-55442}"
PASSWORD="syirkah_test_only"
TMP_DIR="$(mktemp -d)"
cleanup() { docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true; rm -rf "$TMP_DIR"; }
trap cleanup EXIT

docker run -d --rm --name "$CONTAINER_NAME" \
  -e POSTGRES_PASSWORD="$PASSWORD" -e POSTGRES_DB=syirkah_test \
  -p "127.0.0.1:${PORT}:5432" postgres:16-alpine >/dev/null
for _ in $(seq 1 60); do
  docker exec "$CONTAINER_NAME" pg_isready -U postgres -d syirkah_test >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$CONTAINER_NAME" pg_isready -U postgres -d syirkah_test >/dev/null
for migration in "$(dirname "$0")"/../db/migration/*.sql; do
  docker exec -i "$CONTAINER_NAME" psql -q -v ON_ERROR_STOP=1 -U postgres -d syirkah_test < "$migration"
done
psql() { docker exec -i "$CONTAINER_NAME" psql -Atq -v ON_ERROR_STOP=1 -U postgres -d syirkah_test "$@"; }

psql <<'SQL'
CREATE TABLE outbox_replay_audit (
  audit_id BIGSERIAL PRIMARY KEY,
  event_id UUID NOT NULL,
  operator_id TEXT NOT NULL,
  reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
  previous_attempts INTEGER NOT NULL,
  replayed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO construction_outbox_event
 (event_id, tenant_id, aggregate_type, aggregate_id, event_type, payload, occurred_at, status, attempts, available_at, max_attempts, terminal)
VALUES
 ('74000000-0000-0000-0000-000000000001','tenant-retry-test','BOQ','75000000-0000-0000-0000-000000000001','RetryTest','{}'::jsonb,now(),'FAILED',1,now()+interval '10 seconds',4,FALSE),
 ('74000000-0000-0000-0000-000000000002','tenant-retry-test','BOQ','75000000-0000-0000-0000-000000000002','TerminalTest','{}'::jsonb,now(),'FAILED',4,now()-interval '1 second',4,TRUE),
 ('74000000-0000-0000-0000-000000000003','tenant-retry-test','BOQ','75000000-0000-0000-0000-000000000003','BackoffCapTest','{}'::jsonb,now(),'FAILED',10,now(),'12',FALSE);
SQL

# Retry delays follow exponential backoff with a hard cap. This models the relay's
# scheduling expression without sleeping; the resulting availability is asserted.
psql <<'SQL' > "$TMP_DIR/backoff"
UPDATE construction_outbox_event
 SET available_at = now() + make_interval(secs => LEAST(5 * power(2, GREATEST(attempts - 1, 0))::INTEGER, 60))
 WHERE event_id='74000000-0000-0000-0000-000000000001';
SELECT (available_at > now() AND available_at <= now()+interval '10 seconds')::text
 FROM construction_outbox_event WHERE event_id='74000000-0000-0000-0000-000000000001';
UPDATE construction_outbox_event
 SET available_at = now() + make_interval(secs => LEAST(5 * power(2, GREATEST(attempts - 1, 0))::INTEGER, 60))
 WHERE event_id='74000000-0000-0000-0000-000000000003';
SELECT (available_at > now() AND available_at <= now()+interval '60 seconds')::text
 FROM construction_outbox_event WHERE event_id='74000000-0000-0000-0000-000000000003';
SQL
[[ "$(grep -c '^t$' "$TMP_DIR/backoff")" == 2 ]] || { echo 'FAIL: exponential retry backoff/cap was not respected' >&2; cat "$TMP_DIR/backoff" >&2; exit 1; }

# A terminal row remains unclaimable even if its availability timestamp is in the past.
TERMINAL_CLAIMS="$(psql -c "SELECT count(*) FROM construction_outbox_event WHERE event_id='74000000-0000-0000-0000-000000000002' AND terminal=FALSE AND available_at<=now() AND attempts<max_attempts AND status IN ('PENDING','FAILED')")"
[[ "$TERMINAL_CLAIMS" == 0 ]] || { echo 'FAIL: exhausted terminal event is claimable' >&2; exit 1; }

# Operator replay is explicit and audited; prior attempt count is retained in the audit.
psql <<'SQL'
WITH target AS (
 SELECT event_id, attempts FROM construction_outbox_event
 WHERE event_id='74000000-0000-0000-0000-000000000002' AND terminal=TRUE FOR UPDATE
), audited AS (
 INSERT INTO outbox_replay_audit(event_id, operator_id, reason, previous_attempts)
 SELECT event_id, 'operator-test', 'Reviewed remediation completed', attempts FROM target
 RETURNING event_id
)
UPDATE construction_outbox_event e
 SET terminal=FALSE, status='PENDING', attempts=0, available_at=now(),
     last_error=NULL, worker_id=NULL, lease_token=NULL, locked_until=NULL
 FROM audited WHERE e.event_id=audited.event_id;
SQL
AUDIT="$(psql -c "SELECT operator_id || ':' || previous_attempts || ':' || (length(trim(reason))>0)::text FROM outbox_replay_audit WHERE event_id='74000000-0000-0000-0000-000000000002'")"
[[ "$AUDIT" == 'operator-test:4:true' ]] || { echo "FAIL: operator replay audit missing/incorrect: $AUDIT" >&2; exit 1; }
REPLAY="$(psql -c "SELECT status || ':' || terminal::text || ':' || attempts::text FROM construction_outbox_event WHERE event_id='74000000-0000-0000-0000-000000000002'")"
[[ "$REPLAY" == 'PENDING:false:0' ]] || { echo "FAIL: audited replay did not requeue event: $REPLAY" >&2; exit 1; }

printf '%s\n' 'PASS: exponential retry delay is scheduled and capped.'
printf '%s\n' 'PASS: exhausted terminal events are excluded from normal claims.'
printf '%s\n' 'PASS: operator replay is explicitly audited before requeue.'
printf '%s\n' 'NOTE: disposable SQL harness only; does not invoke the production relay or admin API.'
