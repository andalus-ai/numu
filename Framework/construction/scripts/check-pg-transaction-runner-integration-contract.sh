#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TEST="$ROOT/construction-adapter/src/test/java/tech/kayys/numou/construction/adapter/postgres/idempotency/PgTransactionalCommandRunnerIntegrationTest.java"
CONTEXT="$ROOT/construction-adapter/src/main/java/tech/kayys/numou/construction/adapter/postgres/idempotency/PgTransactionalCommandContext.java"
RUNNER="$ROOT/construction-adapter/src/main/java/tech/kayys/numou/construction/adapter/postgres/idempotency/PgTransactionalCommandRunner.java"
for file in "$TEST" "$CONTEXT" "$RUNNER"; do
  test -s "$file" || { echo "FAIL: missing $file" >&2; exit 1; }
done
for expected in \
  '@QuarkusTest' \
  'failedCallbackRollsBackReservationAndOutboxTogether' \
  'concurrentSameKeyCommandsExecuteBusinessCallbackAndAppendEventOnce' \
  'new PgTransactionalCommandRunner(pool' \
  'new IdempotentCommandExecutor(runner' \
  'context.appendOutboxEvent(event)' \
  'countIdempotency(tenant, key)' \
  'countOutboxForTenant(tenant)'; do
  grep -Fq "$expected" "$TEST" || { echo "FAIL: missing test contract: $expected" >&2; exit 1; }
done
grep -Fq 'pool.withTransaction' "$RUNNER"
grep -Fq 'connection.preparedQuery(INSERT_RESERVATION)' "$CONTEXT"
grep -Fq 'connection.preparedQuery(INSERT_OUTBOX_EVENT)' "$CONTEXT"
echo 'PASS: transaction-runner integration test contract assertions'
