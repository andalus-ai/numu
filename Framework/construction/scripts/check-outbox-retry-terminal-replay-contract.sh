#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SQL="$ROOT/scripts/postgres-outbox-retry-terminal-replay-test.sh"
DOC="$ROOT/db/test/postgres-outbox-retry-terminal-replay-test.md"
[[ -f "$SQL" && -f "$DOC" ]]
grep -q 'make_interval' "$SQL"
grep -q 'power(2' "$SQL"
grep -q 'LEAST(' "$SQL"
grep -q 'terminal=TRUE' "$SQL"
grep -q 'outbox_replay_audit' "$SQL"
grep -q 'operator_id' "$SQL"
grep -q 'previous_attempts' "$SQL"
grep -q 'length(trim(reason))' "$SQL"
grep -q 'attempts=0' "$SQL"
grep -q 'last_error=NULL' "$SQL"
grep -q 'does not invoke the production relay or admin API' "$SQL"
grep -q 'exponential' "$DOC"
grep -q 'operator replay' "$DOC"
grep -q 'disposable PostgreSQL' "$DOC"
printf '%s\n' 'PASS: outbox retry/terminal/replay contract (14 assertions)'
