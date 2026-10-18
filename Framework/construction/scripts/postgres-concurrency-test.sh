#!/usr/bin/env bash
set -euo pipefail

# Disposable PostgreSQL concurrency test for schema-level guarantees.
# Requires Docker, psql inside the container, and GNU sort/coreutils locally.
CONTAINER_NAME="numou-pg-concurrency-$$"
PORT="${NUMOU_TEST_PG_PORT:-55440}"
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

# Race N independent sessions to allocate revisions for one BOQ. The upsert
# lock should serialize increments and produce each number exactly once.
BOQ_ID='61000000-0000-0000-0000-000000000001'
N="${NUMOU_CONCURRENCY_WORKERS:-16}"
if ! [[ "$N" =~ ^[0-9]+$ ]] || (( N < 2 || N > 100 )); then
  echo "NUMOU_CONCURRENCY_WORKERS must be between 2 and 100" >&2; exit 2
fi
for i in $(seq 1 "$N"); do
  (
    docker exec "$CONTAINER_NAME" psql -Atq -v ON_ERROR_STOP=1 -U postgres -d numou_test \
      -c "INSERT INTO construction_boq_revision_counter (tenant_id,boq_id,last_allocated_revision) VALUES ('tenant-race','$BOQ_ID',1) ON CONFLICT (tenant_id,boq_id) DO UPDATE SET last_allocated_revision=construction_boq_revision_counter.last_allocated_revision+1, updated_at=now() RETURNING last_allocated_revision" \
      > "$TMP_DIR/revision-$i"
  ) &
done
wait
cat "$TMP_DIR"/revision-* | sort -n > "$TMP_DIR/revisions.sorted"
EXPECTED="$(seq 1 "$N" | sort -n | tr '\n' ' ')"
ACTUAL="$(tr '\n' ' ' < "$TMP_DIR/revisions.sorted")"
if [[ "$ACTUAL" != "$EXPECTED" ]]; then
  echo "FAIL: revision allocation was not unique/contiguous" >&2
  echo "expected: $EXPECTED" >&2; echo "actual:   $ACTUAL" >&2; exit 1
fi

# Race identical idempotency reservations. ON CONFLICT DO NOTHING means only
# one session inserts the tenant/key pair; all contenders can inspect the winner.
for i in $(seq 1 "$N"); do
  (
    docker exec "$CONTAINER_NAME" psql -Atq -v ON_ERROR_STOP=1 -U postgres -d numou_test \
      -c "INSERT INTO construction_command_idempotency (tenant_id,idempotency_key,operation,request_fingerprint,state,created_at) VALUES ('tenant-race','same-key','capture',repeat('a',64),'PROCESSING',now()) ON CONFLICT (tenant_id,idempotency_key) DO NOTHING RETURNING idempotency_key" \
      > "$TMP_DIR/idempotency-$i"
  ) &
done
wait
INSERT_WINNERS="$(grep -h -c '^same-key$' "$TMP_DIR"/idempotency-* | awk '{s+=$1} END {print s+0}')"
if [[ "$INSERT_WINNERS" != "1" ]]; then
  echo "FAIL: expected exactly one idempotency reservation winner; got $INSERT_WINNERS" >&2; exit 1
fi
ROWS="$(docker exec "$CONTAINER_NAME" psql -Atq -U postgres -d numou_test -c "SELECT count(*) FROM construction_command_idempotency WHERE tenant_id='tenant-race' AND idempotency_key='same-key'")"
[[ "$ROWS" == "1" ]] || { echo "FAIL: expected one idempotency row, got $ROWS" >&2; exit 1; }

# The same key remains independently reservable by another tenant.
OTHER_TENANT="$(docker exec "$CONTAINER_NAME" psql -Atq -v ON_ERROR_STOP=1 -U postgres -d numou_test -c "INSERT INTO construction_command_idempotency (tenant_id,idempotency_key,operation,request_fingerprint,state,created_at) VALUES ('tenant-other','same-key','capture',repeat('b',64),'PROCESSING',now()) RETURNING tenant_id")"
[[ "$OTHER_TENANT" == "tenant-other" ]] || { echo "FAIL: tenant isolation key scope failed" >&2; exit 1; }

# Identical BOQ UUIDs in distinct tenants must have independent revision counters.
ISOLATED="$(docker exec "$CONTAINER_NAME" psql -Atq -v ON_ERROR_STOP=1 -U postgres -d numou_test -c "INSERT INTO construction_boq_revision_counter (tenant_id,boq_id,last_allocated_revision) VALUES ('tenant-other','$BOQ_ID',7) RETURNING last_allocated_revision")"
[[ "$ISOLATED" == "7" ]] || { echo "FAIL: revision counter was not tenant-isolated" >&2; exit 1; }

echo "PASS: $N concurrent revision allocations were unique and contiguous."
echo "PASS: $N competing identical idempotency reservations produced exactly one row."
echo "PASS: the same idempotency key is scoped independently per tenant."
echo "PASS: the same BOQ UUID has an independent revision counter per tenant."
echo "NOTE: schema-level concurrency test only; does not exercise Java adapters or live BOQ table mappings."
