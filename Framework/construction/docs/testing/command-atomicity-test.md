# Command atomicity protocol test

Run `scripts/postgres-command-atomicity-test.sh` in an environment with Docker. It starts a disposable PostgreSQL 16 container, applies the starter migrations, and exercises the database protocol for a BOQ capture command.

The test checks:

- an injected failure after the idempotency reservation, revision snapshot/item writes, live BOQ update, and outbox insert leaves none of those writes committed;
- a successful command persists the live revision, immutable snapshot/item, completed idempotency outcome, and outbox event;
- retrying the same tenant/idempotency key does not overwrite the stored completed response or create a second outbox event.

## Scope and limitations

This is a SQL-level protocol test using a test-only `test_live_boq` table because the source bundle does not establish the actual Syirkah live BOQ schema. It does **not** instantiate the Java `IdempotentCommandExecutor`, `PgTransactionalCommandRunner`, CDI wiring, or the production workflow adapter. The retry check verifies uniqueness and preservation of the stored outcome at the database level; application-level response decoding and fingerprint-conflict behavior still require Java tests.

Run alongside `scripts/postgres-concurrency-test.sh`, `scripts/postgres-outbox-recovery-test.sh`, and `scripts/postgres-boq-workflow-test.sh`.
