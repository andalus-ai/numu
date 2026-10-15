#!/usr/bin/env bash
set -euo pipefail

# Disposable PostgreSQL protocol test for atomic BOQ + idempotency + outbox writes.
# This validates database transaction semantics using a test-only live BOQ table;
# it does not invoke the Java workflow or PgTransactionalCommandRunner.
CONTAINER_NAME="syirkah-pg-command-atomicity-$$"
PORT="${SYIRKAH_TEST_PG_PORT:-55447}"
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
  echo "Applying $(basename "$migration")"
  docker exec -i "$CONTAINER_NAME" psql -v ON_ERROR_STOP=1 -U postgres -d syirkah_test < "$migration"
done

docker exec -i "$CONTAINER_NAME" psql -v ON_ERROR_STOP=1 -U postgres -d syirkah_test <<'SQL'
CREATE TABLE test_live_boq (
  tenant_id TEXT NOT NULL,
  boq_id UUID NOT NULL,
  current_revision INTEGER NOT NULL DEFAULT 0 CHECK (current_revision >= 0),
  status TEXT NOT NULL CHECK (status IN ('DRAFT','UNDER_REVIEW','APPROVED')),
  PRIMARY KEY (tenant_id, boq_id)
);
INSERT INTO test_live_boq(tenant_id,boq_id,current_revision,status)
VALUES ('tenant-atomicity','61000000-0000-0000-0000-000000000001',0,'DRAFT');

-- Failure after all logical writes: the inner PL/pgSQL exception block acts as a
-- savepoint. PostgreSQL rolls back every write made inside that block.
DO $$
DECLARE b UUID := '61000000-0000-0000-0000-000000000001';
BEGIN
  BEGIN
    INSERT INTO construction_command_idempotency
      (tenant_id,idempotency_key,operation,request_fingerprint,state,created_at)
    VALUES ('tenant-atomicity','failed-key','capture','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','PROCESSING',now());
    INSERT INTO construction_boq_revision_snapshot
      (tenant_id,boq_id,project_id,revision_number,boq_name,status,calculation_policy_version,created_by,created_at,total_amount)
    VALUES ('tenant-atomicity',b,'62000000-0000-0000-0000-000000000001',1,'Atomicity BOQ','DRAFT','v1',
            '63000000-0000-0000-0000-000000000001',now(),10);
    INSERT INTO construction_boq_revision_item
      (tenant_id,boq_id,revision_number,item_id,wbs_node_id,item_code,description,item_type,quantity,quantity_unit,
       rate_amount,rate_unit,currency_code,line_amount)
    VALUES ('tenant-atomicity',b,1,'64000000-0000-0000-0000-000000000001','65000000-0000-0000-0000-000000000001',
            'A-1','Rollback item','WORK',2,'m2',5,'m2','IDR',10);
    UPDATE test_live_boq SET current_revision=1 WHERE tenant_id='tenant-atomicity' AND boq_id=b AND status='DRAFT';
    INSERT INTO construction_outbox_event
      (event_id,tenant_id,aggregate_type,aggregate_id,event_type,payload,occurred_at)
    VALUES ('66000000-0000-0000-0000-000000000001','tenant-atomicity','BOQ',b,'ShouldRollback','{}'::jsonb,now());
    RAISE EXCEPTION 'injected failure after command writes';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM <> 'injected failure after command writes' THEN RAISE; END IF;
  END;
END $$;

DO $$
DECLARE b UUID := '61000000-0000-0000-0000-000000000001';
BEGIN
  IF EXISTS (SELECT 1 FROM construction_command_idempotency WHERE tenant_id='tenant-atomicity' AND idempotency_key='failed-key') THEN RAISE EXCEPTION 'failed idempotency row survived rollback'; END IF;
  IF EXISTS (SELECT 1 FROM construction_boq_revision_snapshot WHERE tenant_id='tenant-atomicity' AND boq_id=b) THEN RAISE EXCEPTION 'failed snapshot survived rollback'; END IF;
  IF EXISTS (SELECT 1 FROM construction_boq_revision_item WHERE tenant_id='tenant-atomicity' AND boq_id=b) THEN RAISE EXCEPTION 'failed item survived rollback'; END IF;
  IF EXISTS (SELECT 1 FROM construction_outbox_event WHERE event_id='66000000-0000-0000-0000-000000000001') THEN RAISE EXCEPTION 'failed outbox event survived rollback'; END IF;
  IF (SELECT current_revision FROM test_live_boq WHERE tenant_id='tenant-atomicity' AND boq_id=b) <> 0 THEN RAISE EXCEPTION 'live BOQ update survived rollback'; END IF;
