# Outbox retry, terminal failure, and operator replay acceptance

Run `scripts/postgres-outbox-retry-terminal-replay-test.sh` directly or use
`scripts/run-phase1-validation.sh --all`. The test creates a disposable PostgreSQL
16 container, applies the starter migrations, and removes the container on exit.
It never connects to an existing database.

## Acceptance checks

- Failed events receive an exponential retry delay (`base * 2^(attempts - 1)`) with a fixed upper bound.
- An exhausted event marked `terminal=true` is excluded from normal claim eligibility.
- An operator replay requires an explicit operator identity and non-empty reason.
- The replay audit retains the previous attempt count before the event is reset and requeued.
- Requeue clears stale worker/lease/error fields and restores a claimable pending state.

The audit table is test-only. This harness specifies desired behavior; it does not
add a production operator API, prove that the relay uses the same backoff expression,
or verify authorization/audit durability in the real Syirkah service. Confirm the
actual relay's retry policy, migration order, and admin authorization before adoption.
