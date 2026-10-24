# Numou Omnichannel — Milestone 20: Durable Worker Lifecycle Integration

## Goal

Exercise the verified-inbox worker against real PostgreSQL persistence, rather than proving lease and retry semantics only with fake stores.

## Coverage added

- Dispatch failure persists a sanitized failure code, clears the lease, increments attempts, and schedules a retry.
- A later worker claims the due retry, loads tenant-scoped payload bytes, dispatches the same event identity, and persists PROCESSED.
- A lease reclaimed while the dispatcher is in flight fences the old worker's acknowledgement; the replacement lease remains authoritative.
- Each test uses an isolated PostgreSQL schema and applies the module's V1–V3 migrations.

## Run

Set `NUMOU_TEST_POSTGRES_URL` to a disposable PostgreSQL database. Optionally set `NUMOU_TEST_POSTGRES_USER` and `NUMOU_TEST_POSTGRES_PASSWORD`. Then run:

```bash
mvn -pl commerce/omnichannel/numou-omnichannel-postgres -am test
```

Without the URL, PostgreSQL integration tests are skipped by assumption. These tests were added as a release-gate layer; they must run in CI or against a disposable PostgreSQL instance before claiming the lifecycle is verified.

## Boundaries

The payload store in this integration test is a tenant-checking in-memory fixture, not a production payload backend. Provider-specific signature algorithms remain covered by provider adapter contract tests. Dispatch remains at-least-once: the target application use case must make its business effect idempotent.
