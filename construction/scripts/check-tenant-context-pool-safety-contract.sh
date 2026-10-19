#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST="$ROOT/scripts/postgres-tenant-context-pool-safety-test.sh"
for token in \
  'NOBYPASSRLS' \
  'ENABLE ROW LEVEL SECURITY' \
  'FORCE ROW LEVEL SECURITY' \
  "current_setting('app.tenant_id', true)" \
  "set_config('app.tenant_id', 'tenant-a', true)" \
  "set_config('app.tenant_id', 'tenant-b', true)" \
  'tenant context leaked across committed transactions' \
  'tenant context leaked after rollback' \
  'Keep one physical PostgreSQL session open across multiple transactions' \
  'Independent sessions must each remain scoped' \
  'connection-pool integration separately'; do
  grep -Fq "$token" "$TEST" || { echo "FAIL: missing tenant-context pool-safety contract: $token" >&2; exit 1; }
done
echo 'PASS: tenant-context pool-safety contract (11 assertions)'
