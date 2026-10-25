# M27 — PostgreSQL integration coverage for courier outbox operations

M27 adds Testcontainers-backed PostgreSQL integration coverage for the outbox operations schema and its concurrency invariants.

## Covered cases

- Runs the repository Flyway migration chain against PostgreSQL 16.
- Inserts real outbox events and destination-specific publication rows.
- Verifies the publication ledger foreign key rejects a delivery row without a corresponding outbox event.
- Verifies publication audit rows cannot be deleted after insertion.
- Uses two independent database connections to prove `FOR UPDATE SKIP LOCKED` prevents a second worker from claiming a row locked by the first worker.

## Running

From the repository root, run the courier PostgreSQL adapter test suite, for example:

```bash
mvn -pl logistics/numou-courier-postgres-adapter -am test
```

The integration class is skipped by Testcontainers when Docker is unavailable. Ensure Docker is available in CI so the PostgreSQL checks execute rather than being skipped.

## Remaining validation

These integration tests validate database invariants but do not replace end-to-end tests for a real `CourierEventPublisher` transport adapter, delivery acknowledgement after remote acceptance, or the complete application authorization wiring. Those remain deployment-level release gates.
