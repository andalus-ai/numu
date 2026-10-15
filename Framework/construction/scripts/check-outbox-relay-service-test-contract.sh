#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST="$ROOT/src/test/java/tech/kayys/syirkah/construction/application/outbox/OutboxRelayServiceTest.java"
DOC="$ROOT/db/test/outbox-relay-service-unit-tests.md"
[[ -f "$TEST" && -f "$DOC" ]]
grep -q 'emptyBatchDoesNotInvokeDeliveryAndReturnsZero' "$TEST"
grep -q 'successfulDeliveryAcknowledgesUsingEventLeaseToken' "$TEST"
grep -q 'deliveryFailureSchedulesRetryAndBoundsStoredError' "$TEST"
grep -q 'exhaustedAttemptIsMarkedTerminalAndNotScheduledForRetry' "$TEST"
grep -q 'independentEventsCanSucceedAndFailWithinSameBatch' "$TEST"
grep -q 'rejectsInvalidConfiguration' "$TEST"
grep -q 'OutboxRelayService' "$TEST"
grep -q 'have \*\*not\*\* been compiled or executed here' "$DOC"
printf '%s\n' 'PASS: outbox relay service unit-test contract (8 assertions)'
