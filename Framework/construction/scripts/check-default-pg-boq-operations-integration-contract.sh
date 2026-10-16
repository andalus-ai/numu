#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST="$ROOT/construction-adapter/src/test/java/tech/kayys/syirkah/construction/adapter/postgres/boq/DefaultPgBoqRevisionOperationsFactoryIntegrationTest.java"
[[ -f "$TEST" ]] || { echo "Missing integration test: $TEST" >&2; exit 1; }
checks=(
  'DefaultPgBoqRevisionOperationsFactory'
  'MappedPgLiveBoqRevisionStoreFactory'
  'captureWritesSnapshotCounterLiveStateAndOutboxInOneTransaction'
  'failureAfterCaptureRollsBackSnapshotItemCounterLiveStateAndOutbox'
  'construction_boq_revision_snapshot'
  'construction_boq_revision_item'
  'construction_boq_revision_counter'
  'construction_outbox_event'
  'injected after capture'
)
for needle in "${checks[@]}"; do
  grep -Fq "$needle" "$TEST" || { echo "Missing expected integration contract: $needle" >&2; exit 1; }
done
echo "PASS: ${#checks[@]} BOQ operations integration contract assertions"
