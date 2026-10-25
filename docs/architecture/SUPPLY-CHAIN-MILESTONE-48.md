# Supply-chain milestone 48 — concurrent worker recovery

## Goal

Exercise two worker invocations against the same PostgreSQL-backed export job while the first worker is actively processing its page. The second invocation must not claim the live lease, and the active worker must complete with a single durable manifest.

## Test design

The opt-in PostgreSQL/filesystem integration test inserts a pending job and starts worker A on a background thread. Its audit-page provider blocks at page retrieval after worker A has claimed the job. While worker A is blocked, worker B runs against the same job repository and artifact root.

The test asserts worker B finds no eligible job, releases worker A, and verifies the final job is completed with exactly one manifest row whose byte size matches the final checkpoint.

The test uses a random isolated PostgreSQL schema and a temporary filesystem artifact root. It does not rely on timing sleeps: latches coordinate the concurrency window.

## Validation status

- PostgreSQL integration test added.
- Maven tests not run in this change.
- Requires `NUMOU_TEST_POSTGRES_URL`; `NUMOU_TEST_POSTGRES_USER` and `NUMOU_TEST_POSTGRES_PASSWORD` are optional.
- This covers database lease exclusion and one successful completion under a concurrent invocation; it does not claim distributed filesystem safety across hosts.
