# PostgreSQL idempotency race test

Run `scripts/postgres-idempotency-race-test.sh` in an environment with Docker and `psql` installed. It starts a disposable PostgreSQL 16 container, applies the bundle migrations, and launches two independent sessions against the same `(tenant_id, idempotency_key)` while the winning transaction remains uncommitted.

Assertions cover:

- the competing insert waits for the winner and does not create a second reservation;
- only the transaction that reserved the key performs the simulated business effect;
- the losing request observes the completed response after the winner commits;
- a different request fingerprint cannot replace the original fingerprint or execute the simulated effect.

## Scope and limitations

This is a database concurrency/protocol test. The business effect is a test-only table insert; it does not instantiate `IdempotentCommandExecutor`, `PgTransactionalCommandRunner`, or `PgTransactionalCommandContext`. It also does not prove the application correctly compares operation and fingerprint after loading an existing row. Run this alongside Java tests and an integration test against the host application's actual dependency graph before release.

Override `SYIRKAH_TEST_PG_PORT` if port `55448` is already occupied. The script removes its disposable container and temporary output directory on exit.