END $$;

-- Successful command: business state, idempotency outcome, and outbox event all persist.
BEGIN;
INSERT INTO construction_command_idempotency
  (tenant_id,idempotency_key,operation,request_fingerprint,state,created_at)
VALUES ('tenant-atomicity','success-key','capture','bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb','PROCESSING',now());
INSERT INTO construction_boq_revision_snapshot
  (tenant_id,boq_id,project_id,revision_number,boq_name,status,calculation_policy_version,created_by,created_at,total_amount)
VALUES ('tenant-atomicity','61000000-0000-0000-0000-000000000001','62000000-0000-0000-0000-000000000001',1,
        'Atomicity BOQ','DRAFT','v1','63000000-0000-0000-0000-000000000001',now(),10);
INSERT INTO construction_boq_revision_item
  (tenant_id,boq_id,revision_number,item_id,wbs_node_id,item_code,description,item_type,quantity,quantity_unit,
   rate_amount,rate_unit,currency_code,line_amount)
VALUES ('tenant-atomicity','61000000-0000-0000-0000-000000000001',1,'64000000-0000-0000-0000-000000000001',
        '65000000-0000-0000-0000-000000000001','A-1','Success item','WORK',2,'m2',5,'m2','IDR',10);
UPDATE test_live_boq SET current_revision=1 WHERE tenant_id='tenant-atomicity'
  AND boq_id='61000000-0000-0000-0000-000000000001' AND current_revision=0 AND status='DRAFT';
INSERT INTO construction_outbox_event
  (event_id,tenant_id,aggregate_type,aggregate_id,event_type,payload,occurred_at)
VALUES ('66000000-0000-0000-0000-000000000002','tenant-atomicity','BOQ',
        '61000000-0000-0000-0000-000000000001','BoqRevisionCaptured','{"revisionNumber":1}'::jsonb,now());
UPDATE construction_command_idempotency SET state='COMPLETED',response_json='{"revisionNumber":1}'::jsonb,completed_at=now()
 WHERE tenant_id='tenant-atomicity' AND idempotency_key='success-key' AND state='PROCESSING';
COMMIT;

-- A retry with the same tenant/key must not create a second reservation or event.
INSERT INTO construction_command_idempotency
  (tenant_id,idempotency_key,operation,request_fingerprint,state,created_at)
VALUES ('tenant-atomicity','success-key','capture','bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb','PROCESSING',now())
ON CONFLICT (tenant_id,idempotency_key) DO NOTHING;
DO $$
BEGIN
  IF (SELECT count(*) FROM construction_command_idempotency WHERE tenant_id='tenant-atomicity' AND idempotency_key='success-key') <> 1 THEN RAISE EXCEPTION 'duplicate retry created another idempotency record'; END IF;
  IF (SELECT state FROM construction_command_idempotency WHERE tenant_id='tenant-atomicity' AND idempotency_key='success-key') <> 'COMPLETED' THEN RAISE EXCEPTION 'completed outcome was overwritten'; END IF;
  IF (SELECT response_json->>'revisionNumber' FROM construction_command_idempotency WHERE tenant_id='tenant-atomicity' AND idempotency_key='success-key') <> '1' THEN RAISE EXCEPTION 'stored response changed on retry'; END IF;
  IF (SELECT count(*) FROM construction_outbox_event WHERE tenant_id='tenant-atomicity' AND event_type='BoqRevisionCaptured') <> 1 THEN RAISE EXCEPTION 'retry duplicated outbox event'; END IF;
  IF (SELECT current_revision FROM test_live_boq WHERE tenant_id='tenant-atomicity' AND boq_id='61000000-0000-0000-0000-000000000001') <> 1 THEN RAISE EXCEPTION 'successful live BOQ revision not persisted'; END IF;
END $$;
SELECT 'Syirkah command atomicity protocol test PASSED' AS result;
SQL

echo "PASS: command atomicity and retry protocol assertions succeeded."
echo "NOTE: SQL-level protocol test only; it does not invoke Java command handlers or PgTransactionalCommandRunner."
