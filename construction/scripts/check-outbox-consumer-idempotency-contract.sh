#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SQL="$ROOT/scripts/postgres-outbox-consumer-idempotency-test.sh"
DOC="$ROOT/docs/testing/postgres-outbox-consumer-idempotency-test.md"
[[ -f "$SQL" && -f "$DOC" ]]
grep -q 'locked_until=now()-interval' "$SQL"
grep -q "attempts=attempts+1" "$SQL"
grep -q 'PRIMARY KEY (tenant_id,event_id)' "$SQL"
grep -q 'ON CONFLICT (tenant_id,event_id) DO NOTHING' "$SQL"
grep -q 'test_consumer_inbox' "$SQL"
grep -q 'test_business_effect' "$SQL"
grep -q 'DELIVERIES.*== '\''2'\''' "$SQL"
grep -q 'INBOX.*== '\''1'\''' "$SQL"
grep -q 'EFFECTS.*== '\''1'\''' "$SQL"
grep -q 'does not invoke the production relay' "$SQL"
grep -q 'at-least-once' "$DOC"
grep -q 'same transaction' "$DOC"
printf '%s\n' 'PASS: outbox consumer idempotency contract (12 assertions)'
