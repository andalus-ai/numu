#!/usr/bin/env bash
set -euo pipefail

# Disposable PostgreSQL smoke test for the migration starter.
# Requires Docker. Does not connect to or mutate an existing database.
CONTAINER_NAME="syirkah-pg-smoke-$$"
PORT="${SYIRKAH_TEST_PG_PORT:-55439}"
PASSWORD="syirkah_test_only"
cleanup() {
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
}
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
  echo "Applying $(basename "$migration")"
  docker exec -i "$CONTAINER_NAME" psql -v ON_ERROR_STOP=1 -U postgres -d syirkah_test < "$migration"
done

docker exec -i "$CONTAINER_NAME" psql -v ON_ERROR_STOP=1 -U postgres -d syirkah_test <<'SQL'
DO $$
DECLARE
  b uuid := '10000000-0000-0000-0000-000000000001';
  p uuid := '20000000-0000-0000-0000-000000000001';
  actor uuid := '30000000-0000-0000-0000-000000000001';
  item uuid := '40000000-0000-0000-0000-000000000001';
  event uuid := '50000000-0000-0000-0000-000000000001';
BEGIN
  -- Happy-path snapshot + item + tenant-scoped outbox insert.
  INSERT INTO construction_boq_revision_snapshot
    (tenant_id, boq_id, project_id, revision_number, boq_name, status, calculation_policy_version, created_by, created_at, total_amount)
  VALUES ('tenant-smoke', b, p, 1, 'smoke BOQ', 'DRAFT', 'v1', actor, now(), 12.50000000);
  INSERT INTO construction_boq_revision_item
    (tenant_id, boq_id, revision_number, item_id, wbs_node_id, item_code, description, item_type,
     quantity, quantity_unit, rate_amount, rate_unit, currency_code, line_amount)
  VALUES ('tenant-smoke', b, 1, item, item, 'A-1', 'smoke item', 'WORK', 2, 'm2', 6.25, 'm2', 'IDR', 12.5);
  INSERT INTO construction_outbox_event
    (event_id, tenant_id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
  VALUES (event, 'tenant-smoke', 'BOQ', b, 'BoqRevisionCaptured', '{"revision":1}'::jsonb, now());

  IF NOT EXISTS (SELECT 1 FROM construction_boq_revision_snapshot WHERE tenant_id='tenant-smoke' AND boq_id=b AND revision_number=1) THEN
    RAISE EXCEPTION 'snapshot read-back failed';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM construction_outbox_event WHERE event_id=event AND tenant_id='tenant-smoke') THEN
    RAISE EXCEPTION 'tenant-scoped outbox read-back failed';
  END IF;

  -- Same BOQ/revision/item UUIDs are independently addressable in another tenant.
  INSERT INTO construction_boq_revision_snapshot
    (tenant_id, boq_id, project_id, revision_number, boq_name, status, calculation_policy_version, created_by, created_at, total_amount)
  VALUES ('tenant-other', b, p, 1, 'other tenant BOQ', 'DRAFT', 'v1', actor, now(), 7);
  INSERT INTO construction_boq_revision_item
    (tenant_id, boq_id, revision_number, item_id, wbs_node_id, item_code, description, item_type,
     quantity, quantity_unit, rate_amount, rate_unit, currency_code, line_amount)
  VALUES ('tenant-other', b, 1, item, item, 'A-1', 'other tenant item', 'WORK', 1, 'm2', 7, 'm2', 'IDR', 7);
  IF (SELECT count(*) FROM construction_boq_revision_snapshot WHERE boq_id=b AND revision_number=1) <> 2 THEN
    RAISE EXCEPTION 'same BOQ/revision was not independently scoped per tenant';
  END IF;
  IF (SELECT boq_name FROM construction_boq_revision_snapshot WHERE tenant_id='tenant-smoke' AND boq_id=b AND revision_number=1) <> 'smoke BOQ' THEN
    RAISE EXCEPTION 'tenant A snapshot lookup leaked or selected tenant B data';
  END IF;

  -- Duplicate revision must fail due to primary key.
  BEGIN
    INSERT INTO construction_boq_revision_snapshot
      (tenant_id, boq_id, project_id, revision_number, boq_name, status, calculation_policy_version, created_by, created_at)
    VALUES ('tenant-smoke', b, p, 1, 'duplicate', 'DRAFT', 'v1', actor, now());
    RAISE EXCEPTION 'duplicate revision unexpectedly succeeded';
  EXCEPTION WHEN unique_violation THEN
    NULL;
  END;

  -- Snapshot item FK must reject a missing revision header.
  BEGIN
    INSERT INTO construction_boq_revision_item
      (tenant_id, boq_id, revision_number, item_id, wbs_node_id, item_code, description, item_type,
       quantity, quantity_unit, rate_amount, rate_unit, currency_code, line_amount)
    VALUES ('tenant-smoke', b, 99, gen_random_uuid(), item, 'BAD', 'bad FK', 'WORK', 1, 'm', 1, 'm', 'IDR', 1);
    RAISE EXCEPTION 'orphan revision item unexpectedly succeeded';
  EXCEPTION WHEN foreign_key_violation THEN
    NULL;
  END;

  -- Tenant + idempotency key is unique.
  INSERT INTO construction_command_idempotency
    (tenant_id, idempotency_key, operation, request_fingerprint, state, created_at)
  VALUES ('tenant-smoke', 'key-1', 'capture', repeat('a',64), 'PROCESSING', now());
  BEGIN
    INSERT INTO construction_command_idempotency
      (tenant_id, idempotency_key, operation, request_fingerprint, state, created_at)
    VALUES ('tenant-smoke', 'key-1', 'capture', repeat('a',64), 'PROCESSING', now());
    RAISE EXCEPTION 'duplicate idempotency key unexpectedly succeeded';
  EXCEPTION WHEN unique_violation THEN
    NULL;
  END;
