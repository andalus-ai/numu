#!/usr/bin/env bash
set -euo pipefail

# Disposable PostgreSQL protocol test for BOQ capture -> review -> approval.
# Uses a tiny test-only live BOQ table because the source dump does not define
# the physical live BOQ schema. This validates SQL constraints/transaction shape,
# not invocation of the Java workflow adapter.
CONTAINER_NAME="numou-pg-boq-workflow-$$"
PORT="${SYIRKAH_TEST_PG_PORT:-55442}"
PASSWORD="numou_test_only"
cleanup() { docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true; }
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
  echo "Applying $(basename "$migration")"
  docker exec -i "$CONTAINER_NAME" psql -v ON_ERROR_STOP=1 -U postgres -d numou_test < "$migration"
done

docker exec -i "$CONTAINER_NAME" psql -v ON_ERROR_STOP=1 -U postgres -d numou_test <<'SQL'
CREATE TABLE test_live_boq (
  boq_id UUID PRIMARY KEY,
  project_id UUID NOT NULL,
  tenant_id TEXT NOT NULL,
  name TEXT NOT NULL,
  current_revision INTEGER NOT NULL CHECK (current_revision >= 1),
  status TEXT NOT NULL CHECK (status IN ('DRAFT','UNDER_REVIEW','APPROVED','SUPERSEDED','CLOSED'))
);
INSERT INTO test_live_boq VALUES
 ('11000000-0000-0000-0000-000000000001', '22000000-0000-0000-0000-000000000001',
  'tenant-workflow', 'Workflow BOQ', 1, 'DRAFT');

-- Capture snapshot, item, live metadata, and outbox event as one transaction.
BEGIN;
DO $$
DECLARE b uuid := '11000000-0000-0000-0000-000000000001';
        p uuid := '22000000-0000-0000-0000-000000000001';
        a uuid := '33000000-0000-0000-0000-000000000001';
        i uuid := '44000000-0000-0000-0000-000000000001';
        affected integer;
