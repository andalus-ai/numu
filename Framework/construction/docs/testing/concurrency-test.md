# PostgreSQL concurrency test

Run from the bundle root:

```bash
./scripts/postgres-concurrency-test.sh
```

The script creates a disposable PostgreSQL 16 container, applies the bundle migrations, and starts independent database sessions to race revision-counter upserts and idempotency-key reservations. It asserts that N revision allocations for a BOQ return exactly `1..N`, that competing reservations for one `(tenant_id, idempotency_key)` yield one inserted row, and that another tenant can use the same key independently.

Environment variables:

- `NUMOU_TEST_PG_PORT` — host port, default `55440`.
- `NUMOU_CONCURRENCY_WORKERS` — number of competing sessions, default `16`, allowed `2..100`.

This is a schema-level concurrency test. It does **not** prove that the Java revision allocator uses this exact upsert, that snapshot + live BOQ writes share a transaction, or that the production schema matches these starter migrations. Those require the real repository checkout and adapter integration tests.