END $$;

-- Verify transaction rollback leaves no partial snapshot/outbox/counter writes.
BEGIN;
INSERT INTO construction_boq_revision_counter (tenant_id, boq_id, last_allocated_revision)
VALUES ('tenant-smoke', '10000000-0000-0000-0000-000000000099', 1);
INSERT INTO construction_boq_revision_snapshot
  (tenant_id, boq_id, project_id, revision_number, boq_name, status, calculation_policy_version, created_by, created_at)
VALUES ('tenant-smoke', '10000000-0000-0000-0000-000000000099', '20000000-0000-0000-0000-000000000099', 1,
        'rollback', 'DRAFT', 'v1', '30000000-0000-0000-0000-000000000099', now());
INSERT INTO construction_outbox_event
  (event_id, tenant_id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
VALUES ('50000000-0000-0000-0000-000000000099', 'tenant-smoke', 'BOQ',
        '10000000-0000-0000-0000-000000000099', 'MustRollback', '{}'::jsonb, now());
ROLLBACK;

DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM construction_boq_revision_counter WHERE tenant_id='tenant-smoke' AND boq_id='10000000-0000-0000-0000-000000000099')
     OR EXISTS (SELECT 1 FROM construction_boq_revision_snapshot WHERE tenant_id='tenant-smoke' AND boq_id='10000000-0000-0000-0000-000000000099')
     OR EXISTS (SELECT 1 FROM construction_outbox_event WHERE event_id='50000000-0000-0000-0000-000000000099') THEN
    RAISE EXCEPTION 'rollback left partial writes';
  END IF;
END $$;

SELECT 'Numou PostgreSQL schema smoke test PASSED' AS result;
SQL

echo "PASS: migrations and database-level smoke assertions succeeded."
echo "NOTE: this does not compile or invoke Java adapters; run the project Maven integration suite separately."
