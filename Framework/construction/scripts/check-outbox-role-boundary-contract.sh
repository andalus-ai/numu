#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST="$ROOT/scripts/postgres-outbox-role-boundary-test.sh"
for token in \
  'NOSUPERUSER' \
  'NOBYPASSRLS' \
  'ENABLE ROW LEVEL SECURITY' \
  'FORCE ROW LEVEL SECURITY' \
  "current_setting('app.tenant_id', true)" \
  'GRANT SELECT, UPDATE (status, lease_token)' \
  'GRANT INSERT ON outbox_role_boundary_fixture TO numou_command_writer_test' \
  'cross-tenant update unexpectedly changed event' \
  'relay role unexpectedly updated tenant ownership' \
  'command writer role unexpectedly updated relay state' \
  'rolbypassrls OR rolsuper'; do
  grep -Fq "$token" "$TEST" || { echo "FAIL: missing outbox role-boundary contract: $token" >&2; exit 1; }
done
echo 'PASS: outbox role-boundary contract (11 assertions)'
