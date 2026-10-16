#!/usr/bin/env bash
set -euo pipefail

# Disposable PostgreSQL test for tenant-scoped BOQ revision storage and outbox predicates.
# Requires Docker. Does not touch an existing database and does not execute Java code.
CONTAINER_NAME="syirkah-pg-tenant-$$"
PORT="${SYIRKAH_TEST_PG_PORT:-55442}"
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

docker exec -i "$CONTAINER_NAME" psql -v ON_ERROR_STOP=1 -U postgres -d syirkah_test <<'SQL'
CREATE TABLE test_revision_snapshot (
  tenant_id text NOT NULL, boq_id uuid NOT NULL, revision_number integer NOT NULL,
  project_id uuid NOT NULL, status text NOT NULL,
  PRIMARY KEY (tenant_id, boq_id, revision_number)
);
CREATE TABLE test_revision_item (
  tenant_id text NOT NULL, boq_id uuid NOT NULL, revision_number integer NOT NULL,
  item_id uuid NOT NULL, description text NOT NULL,
  PRIMARY KEY (tenant_id, boq_id, revision_number, item_id),
  FOREIGN KEY (tenant_id, boq_id, revision_number)
    REFERENCES test_revision_snapshot (tenant_id, boq_id, revision_number)
);
CREATE TABLE test_revision_counter (
  tenant_id text NOT NULL, boq_id uuid NOT NULL, next_revision integer NOT NULL,
  PRIMARY KEY (tenant_id, boq_id)
);
CREATE TABLE test_outbox (
  event_id uuid PRIMARY KEY, tenant_id text NOT NULL, aggregate_id uuid NOT NULL,
  event_type text NOT NULL, status text NOT NULL DEFAULT 'PENDING'
);

-- Same business identifiers can be represented in two tenant scopes without collision.
INSERT INTO test_revision_snapshot VALUES
 ('tenant-a','10000000-0000-0000-0000-000000000001',1,'20000000-0000-0000-0000-000000000001','DRAFT'),
 ('tenant-b','10000000-0000-0000-0000-000000000001',1,'20000000-0000-0000-0000-000000000002','APPROVED');
INSERT INTO test_revision_item VALUES
 ('tenant-a','10000000-0000-0000-0000-000000000001',1,'30000000-0000-0000-0000-000000000001','Tenant A item'),
 ('tenant-b','10000000-0000-0000-0000-000000000001',1,'30000000-0000-0000-0000-000000000001','Tenant B item');
INSERT INTO test_revision_counter VALUES
 ('tenant-a','10000000-0000-0000-0000-000000000001',2),
 ('tenant-b','10000000-0000-0000-0000-000000000001',9);
INSERT INTO test_outbox VALUES
 ('50000000-0000-0000-0000-000000000001','tenant-a','10000000-0000-0000-0000-000000000001','BoqRevisionCaptured','PENDING'),
 ('50000000-0000-0000-0000-000000000002','tenant-b','10000000-0000-0000-0000-000000000001','BoqRevisionApproved','PENDING');

DO $$
DECLARE
  tenant_a text := 'tenant-a';
  v_boq_id uuid := '10000000-0000-0000-0000-000000000001';
  found_count integer;
  changed_count integer;
  v_next_revision integer;
BEGIN
  -- Qualify column references and keep local variables distinct from column names.
  SELECT count(*) INTO found_count FROM test_revision_snapshot AS s
   WHERE s.tenant_id=tenant_a AND s.boq_id=v_boq_id AND s.revision_number=1;
  IF found_count <> 1 THEN RAISE EXCEPTION 'own-tenant snapshot lookup failed'; END IF;

  SELECT count(*) INTO found_count FROM test_revision_snapshot AS s
   WHERE s.tenant_id=tenant_a AND s.status='APPROVED';
  IF found_count <> 0 THEN RAISE EXCEPTION 'cross-tenant snapshot state leaked'; END IF;

  SELECT count(*) INTO found_count FROM test_revision_item AS i
   WHERE i.tenant_id=tenant_a AND i.boq_id=v_boq_id
     AND EXISTS (SELECT 1 FROM test_revision_snapshot AS s
                  WHERE s.tenant_id=i.tenant_id AND s.boq_id=i.boq_id
                    AND s.revision_number=i.revision_number);
  IF found_count <> 1 THEN RAISE EXCEPTION 'tenant-scoped item join returned unexpected rows'; END IF;

  SELECT c.next_revision INTO v_next_revision FROM test_revision_counter AS c
   WHERE c.tenant_id=tenant_a AND c.boq_id=v_boq_id;
  IF v_next_revision <> 2 THEN RAISE EXCEPTION 'tenant-scoped counter lookup returned wrong value'; END IF;

  UPDATE test_revision_snapshot AS s SET status='UNDER_REVIEW'
   WHERE s.tenant_id=tenant_a AND s.boq_id=v_boq_id AND s.status='APPROVED';
  GET DIAGNOSTICS changed_count = ROW_COUNT;
  IF changed_count <> 0 THEN RAISE EXCEPTION 'cross-tenant snapshot update unexpectedly changed rows'; END IF;

  UPDATE test_revision_counter AS c SET next_revision=c.next_revision+1
   WHERE c.tenant_id=tenant_a AND c.boq_id=v_boq_id AND c.next_revision=9;
  GET DIAGNOSTICS changed_count = ROW_COUNT;
  IF changed_count <> 0 THEN RAISE EXCEPTION 'cross-tenant counter update unexpectedly changed rows'; END IF;

  SELECT count(*) INTO found_count FROM test_outbox AS o
   WHERE o.tenant_id=tenant_a AND o.status='PENDING';
  IF found_count <> 1 THEN RAISE EXCEPTION 'tenant-filtered outbox poll returned unexpected rows'; END IF;

  -- An item cannot claim tenant A while pointing at a snapshot owned by tenant B.
  BEGIN
    INSERT INTO test_revision_item VALUES
      ('tenant-a',v_boq_id,1,'30000000-0000-0000-0000-000000000099','Invalid cross-tenant item');
    RAISE EXCEPTION 'cross-tenant item/snapshot reference unexpectedly succeeded';
  EXCEPTION WHEN foreign_key_violation THEN
    NULL; -- expected composite tenant-scoped FK rejection
  END;
END $$;
SELECT 'Numou tenant isolation SQL protocol test PASSED' AS result;
SQL

echo "PASS: tenant-scoped snapshot/item/counter reads and writes, composite FK, and outbox filtering behaved as expected."
echo "NOTE: this validates a representative SQL protocol in a disposable database; it does not compile or invoke Java adapters."
