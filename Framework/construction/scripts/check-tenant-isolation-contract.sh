#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SQL="$ROOT/scripts/postgres-tenant-isolation-test.sh"
DOC="$ROOT/db/test/postgres-tenant-isolation-test.md"
[[ -f "$SQL" && -f "$DOC" ]]
grep -q 'test_revision_snapshot' "$SQL"
grep -q 'test_revision_item' "$SQL"
grep -q 'test_revision_counter' "$SQL"
grep -q 'test_outbox' "$SQL"
grep -q 'FOREIGN KEY (tenant_id, boq_id, revision_number)' "$SQL"
grep -q 'cross-tenant snapshot update' "$SQL"
grep -q 'cross-tenant counter update' "$SQL"
grep -q 'tenant-filtered outbox poll' "$SQL"
grep -q 'foreign_key_violation' "$SQL"
grep -q 'does not compile or invoke Java adapters' "$SQL"
grep -q 'does not prove' "$DOC"
printf '%s\n' 'PASS: tenant-isolation contract (11 assertions)'
