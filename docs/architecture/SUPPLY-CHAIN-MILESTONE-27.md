# Supply Chain Milestone 27 — Recovery Persistence Integration

## Scope

This milestone adds opt-in PostgreSQL integration coverage for operator-facing warehouse recovery persistence. It complements the existing claim-contention and lease-recovery tests with assertions against the real JDBC adapter and database state.

## Covered behavior

- A dead-letter created for one tenant is visible through that tenant's bounded dead-letter query and not another tenant's query.
- Recovery health summaries count dead letters independently for each tenant.
- Manual replay transitions a dead-letter to `PREPARED`, increments its attempt count, records the operator's reason, and updates the tenant-scoped dead-letter count.
- A stale expected version cannot replay the same dead-letter twice.
- Replaying one tenant's record does not alter another tenant's dead-letter state or summary.
- Test rows are removed by tenant in a `finally` block.

## Running the integration test

Use a disposable PostgreSQL database and apply warehouse migrations V1 through V5. Configure:

- `NUMOU_TEST_POSTGRES_URL` (required to enable the opt-in test)
- `NUMOU_TEST_POSTGRES_USER` (optional)
- `NUMOU_TEST_POSTGRES_PASSWORD` (optional)

Run the warehouse JDBC persistence module's test suite using the repository's normal Maven build. Without the URL, JUnit assumptions skip the PostgreSQL integration tests; a skipped test is not evidence that the database path passed.

## Validation status

The test is committed as opt-in integration coverage. The GitHub-backed editing environment did not execute Maven or connect to PostgreSQL, so compilation and live database behavior still require CI or a configured disposable PostgreSQL environment.

## Follow-up

Manual replay currently records the operator reason in the recovery row's `last_error` field. A later milestone should add an append-only operator-action audit trail with actor identity, timestamp, prior/new version, reason, and correlation/request ID. The row's mutable state is useful operationally but is not a substitute for durable audit history.
