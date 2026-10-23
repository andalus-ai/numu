# Supply Chain M18 — Warehouse command idempotency conflict protection

## Goal

Make the JDBC recovery journal enforce the repository contract that a tenant-scoped command key can identify only one immutable business command payload.

## Behavior

- A first `createIfAbsent` call persists the proposed command.
- An exact replay returns the stored attempt, including its current status/version; it does not replace the stored row with the caller's freshly prepared attempt.
- Reusing the same tenant and command key with a different fingerprint is rejected.
- Reusing the key and fingerprint while changing the work order, line, operation, or recovery payload is also rejected.
- Conflicts raise `JdbcWarehouseExecutionAttemptRepository.IdempotencyConflictException`, a specific `IllegalStateException` subtype that callers can classify separately from persistence failures.

## Why this matters

A database uniqueness constraint prevents duplicate rows, but does not by itself prevent a retry from accidentally binding an existing key to a different command. Silently returning the existing row can conceal an upstream idempotency bug and make inventory/warehouse recovery ambiguous.

## Test

`WarehouseExecutionIdempotencyPostgresIntegrationTest` is opt-in. Configure `NUMOU_TEST_POSTGRES_URL`, optional `NUMOU_TEST_POSTGRES_USER` / `NUMOU_TEST_POSTGRES_PASSWORD`, and apply warehouse migrations V1–V4 to a disposable PostgreSQL database. It checks fingerprint mismatch, payload mismatch, and successful exact replay.

## Validation status and limits

The code and test were added through GitHub editing; Maven and PostgreSQL tests have not been executed in this session. CI should run the warehouse JDBC module tests against PostgreSQL before merge.

This guard enforces payload consistency at the JDBC adapter boundary. It does not compute a cryptographic fingerprint, guarantee that upstream fingerprint generation is canonical, or provide a distributed transaction across warehouse and inventory systems.
