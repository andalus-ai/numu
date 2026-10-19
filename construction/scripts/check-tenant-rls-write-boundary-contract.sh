#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="$ROOT/scripts/postgres-tenant-rls-write-boundary-test.sh"
assert_contains() {
  local needle="$1"
  if ! grep -Fq -- "$needle" "$SCRIPT"; then
    echo "FAIL: missing tenant RLS write-boundary contract: $needle" >&2
    exit 1
  fi
  printf 'PASS: %s\n' "$needle"
}
assert_contains 'ENABLE ROW LEVEL SECURITY'
assert_contains 'FORCE ROW LEVEL SECURITY'
assert_contains 'WITH CHECK (tenant_id = current_setting'
assert_contains 'NOLOGIN NOSUPERUSER'
assert_contains 'NOBYPASSRLS'
assert_contains 'SELECT count(*) FROM tenant_rls_write_fixture'
assert_contains 'INSERT INTO tenant_rls_write_fixture(row_id,tenant_id,payload) VALUES (10'
assert_contains "tenant_id='tenant-b'"
assert_contains 'UPDATE tenant_rls_write_fixture SET tenant_id='
assert_contains 'tenant-a:a-updated'
assert_contains 'FAIL: fixture role can bypass RLS'
echo 'PASS: tenant RLS write-boundary contract (11 assertions)'
