# Transactional outbox writes from commands

This slice adds `TransactionalOutboxEvent` and `TransactionalCommandContext.appendOutboxEvent(...)`.
The PostgreSQL implementation inserts through the exact `SqlConnection` owned by
`PgTransactionalCommandRunner`; it does not use `PgPool` or open a second transaction.

## Required command ordering

1. Reserve the tenant-scoped idempotency key.
2. Perform BOQ state and immutable snapshot writes through `context.boqRevisions()`.
3. Insert each integration event with `context.appendOutboxEvent(...)`.
4. Serialize and persist the successful response with `context.complete(...)`.
5. Let `withTransaction` commit all writes together.

Any failure in steps 2–4 must fail the returned `Uni`, causing the transaction runner to roll back. Do not publish to a broker inside this transaction; the relay publishes only after commit.

## Payload and identity rules

- `eventId` must be globally unique and reused only for retries of the same logical event.
- `tenantId` must come from authenticated request context, not an untrusted payload field.
- `payloadJson` must be valid JSON; PostgreSQL's `::jsonb` cast rejects invalid JSON and causes rollback.
- Keep payloads versioned and avoid secrets or unnecessary personal data.
- Consumers must deduplicate by `eventId`, because broker delivery remains at-least-once.

## Integration tests still required

- BOQ update + snapshot + outbox + response commit together.
- Invalid JSON or an outbox constraint failure rolls back BOQ/snapshot/idempotency writes.
- Duplicate `event_id` rolls back the entire command.
- Event is not visible to the relay before transaction commit.
- Event tenant and aggregate IDs match the command's authorized context.

The source available for this starter is a text dump, not a checked-out repository. This code has not been compiled against the actual module dependency graph or run against PostgreSQL; validate schema and migration ordering before applying it.
