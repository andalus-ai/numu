#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST="$ROOT/construction-adapter/src/test/java/tech/kayys/numou/construction/adapter/postgres/outbox/PgOutboxRelayStoreIntegrationTest.java"
STORE="$ROOT/construction-adapter/src/main/java/tech/kayys/numou/construction/adapter/postgres/outbox/PgOutboxRelayStore.java"
for token in \
  '@QuarkusTest' \
  'claimsEligibleRowsAndPersistsLeaseIdentity' \
  'staleLeaseCannotAcknowledgeAfterExpiredEventIsReclaimed' \
  'failedTransitionIsLeaseFencedAndBoundsStoredError' \
  'rejectsInvalidClaimArgumentsBeforeIssuingSql' \
  'store.claimBatch(' \
  'store.markPublished(' \
  'store.markFailed(' \
  'DELETE FROM construction_outbox_event WHERE event_type' \
  'assertNotEquals(staleToken, currentToken)'; do
  grep -Fq "$token" "$TEST" || { echo "FAIL: missing integration-test contract: $token" >&2; exit 1; }
done
for token in 'FOR UPDATE SKIP LOCKED' 'lease_token = gen_random_uuid()' 'AND lease_token = $2' 'rowCount() == 1'; do
  grep -Fq "$token" "$STORE" || { echo "FAIL: missing adapter contract: $token" >&2; exit 1; }
done
echo 'PASS: PostgreSQL outbox relay store integration contract (14 assertions)'
