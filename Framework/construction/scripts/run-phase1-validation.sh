#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:---all}"

usage() {
  cat <<'USAGE'
Usage: scripts/run-phase1-validation.sh [--all|--static-only|--postgres-only]

  --all            Run static source-contract checks, then disposable PostgreSQL tests (default).
  --static-only    Run static source-contract checks and shell syntax validation only.
  --postgres-only  Run disposable PostgreSQL tests only.

PostgreSQL tests create and remove their own local Docker containers. They do not
connect to an existing application database. A Docker daemon is required; psql
must also be installed locally because some tests use the host client.
USAGE
}

case "$MODE" in
  --all|--static-only|--postgres-only) ;;
  -h|--help) usage; exit 0 ;;
  *) usage >&2; exit 2 ;;
esac

if [[ "$MODE" != "--postgres-only" ]]; then
  echo '== Phase 1 static validation =='
  bash "$ROOT/scripts/run-phase1-static-checks.sh"
fi

if [[ "$MODE" != "--static-only" ]]; then
  command -v docker >/dev/null 2>&1 || {
    echo 'ERROR: Docker CLI is required for PostgreSQL validation.' >&2
    echo 'Run --static-only if Docker is unavailable.' >&2
    exit 2
  }
  docker info >/dev/null 2>&1 || {
    echo 'ERROR: Docker daemon is not reachable.' >&2
    exit 2
  }
  command -v psql >/dev/null 2>&1 || {
    echo 'ERROR: local psql client is required by some PostgreSQL tests.' >&2
    exit 2
  }
  echo '== Disposable PostgreSQL validation =='
  for test_script in \
    postgres-schema-smoke-test.sh \
    postgres-concurrency-test.sh \
    postgres-outbox-recovery-test.sh \
    postgres-outbox-tenant-safety-test.sh \
    postgres-outbox-consumer-idempotency-test.sh \
    postgres-outbox-lease-fencing-test.sh \
    postgres-outbox-retry-terminal-replay-test.sh \
    postgres-outbox-role-boundary-test.sh \
    postgres-tenant-context-pool-safety-test.sh \
    postgres-tenant-rls-write-boundary-test.sh \
    postgres-tenant-isolation-test.sh \
    postgres-boq-workflow-test.sh \
    postgres-idempotency-race-test.sh \
    postgres-command-atomicity-test.sh; do
    echo
    echo "-- $test_script --"
    bash "$ROOT/scripts/$test_script"
  done
fi

echo 'PASS: requested Phase 1 validation stages completed.'
echo 'NOTE: PostgreSQL scripts exercise migration/SQL protocols in disposable containers; they do not compile or invoke the Java application.'
