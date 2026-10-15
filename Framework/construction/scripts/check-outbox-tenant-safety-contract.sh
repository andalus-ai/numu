#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SQL="$ROOT/scripts/postgres-outbox-tenant-safety-test.sh"
DOC="$ROOT/db/test/postgres-outbox-tenant-safety-test.md"
[[ -f "$SQL" && -f "$DOC" ]]
grep -q "WHERE tenant_id='tenant-a'" "$SQL"
grep -q "WHERE tenant_id='tenant-b'" "$SQL"
grep -q 'CROSS_ACK' "$SQL"
grep -q 'available_at=now()+interval '\''1 hour' "$SQL"
grep -q 'attempts=attempts+1' "$SQL"
grep -q 'tenant_id IS NULL' "$SQL"
grep -q 'LegacyTenantlessEvent' "$SQL"
grep -q 'does not invoke PgOutboxRelayStore' "$SQL"
grep -q 'not a production-adapter test' "$DOC"
grep -q 'trusted execution context' "$DOC"
printf '%s\n' 'PASS: outbox tenant-safety contract (10 assertions)'
