#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA="$ROOT/construction-adapter/src/test/java/tech/kayys/syirkah/construction/adapter/postgres/context/PgTenantContextTransactionIntegrationTest.java"
for expected in \
  'committedTenantContextDoesNotLeakIntoNextTransactionOnSameConnection' \
  'rolledBackTenantContextDoesNotLeakIntoNextTransactionOnSameConnection' \
  "set_config('app.tenant_id', \$1, true)" \
  "current_setting('app.tenant_id', true)" \
  'injected rollback' \
  'connection.close().await().indefinitely()'; do
  grep -Fq "$expected" "$JAVA" || { echo "FAIL: missing tenant-context transaction contract: $expected" >&2; exit 1; }
done
printf '%s\n' 'PASS: PostgreSQL tenant-context transaction contract (6 assertions)'
