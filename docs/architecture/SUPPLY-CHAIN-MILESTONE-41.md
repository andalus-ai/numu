# Supply-chain milestone 41: PostgreSQL claim and lease-recovery integration tests

## Scope

Add opt-in integration tests against a dedicated PostgreSQL database for the durable recovery-audit export job repository. The tests exercise real row locking and transaction behavior rather than mocks.

## Scenarios

- Two worker threads claim the same single pending job at the same time; exactly one receives the job and the other sees no claimable work.
- A RUNNING job whose lease expired can be reclaimed, with the worker owner and lease replaced and the optimistic version incremented.
- Each test creates an isolated random schema and drops it afterward. The configured database user must be allowed to create/drop schemas.

## Running

Set these environment variables and run the JDBC module's tests:

- NUMOU_TEST_POSTGRES_URL: JDBC URL for a dedicated PostgreSQL test database
- NUMOU_TEST_POSTGRES_USER: database user
- NUMOU_TEST_POSTGRES_PASSWORD: password

If the URL is absent, the PostgreSQL integration class is skipped. Do not point these tests at a production database. The test schema is isolated, but the database credentials must have permission to create and drop schemas.

## Validation limitations

These tests require PostgreSQL and are not a substitute for end-to-end worker fault injection around artifact writes, checkpoints, manifest insertion, and completion. Maven tests were not executed as part of authoring this change; CI or a developer environment with PostgreSQL must run them.
