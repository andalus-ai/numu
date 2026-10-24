# Supply Chain Milestone 29 — Recovery Audit Atomicity and Concurrency

## Scope

M29 hardens the M28 operator audit contract with opt-in PostgreSQL tests for two failure modes that are especially important for recovery operations: an audit insert failing after the state update, and two operators replaying the same dead-letter attempt concurrently.

## Guarantees under test

- **Atomic rollback:** if the audit insert is rejected by a database constraint, the dead-letter attempt remains in `DEAD_LETTER` at its original version and no audit event is persisted.
- **Single-winner replay:** two concurrent replay requests using the same expected version result in exactly one successful transition, one stale-version rejection, and exactly one audit event.
- **Audit identity:** the one successful event records the actor and the resulting version.
- **Isolation and cleanup:** test data is tenant-scoped and uses unique identifiers. These tests require a disposable PostgreSQL database with warehouse migrations V1–V6 applied.

## Run

Configure `NUMOU_TEST_POSTGRES_URL` and optionally `NUMOU_TEST_POSTGRES_USER` / `NUMOU_TEST_POSTGRES_PASSWORD`, then run the relevant test in `warehouse-persistence-jdbc` with the repository's Maven test command.

The rollback test adds a temporary check constraint to force an audit insert failure and removes it in `finally`; run only against a disposable integration database, never a shared production database. The concurrency test uses two independent repository calls and JDBC connections to exercise the database's version fence.

## Validation status

The source changes were committed to the milestone branch. Maven compilation and live PostgreSQL execution have not been run from this editing environment; CI or a configured disposable PostgreSQL instance must confirm the tests before merge.

## Operational follow-up

The application boundary must continue deriving actor identity from authenticated credentials, not from an untrusted request field. Database roles should be restricted from updating or deleting audit rows where feasible. Audit retention/export remains a deployment-level compliance decision.
