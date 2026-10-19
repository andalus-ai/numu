# Java/PostgreSQL transaction-runner integration tests

`PgTransactionalCommandRunnerIntegrationTest` is the first test in this starter that invokes the actual `PgTransactionalCommandRunner`, `PgTransactionalCommandContext`, and `IdempotentCommandExecutor` against a configured PostgreSQL database.

## Cases covered

- **Atomic rollback:** reserve an idempotency key and append an outbox event, then fail the callback. The test checks that neither row remains after `withTransaction` rolls back.
- **Concurrent duplicate command:** run two requests concurrently with the same tenant, key, operation, and fingerprint. The test expects one callback execution, one completed idempotency row, one outbox event, and the same response for both callers.

## Prerequisites

1. Run this test in the actual Quarkus host module with `quarkus-junit5`, Mutiny, Vert.x reactive PostgreSQL client, and JUnit 5 already configured.
2. Configure the Quarkus test datasource to point at a disposable PostgreSQL database.
3. Apply every migration under `db/migration` before running the test. In particular, the `tenant_id` columns must exist on the idempotency and outbox tables.
4. Ensure the CDI application can initialize in the test profile. The test constructs a runner with a test-only `BoqRevisionOperationsFactory`; BOQ operations are intentionally unused in these two cases.

Example command in the host module (adapt the module path to the real repository):

```bash
./mvnw -pl construction-adapter-postgres -Dtest=PgTransactionalCommandRunnerIntegrationTest test
```

The module name above is illustrative; this source dump does not establish the actual Maven module name. Do not add a new guessed module solely to match the example.

## Limits

This test does not yet exercise the real `DefaultPgBoqRevisionOperationsFactory`, the live BOQ mapping, or snapshot table writes. It also has not been compiled or run in this environment because the host Maven build and live PostgreSQL service are unavailable. Treat it as an integration test source to wire into the real module, not as a passing test result.
