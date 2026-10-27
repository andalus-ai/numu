# PostgreSQL reservation repositories

The PostgreSQL adapter now provides connection-bound implementations of
`IdempotentReservationRepository` and `LedgerRepository`:

- `PgReservationRepository` performs tenant-scoped reads, optimistic-version writes,
  and atomic tenant-scoped idempotency-key arbitration.
- `PgLedgerRepository` inserts or reuses the unique facility/day ledger row and locks
  it with `SELECT ... FOR UPDATE` before allocation work.
- Both require a transaction-scoped `SqlConnection`; neither obtains a connection from
  `PgPool`. Construct both inside the same `PgReservationTransactionRunner` callback.

Example composition pattern:

```java
transactionRunner.inTransaction(connection -> {
    var reservations = new PgReservationRepository(connection);
    var ledgers = new PgLedgerRepository(connection);
    // Invoke the reservation application workflow with these transaction-scoped ports.
    // Do not let the ports escape this callback or publish external effects before commit.
    return command.execute(reservations, ledgers);
});
```

## Concurrency and idempotency

The database unique constraint on `(tenant_id, idempotency_key)` arbitrates concurrent
retries. The key is inserted before the reservation row; the migration's deferred FK
allows both writes to commit or roll back together. A replay compares the stored
fingerprint and returns the canonical persisted reservation, while a different
fingerprint fails with `IdempotencyKeyConflictException`.

The daily ledger's unique `(tenant_id, facility_id, ledger_day)` constraint handles
first-creation races. Locking that row serializes allocations for the same facility/day,
provided the caller performs find/allocate/save in the same database transaction.

## Remaining production gates

These classes are connection-bound persistence adapters, but the existing application
composition must still inject them into the actual `ReservationService` command path
through a transaction-aware unit of work. The generic `UnitOfWork` must not be assumed
to share a connection unless its implementation explicitly guarantees it. Outbox
insertion/dispatch, PostgreSQL integration tests for contention and rollback, JSON
snapshot round-trip tests, and the end-to-end transaction wiring remain required before
this is considered production-ready. Do not publish domain events externally before the
transaction commits.
