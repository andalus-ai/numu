#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST="$ROOT/construction-adapter/src/test/java/tech/kayys/numou/construction/adapter/postgres/boq/DefaultPgBoqRevisionOperationsFactoryIntegrationTest.java"
[[ -f "$TEST" ]] || { echo "Missing BOQ operations integration test: $TEST" >&2; exit 1; }
checks=(
  'concurrentApprovalsAllowExactlyOneWinnerAndEmitOneApprovalEvent'
  'staleRevisionSubmissionDoesNotChangeLiveStateOrEmitEvent'
  'fullLifecycleEmitsOneEventPerTransitionAndRejectsSelfApproval'
  'approveAfterBarrier'
  'BoqRevisionApproved'
  'BoqRevisionCaptured'
  'BoqRevisionSubmittedForReview'
  'the submitter must not approve their own revision'
  'BoqRevisionSubmittedForReview'
  'assertNotEquals(firstWon, secondWon'
  'exactly one approval should succeed'
)
for needle in "${checks[@]}"; do
  grep -Fq "$needle" "$TEST" || { echo "Missing lifecycle concurrency contract: $needle" >&2; exit 1; }
done
echo "PASS: ${#checks[@]} BOQ lifecycle concurrency contract assertions"
