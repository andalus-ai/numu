#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="$ROOT/scripts/verify-real-repository-readiness.sh"
DOC="$ROOT/docs/phase1-production-integration-gate.md"
assert_contains() {
  local file="$1" needle="$2"
  if ! grep -Fq -- "$needle" "$file"; then
    printf 'FAIL: expected %s to contain: %s\n' "$file" "$needle" >&2
    exit 1
  fi
  printf 'PASS: %s contains %s\n' "$(basename "$file")" "$needle"
}
assert_contains "$SCRIPT" 'pom.xml'
assert_contains "$SCRIPT" 'build.gradle.kts'
assert_contains "$SCRIPT" 'canonical application migration directory'
assert_contains "$SCRIPT" 'exit 2'
assert_contains "$SCRIPT" 'does not compile Java'
assert_contains "$DOC" 'authenticated server-side tenant resolver'
assert_contains "$DOC" 'transaction-local setting'
assert_contains "$DOC" 'actual application/relay roles'
assert_contains "$DOC" 'BLOCKED'
assert_contains "$DOC" 'All tests required by CI pass'
printf '%s\n' 'PASS: real-repository readiness contract (10 assertions)'
