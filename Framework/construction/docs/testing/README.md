# PostgreSQL schema smoke test

Run from any directory:

```bash
./scripts/postgres-schema-smoke-test.sh
```

Requirements: Docker with permission to start a container. The script starts a disposable `postgres:16-alpine` container, applies all SQL migrations in lexical order, checks basic snapshot/item/outbox/idempotency constraints, and verifies that a transaction rollback removes counter, snapshot, and outbox writes together. It removes its container on exit and does not connect to a configured application database.

To change the host port, set `NUMOU_TEST_PG_PORT` (default `55439`). If that port is occupied, choose another free port.

## Scope limits

This is a database schema smoke test, not a Java adapter integration test. It does not prove the live BOQ table mapping, transaction-bound Mutiny wiring, concurrent revision allocation, authorization, or broker delivery. Those still require the actual repository checkout and application-level PostgreSQL tests.
