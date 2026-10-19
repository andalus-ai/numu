#!/usr/bin/env bash
set -euo pipefail

# Real PostgreSQL race test for same-key idempotency reservations.
# Uses the project's actual migration schema, but simulates the command body in SQL;
# it does not invoke the Java executor or PgTransactionalCommandRunner.
CONTAINER_NAME="numou-pg-idempotency-race-$$"
PORT="${NUMOU_TEST_PG_PORT:-55448}"
PASSWORD="numou_test_only"
TMP_DIR="$(mktemp -d)"
cleanup() {
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

command -v docker >/dev/null || { echo "ERROR: docker is required" >&2; exit 2; }
command -v psql >/dev/null || { echo "ERROR: psql client is required" >&2; exit 2; }

docker run -d --rm --name "$CONTAINER_NAME" \
  -e POSTGRES_PASSWORD="$PASSWORD" -e POSTGRES_DB=numou_test \
  -p "127.0.0.1:${PORT}:5432" postgres:16-alpine >/dev/null
for _ in $(seq 1 60); do
  if docker exec "$CONTAINER_NAME" pg_isready -U postgres -d numou_test >/dev/null 2>&1; then break; fi
  sleep 1
done
docker exec "$CONTAINER_NAME" pg_isready -U postgres -d numou_test >/dev/null

for migration in "$(dirname "$0")"/../db/migration/*.sql; do
  docker exec -i "$CONTAINER_NAME" psql -v ON_ERROR_STOP=1 -U postgres -d numou_test < "$migration" >/dev/null
done

# The helper table represents the business effect that must execute only for the
# transaction that successfully reserves the idempotency key.
docker exec -i "$CONTAINER_NAME" psql -v ON_ERROR_STOP=1 -U postgres -d numou_test <<'SQL'
CREATE TABLE test_command_effect (
  tenant_id VARCHAR(100) NOT NULL,
  idempotency_key VARCHAR(200) NOT NULL,
  effect_count INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (tenant_id, idempotency_key)
);
SQL

# Worker A wins the unique-key insert and holds the transaction open. Worker B
# races the same key while A is uncommitted; its ON CONFLICT must wait, then lose.
(
  PGPASSWORD="$PASSWORD" psql -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p "$PORT" -U postgres -d numou_test \
    >"$TMP_DIR/worker-a.out" 2>&1 <<'SQL'
BEGIN;
WITH reserved AS (
  INSERT INTO construction_command_idempotency
    (tenant_id,idempotency_key,operation,request_fingerprint,state,created_at)
  VALUES ('tenant-race','same-key','capture',repeat('a',64),'PROCESSING',now())
  ON CONFLICT (tenant_id,idempotency_key) DO NOTHING
  RETURNING tenant_id,idempotency_key
)
INSERT INTO test_command_effect(tenant_id,idempotency_key)
SELECT tenant_id,idempotency_key FROM reserved;
SELECT pg_sleep(2);
UPDATE construction_command_idempotency
   SET state='COMPLETED', response_json='{"revisionNumber":1}'::jsonb, completed_at=now()
 WHERE tenant_id='tenant-race' AND idempotency_key='same-key' AND state='PROCESSING';
COMMIT;
SQL
) &
A_PID=$!
sleep 0.35
(
  PGPASSWORD="$PASSWORD" psql -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p "$PORT" -U postgres -d numou_test \
    >"$TMP_DIR/worker-b.out" 2>&1 <<'SQL'
BEGIN;
WITH reserved AS (
  INSERT INTO construction_command_idempotency
    (tenant_id,idempotency_key,operation,request_fingerprint,state,created_at)
  VALUES ('tenant-race','same-key','capture',repeat('a',64),'PROCESSING',now())
  ON CONFLICT (tenant_id,idempotency_key) DO NOTHING
  RETURNING tenant_id,idempotency_key
)
INSERT INTO test_command_effect(tenant_id,idempotency_key)
SELECT tenant_id,idempotency_key FROM reserved;
SELECT state || ':' || COALESCE(response_json->>'revisionNumber','null') AS observed_outcome
  FROM construction_command_idempotency
 WHERE tenant_id='tenant-race' AND idempotency_key='same-key';
COMMIT;
SQL
) &
B_PID=$!
wait "$A_PID"
wait "$B_PID"

if ! grep -Eq 'COMPLETED:1' "$TMP_DIR/worker-b.out"; then
  echo "ERROR: competing request did not observe the committed completed outcome" >&2
  cat "$TMP_DIR/worker-b.out" >&2
  exit 1
fi

# A different fingerprint cannot take over the same tenant/key or execute a
# second business effect. It is detected by reading the existing fingerprint.
PGPASSWORD="$PASSWORD" psql -X -v ON_ERROR_STOP=1 -h 127.0.0.1 -p "$PORT" -U postgres -d numou_test <<'SQL'
DO $$
BEGIN
  IF (SELECT count(*) FROM construction_command_idempotency
       WHERE tenant_id='tenant-race' AND idempotency_key='same-key') <> 1 THEN
    RAISE EXCEPTION 'same-key race created multiple idempotency rows';
  END IF;
  IF (SELECT count(*) FROM test_command_effect
       WHERE tenant_id='tenant-race' AND idempotency_key='same-key') <> 1 THEN
    RAISE EXCEPTION 'same-key race executed business effect more than once';
  END IF;
  IF (SELECT state FROM construction_command_idempotency
       WHERE tenant_id='tenant-race' AND idempotency_key='same-key') <> 'COMPLETED' THEN
    RAISE EXCEPTION 'winning command did not complete';
  END IF;
  IF (SELECT response_json->>'revisionNumber' FROM construction_command_idempotency
       WHERE tenant_id='tenant-race' AND idempotency_key='same-key') <> '1' THEN
    RAISE EXCEPTION 'completed response was not preserved';
  END IF;
END $$;

-- Simulate the adapter's conflict branch: no insert is made for a different
-- fingerprint, and the caller can compare against the persisted request.
INSERT INTO construction_command_idempotency
  (tenant_id,idempotency_key,operation,request_fingerprint,state,created_at)
VALUES ('tenant-race','same-key','capture',repeat('b',64),'PROCESSING',now())
ON CONFLICT (tenant_id,idempotency_key) DO NOTHING;
DO $$
BEGIN
  IF (SELECT request_fingerprint FROM construction_command_idempotency
       WHERE tenant_id='tenant-race' AND idempotency_key='same-key') <> repeat('a',64) THEN
    RAISE EXCEPTION 'conflicting request replaced original fingerprint';
  END IF;
  IF (SELECT count(*) FROM test_command_effect
       WHERE tenant_id='tenant-race' AND idempotency_key='same-key') <> 1 THEN
    RAISE EXCEPTION 'conflicting request executed business effect';
  END IF;
END $$;
SELECT 'Numou PostgreSQL idempotency race test PASSED' AS result;
SQL

echo "PASS: same-key concurrent reservation, single business effect, completed response, and conflicting-fingerprint invariants hold."
echo "NOTE: SQL-level concurrency test only; Java adapter/runner integration is still required."