BEGIN
  PERFORM 1 FROM test_live_boq WHERE boq_id=b AND status='DRAFT' FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'capture could not lock draft BOQ'; END IF;
  INSERT INTO construction_boq_revision_snapshot
    (tenant_id, boq_id, project_id, revision_number, boq_name, status, calculation_policy_version, created_by, created_at, total_amount)
  VALUES ('tenant-workflow', b,p,1,'Workflow BOQ','DRAFT','boq-calc-v1',a,now(),25.00000000);
  INSERT INTO construction_boq_revision_item
    (tenant_id, boq_id, revision_number, item_id, wbs_node_id, item_code, description, item_type,
     quantity, quantity_unit, rate_amount, rate_unit, currency_code, line_amount)
  VALUES ('tenant-workflow', b,1,i,i,'W-1','Workflow test item','WORK',5,'m2',5,'m2','IDR',25);
  UPDATE test_live_boq SET current_revision=1 WHERE boq_id=b AND current_revision=1 AND status='DRAFT';
  GET DIAGNOSTICS affected = ROW_COUNT;
  IF affected <> 1 THEN RAISE EXCEPTION 'capture live BOQ CAS failed'; END IF;
  INSERT INTO construction_outbox_event
    (event_id, tenant_id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
  VALUES ('55000000-0000-0000-0000-000000000001','tenant-workflow','BOQ',b,
          'BoqRevisionCaptured',jsonb_build_object('boqId',b,'revisionNumber',1,'status','DRAFT'),now());
END $$;
COMMIT;

-- Submit transition: snapshot CAS + live status CAS + outbox in same transaction.
BEGIN;
DO $$
DECLARE b uuid := '11000000-0000-0000-0000-000000000001';
        a uuid := '33000000-0000-0000-0000-000000000002';
        affected integer;
BEGIN
  UPDATE construction_boq_revision_snapshot
     SET status='UNDER_REVIEW', submitted_by=a, submitted_at=now()
   WHERE tenant_id='tenant-workflow' AND boq_id=b AND revision_number=1 AND status='DRAFT';
  GET DIAGNOSTICS affected = ROW_COUNT;
  IF affected <> 1 THEN RAISE EXCEPTION 'snapshot submit CAS failed'; END IF;
  UPDATE test_live_boq SET status='UNDER_REVIEW'
   WHERE boq_id=b AND current_revision=1 AND status='DRAFT';
  GET DIAGNOSTICS affected = ROW_COUNT;
  IF affected <> 1 THEN RAISE EXCEPTION 'live BOQ submit CAS failed'; END IF;
  INSERT INTO construction_outbox_event
    (event_id, tenant_id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
  VALUES ('55000000-0000-0000-0000-000000000002','tenant-workflow','BOQ',b,
          'BoqRevisionSubmittedForReview',jsonb_build_object('boqId',b,'revisionNumber',1,'status','UNDER_REVIEW'),now());
END $$;
COMMIT;

-- Approve transition.
BEGIN;
DO $$
DECLARE b uuid := '11000000-0000-0000-0000-000000000001';
        a uuid := '33000000-0000-0000-0000-000000000003';
        affected integer;
BEGIN
  UPDATE construction_boq_revision_snapshot
     SET status='APPROVED', approved_by=a, approved_at=now()
   WHERE tenant_id='tenant-workflow' AND boq_id=b AND revision_number=1 AND status='UNDER_REVIEW';
  GET DIAGNOSTICS affected = ROW_COUNT;
  IF affected <> 1 THEN RAISE EXCEPTION 'snapshot approve CAS failed'; END IF;
  UPDATE test_live_boq SET status='APPROVED'
   WHERE boq_id=b AND current_revision=1 AND status='UNDER_REVIEW';
  GET DIAGNOSTICS affected = ROW_COUNT;
  IF affected <> 1 THEN RAISE EXCEPTION 'live BOQ approve CAS failed'; END IF;
  INSERT INTO construction_outbox_event
    (event_id, tenant_id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
  VALUES ('55000000-0000-0000-0000-000000000003','tenant-workflow','BOQ',b,
          'BoqRevisionApproved',jsonb_build_object('boqId',b,'revisionNumber',1,'status','APPROVED'),now());
END $$;
COMMIT;

DO $$
DECLARE b uuid := '11000000-0000-0000-0000-000000000001';
BEGIN
  IF (SELECT status FROM test_live_boq WHERE boq_id=b) <> 'APPROVED' THEN
    RAISE EXCEPTION 'live BOQ did not reach APPROVED';
  END IF;
  IF (SELECT status FROM construction_boq_revision_snapshot WHERE tenant_id='tenant-workflow' AND boq_id=b AND revision_number=1) <> 'APPROVED' THEN
    RAISE EXCEPTION 'snapshot did not reach APPROVED';
  END IF;
  IF (SELECT count(*) FROM construction_boq_revision_item WHERE tenant_id='tenant-workflow' AND boq_id=b AND revision_number=1) <> 1 THEN
    RAISE EXCEPTION 'snapshot item was not retained';
  END IF;
  IF (SELECT count(*) FROM construction_outbox_event WHERE aggregate_id=b AND tenant_id='tenant-workflow') <> 3 THEN
    RAISE EXCEPTION 'expected exactly three tenant-tagged workflow events';
  END IF;
  -- A duplicate/stale submission cannot transition the approved snapshot.
  UPDATE construction_boq_revision_snapshot SET status='UNDER_REVIEW', submitted_by=gen_random_uuid(), submitted_at=now()
   WHERE tenant_id='tenant-workflow' AND boq_id=b AND revision_number=1 AND status='DRAFT';
  IF FOUND THEN RAISE EXCEPTION 'stale duplicate submission unexpectedly succeeded'; END IF;
END $$;

-- A failure after a snapshot write must roll back both it and its outbox event.
BEGIN;
UPDATE construction_boq_revision_snapshot SET status='SUPERSEDED'
 WHERE tenant_id='tenant-workflow' AND boq_id='11000000-0000-0000-0000-000000000001' AND revision_number=1;
INSERT INTO construction_outbox_event
 (event_id, tenant_id, aggregate_type, aggregate_id, event_type, payload, occurred_at)
VALUES ('55000000-0000-0000-0000-000000000099','tenant-workflow','BOQ',
        '11000000-0000-0000-0000-000000000001','ShouldRollback','{}'::jsonb,now());
ROLLBACK;
DO $$ BEGIN
  IF (SELECT status FROM construction_boq_revision_snapshot
       WHERE tenant_id='tenant-workflow' AND boq_id='11000000-0000-0000-0000-000000000001' AND revision_number=1) <> 'APPROVED' THEN
    RAISE EXCEPTION 'rollback failed to restore approved snapshot';
  END IF;
  IF EXISTS (SELECT 1 FROM construction_outbox_event WHERE event_id='55000000-0000-0000-0000-000000000099') THEN
    RAISE EXCEPTION 'rollback left an outbox event';
  END IF;
END $$;
SELECT 'Numou BOQ workflow protocol test PASSED' AS result;
SQL

echo "PASS: BOQ capture/review/approval protocol assertions succeeded."
echo "NOTE: this is a database protocol test using a test-only live BOQ table, not a Java adapter integration test."
