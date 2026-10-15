#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SQL="$ROOT/db/preflight/V20261014_tenant_scope_preflight.sql"
DOC="$ROOT/docs/tenant-scope-migration-preflight.md"
[[ -f "$SQL" && -f "$DOC" ]]
grep -q 'COUNT(\*)' "$SQL"
grep -q 'empty_tables_meet_migration_guard' "$SQL"
grep -q 'READ-ONLY' "$SQL"
grep -q 'do not add a default tenant' "$SQL"
grep -q 'operator-authored backfill' "$SQL"
grep -q 'psql' "$DOC"
grep -q 'stop' "$DOC"
printf '%s\n' 'PASS: tenant-scope migration preflight contract (7 assertions)'
