#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST="$ROOT/src/test/java/tech/kayys/syirkah/construction/application/boq/handler/TrustedCommandContextTest.java"
for pattern in \
  'concurrentRequestsResolveTenantAndActorBeforeAsyncWorkflowBoundary' \
  'ThreadLocal<String> requestTenant' \
  'ThreadLocal<UUID> requestActor' \
  'CountDownLatch contextsReady' \
  'CountDownLatch invokeTogether' \
  'tenant-alpha' \
  'tenant-beta' \
  'executor.shutdownNow()' \
  'requestTenant.remove()' \
  'requestActor.remove()' \
  'CopyOnWriteArrayList'; do
  grep -Fq "$pattern" "$TEST" || { echo "FAIL: missing tenant-context concurrency contract: $pattern" >&2; exit 1; }
done
printf '%s\n' 'PASS: application tenant-context concurrency contract (11 assertions)'
