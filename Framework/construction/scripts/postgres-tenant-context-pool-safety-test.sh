#!/usr/bin/env bash
set -euo pipefail

# Disposable PostgreSQL test for transaction-local tenant context on reused connections.
# This proves the fixture protocol, not the actual Numou pool/runtime wiring.
CONTAINER_NAME="numou-pg-tenant-context-pool-$$"
PORT="${NUMOU_TEST_PG_PORT:-55445}"
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

psql() { docker exec -i "$CONTAINER_NAME" psql -X -q -v ON_ERROR_STOP=1 -U postgres -d numou_test "$@"; }

psql <<'SQL'
CREATE ROLE numou_tenant_context_test NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE TABLE tenant_context_pool_fixture (
  row_id integer PRIMARY KEY,
  tenant_id text NOT NULL,
  payload text NOT NULL
);
ALTER TABLE tenant_context_pool_fixture ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_context_pool_fixture FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_context_pool_policy ON tenant_context_pool_fixture
  USING (tenant_id = current_setting('app.tenant_id', true))
  WITH CHECK (tenant_id = current_setting('app.tenant_id', true));
GRANT USAGE ON SCHEMA public TO numou_tenant_context_test;
GRANT SELECT ON tenant_context_pool_fixture TO numou_tenant_context_test;
INSERT INTO tenant_context_pool_fixture VALUES
  (1, 'tenant-a', 'a-secret'),
  (2, 'tenant-b', 'b-secret');
SQL

# Keep one physical PostgreSQL session open across multiple transactions to model a pooled connection.
RESULT="$(psql -At <<'SQL'
SET ROLE numou_tenant_context_test;
BEGIN;
SELECT set_config('app.tenant_id', 'tenant-a', true);
DO $$ BEGIN
  IF (SELECT count(*) FROM tenant_context_pool_fixture) <> 1
     OR (SELECT min(tenant_id) FROM tenant_context_pool_fixture) <> 'tenant-a' THEN
    RAISE EXCEPTION 'tenant A context did not isolate rows';
  END IF;
END $$;
COMMIT;

-- Same session, next transaction: no context must not inherit tenant A visibility.
BEGIN;
DO $$ BEGIN
  IF (SELECT count(*) FROM tenant_context_pool_fixture) <> 0 THEN
    RAISE EXCEPTION 'tenant context leaked across committed transactions';
  END IF;
END $$;
COMMIT;

-- Tenant B context is local to its transaction and sees only tenant B.
BEGIN;
SELECT set_config('app.tenant_id', 'tenant-b', true);
DO $$ BEGIN
  IF (SELECT count(*) FROM tenant_context_pool_fixture) <> 1
     OR (SELECT min(tenant_id) FROM tenant_context_pool_fixture) <> 'tenant-b' THEN
    RAISE EXCEPTION 'tenant B context did not isolate rows';
  END IF;
END $$;
COMMIT;

-- A rolled-back tenant A transaction must not affect the next borrower of this session.
BEGIN;
SELECT set_config('app.tenant_id', 'tenant-a', true);
DO $$ BEGIN
  IF (SELECT count(*) FROM tenant_context_pool_fixture) <> 1 THEN
    RAISE EXCEPTION 'tenant A context unavailable before rollback';
  END IF;
END $$;
ROLLBACK;
BEGIN;
DO $$ BEGIN
  IF (SELECT count(*) FROM tenant_context_pool_fixture) <> 0 THEN
    RAISE EXCEPTION 'tenant context leaked after rollback';
  END IF;
END $$;
COMMIT;

SELECT 'PASS: tenant-context-pool-safety';
SQL
)"
[[ "$RESULT" == *'PASS: tenant-context-pool-safety'* ]] || { echo "FAIL: tenant context pool safety result missing: $RESULT" >&2; exit 1; }

# Independent sessions must each remain scoped to their own authenticated server-side context.
A="$(psql -Atc "SET ROLE numou_tenant_context_test; BEGIN; SELECT set_config('app.tenant_id','tenant-a',true); SELECT string_agg(tenant_id, ',') FROM tenant_context_pool_fixture; COMMIT")"
B="$(psql -Atc "SET ROLE numou_tenant_context_test; BEGIN; SELECT set_config('app.tenant_id','tenant-b',true); SELECT string_agg(tenant_id, ',') FROM tenant_context_pool_fixture; COMMIT")"
[[ "$A" == *'tenant-a'* && "$A" != *'tenant-b'* ]] || { echo "FAIL: session A crossed tenant boundary: $A" >&2; exit 1; }
[[ "$B" == *'tenant-b'* && "$B" != *'tenant-a'* ]] || { echo "FAIL: session B crossed tenant boundary: $B" >&2; exit 1; }

echo 'PASS: transaction-local tenant context does not leak across commits or rollbacks on a reused session.'
echo 'PASS: independent sessions see only their own tenant rows.'
echo 'NOTE: isolated fixture only; verify the actual Numou authentication-to-transaction context propagation and connection-pool integration separately.'
