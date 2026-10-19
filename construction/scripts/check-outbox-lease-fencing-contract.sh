#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SQL="$ROOT/scripts/postgres-outbox-lease-fencing-test.sh"
DOC="$ROOT/docs/testing/postgres-outbox-lease-fencing-test.md"
[[ -f "$SQL" && -f "$DOC" ]]
grep -q 'lease_token' "$SQL"
grep -q 'locked_until=now()-interval' "$SQL"
grep -Fq "worker_id='\$worker'" "$SQL"
grep -q 'STALE_ACK' "$SQL"
grep -q 'STALE_FAIL' "$SQL"
grep -q 'STALE_RENEW' "$SQL"
grep -Fq '[[ -z "$STALE_ACK" ]]' "$SQL"
grep -Fq '[[ -z "$STALE_FAIL" ]]' "$SQL"
grep -Fq '[[ -z "$STALE_RENEW" ]]' "$SQL"
grep -q 'CURRENT_ACK' "$SQL"
grep -q 'EXPECTED="PUBLISHING:worker-current:' "$SQL"
grep -q 'does not invoke the production relay adapter' "$SQL"
grep -q 'lease token' "$DOC"
grep -q 'stale worker' "$DOC"
printf '%s\n' 'PASS: outbox lease-fencing contract (15 assertions)'
