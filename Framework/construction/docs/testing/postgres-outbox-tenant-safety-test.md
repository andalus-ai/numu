# PostgreSQL outbox tenant-safety test

## Run

From the `syirkah-phase1` directory:

```bash
bash scripts/postgres-outbox-tenant-safety-test.sh
```

Requirements: Docker CLI, a reachable Docker daemon, and permission to bind the local test port (default `55443`; override with `SYIRKAH_TEST_PG_PORT`). The script starts a disposable PostgreSQL 16 container, applies the starter migrations, and removes the container on exit. It does not connect to an existing database.

## Assertions

- A tenant-A claim selects only tenant-A events, and a tenant-B claim selects only tenant-B events.
- A worker cannot acknowledge another tenant's event by supplying its event ID and lease token while using the wrong tenant predicate.
- A failed event scheduled for a future retry is not eligible early; after its availability is advanced for the test, reclaim increments `attempts`.
- Legacy rows with `tenant_id IS NULL` do not match a tenant-scoped claim and therefore remain excluded from delivery until authoritative ownership backfill is performed.

## Limits and operational cautions

This is a representative SQL-protocol test, not a production-adapter test. It does not execute `PgOutboxRelayStore`, broker delivery, tenant authorization, or application code. The SQL query shape must be kept aligned with the actual adapter and migrations. Tenant identity must come from trusted execution context, not request payload fields. Do not assign a guessed tenant to legacy events; backfill from authoritative aggregate ownership before enforcing `tenant_id NOT NULL` or enabling delivery of those rows. Delivery is at-least-once, so consumers must be idempotent.
