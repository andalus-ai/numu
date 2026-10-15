#!/usr/bin/env bash
set -euo pipefail

# Disposable PostgreSQL test of least-privilege role grants and tenant RLS.
# This uses an isolated fixture table; it is not a claim about production grants.
CONTAINER_NAME="syirkah-pg-outbox-role-boundary-$$"
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

psql() { docker exec -i "$CONTAINER_NAME" psql -X -q -v ON_ERROR_STOP=1 -U postgres -d syirkah_test "$@"; }

psql <<'SQL'
CREATE ROLE syirkah_outbox_relay_test NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE syirkah_command_writer_test NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE TABLE outbox_role_boundary_fixture (
  event_id integer PRIMARY KEY,
  tenant_id text NOT NULL,
  status text NOT NULL,
  lease_token text
);
ALTER TABLE outbox_role_boundary_fixture ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_role_boundary_fixture FORCE ROW LEVEL SECURITY;
CREATE POLICY outbox_relay_tenant_policy ON outbox_role_boundary_fixture
  USING (tenant_id = current_setting('app.tenant_id', true))
  WITH CHECK (tenant_id = current_setting('app.tenant_id', true));
GRANT USAGE ON SCHEMA public TO syirkah_outbox_relay_test, syirkah_command_writer_test;
GRANT SELECT, UPDATE (status, lease_token) ON outbox_role_boundary_fixture TO syirkah_outbox_relay_test;
GRANT INSERT ON outbox_role_boundary_fixture TO syirkah_command_writer_test;
INSERT INTO outbox_role_boundary_fixture VALUES
  (1, 'tenant-a', 'PENDING', NULL),
  (2, 'tenant-b', 'PENDING', NULL);
SQL

VISIBLE="$(psql -Atc "SET ROLE syirkah_outbox_relay_test; SET app.tenant_id='tenant-a'; SELECT string_agg(event_id::text, ',' ORDER BY event_id) FROM outbox_role_boundary_fixture")"
[[ "$VISIBLE" == '1' ]] || { echo "FAIL: relay role tenant RLS returned unexpected rows: $VISIBLE" >&2; exit 1; }

CROSS_UPDATE="$(psql -Atc "SET ROLE syirkah_outbox_relay_test; SET app.tenant_id='tenant-a'; UPDATE outbox_role_boundary_fixture SET status='PUBLISHED' WHERE event_id=2 RETURNING event_id")"
[[ -z "$CROSS_UPDATE" ]] || { echo "FAIL: cross-tenant update unexpectedly changed event: $CROSS_UPDATE" >&2; exit 1; }

# The relay can mutate only delivery-state columns, not ownership or payload-like columns.
if psql -c "SET ROLE syirkah_outbox_relay_test; SET app.tenant_id='tenant-a'; UPDATE outbox_role_boundary_fixture SET tenant_id='tenant-a' WHERE event_id=1" >/dev/null 2>&1; then
  echo 'FAIL: relay role unexpectedly updated tenant ownership' >&2; exit 1
fi
if psql -c "SET ROLE syirkah_outbox_relay_test; SET app.tenant_id='tenant-a'; DELETE FROM outbox_role_boundary_fixture WHERE event_id=1" >/dev/null 2>&1; then
  echo 'FAIL: relay role unexpectedly deleted an outbox row' >&2; exit 1
fi
if psql -c "SET ROLE syirkah_command_writer_test; SET app.tenant_id='tenant-a'; UPDATE outbox_role_boundary_fixture SET status='PUBLISHED' WHERE event_id=1" >/dev/null 2>&1; then
  echo 'FAIL: command writer role unexpectedly updated relay state' >&2; exit 1
fi

# Relay can update allowed delivery state for its own tenant.
psql -c "SET ROLE syirkah_outbox_relay_test; SET app.tenant_id='tenant-a'; UPDATE outbox_role_boundary_fixture SET status='PUBLISHING', lease_token='lease-a' WHERE event_id=1" >/dev/null
STATE="$(psql -Atc "SELECT status || ':' || coalesce(lease_token,'') FROM outbox_role_boundary_fixture WHERE event_id=1")"
[[ "$STATE" == 'PUBLISHING:lease-a' ]] || { echo "FAIL: permitted relay state update missing: $STATE" >&2; exit 1; }

# Ensure the test role itself is not privileged to bypass row-level security.
BYPASS="$(psql -Atc "SELECT rolbypassrls OR rolsuper FROM pg_roles WHERE rolname='syirkah_outbox_relay_test'")"
[[ "$BYPASS" == 'f' ]] || { echo 'FAIL: relay fixture role can bypass row-level security' >&2; exit 1; }

echo 'PASS: relay role sees and updates only its tenant rows under RLS.'
echo 'PASS: relay role cannot change tenant ownership or delete rows; command writer cannot mutate relay state.'
echo 'PASS: relay role is neither superuser nor BYPASSRLS.'
echo 'NOTE: isolated permission fixture only; reconcile roles, RLS policies, and grants with canonical Syirkah migrations before production use.'
