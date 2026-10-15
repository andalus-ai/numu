#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST="$ROOT/src/test/java/tech/kayys/syirkah/construction/application/idempotency/IdempotentCommandExecutorTest.java"
[[ -f "$TEST" ]] || { echo "Missing IdempotentCommandExecutorTest.java" >&2; exit 1; }
for method in \
  reservedRequestRunsActionAndStoresEncodedOutcome \
  completedMatchingRequestReturnsStoredResponseWithoutRunningAction \
  reusedKeyWithDifferentRequestFailsBeforeAction \
  matchingButIncompleteReservationFailsClosed \
  actionFailureIsPropagatedAndOutcomeIsNotCompleted \
  failedCompletionCausesCommandFailure \
  invalidTrustedInputsAreRejectedBeforeOpeningTransaction; do
  grep -q "$method" "$TEST" || { echo "Missing test case: $method" >&2; exit 1; }
done
echo "Idempotency Java test source contract checks passed (7 cases present)."
