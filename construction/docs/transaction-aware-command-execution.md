# Transaction-aware idempotent command execution

## Scope

This slice adds an application executor and a transaction-scoped SPI. It deliberately does not claim to implement a concrete Quarkus/PostgreSQL transaction adapter: the source available for this work is a text dump, not a checked-out buildable repository.

## Required transaction boundary

A production `TransactionalCommandRunner` must open one database transaction and construct a `TransactionalCommandContext` bound to the same connection. The following must use that connection and transaction:

1. reserve `(tenant_id, idempotency_key)`;
2. perform the BOQ aggregate compare-and-set;
3. insert the immutable revision snapshot/items;
4. insert the outbox event;
5. store the serialized response and mark the idempotency record `COMPLETED`;
6. commit only after the callback succeeds.

On any failure, rollback all of the above, including the idempotency reservation. Never call the pool-level `PgCommandIdempotencyStore` from inside this transaction; it acquires its own connections and breaks atomicity.

## Concurrency and replay

- The database unique key `(tenant_id, idempotency_key)` is the reservation arbiter.
- A matching completed request returns the stored response without repeating the business action.
- A different operation or fingerprint is a conflict.
- A matching row without a completed response is reported as in-progress. The transaction adapter should normally avoid committing `PROCESSING` rows; a committed one indicates legacy data, manual intervention, or a broken execution path.
- A `complete(...)` update that affects zero rows is a hard failure and must roll back the business writes.

## Integration checklist

- Implement `TransactionalCommandRunner` with the actual Mutiny SQL client transaction API used by the repository version.
- Implement `TransactionalCommandContext` with connection-scoped SQL operations, not `PgPool` calls.
- Make BOQ revision operations transaction-bound and write the outbox row in the same transaction.
- Wire a configured JSON codec and stable request canonicalization before hashing.
- Map `IdempotencyConflictException` to HTTP 409 and in-progress outcomes to a documented retryable response (commonly 409/425); do not leak stored response payloads across tenant boundaries.
- Test with real PostgreSQL: duplicate concurrent keys, same key/different fingerprint, rollback after snapshot failure, rollback after outbox failure, response replay, and lost connection during commit.
- Verify whether the application uses one database or multiple data sources. Atomicity cannot span independent databases without a distributed transaction or a different consistency design.
