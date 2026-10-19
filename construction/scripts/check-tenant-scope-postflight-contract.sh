#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SQL="$ROOT/db/postflight/V20261014_tenant_scope_postflight.sql"
DOC="$ROOT/docs/tenant-scope-migration-postflight.md"
[[ -f "$SQL" && -f "$DOC" ]]
grep -q 'READ-ONLY postflight' "$SQL"
grep -q "a.attname = 'tenant_id'" "$SQL"
grep -q 'a.attnotnull' "$SQL"
grep -q 'expected_pk' "$SQL"
grep -q 'c.convalidated' "$SQL"
grep -q 'idx_boq_revision_tenant_project' "$SQL"
grep -q "tenant_scope_postflight" "$SQL"
grep -q 'does \*\*not\*\* prove' "$DOC"
grep -q 'ON_ERROR_STOP=1' "$DOC"
printf '%s\n' 'PASS: tenant-scope migration postflight contract (9 assertions)'
