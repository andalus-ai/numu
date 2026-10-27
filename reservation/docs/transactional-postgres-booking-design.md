# Transactional PostgreSQL booking persistence

## Status

Design contract for the next reservation persistence milestone. This document is not evidence that a PostgreSQL adapter exists or that reservation writes are currently atomic.

## Ownership and deployment

The generic Reservation capability owns booking identity, capacity allocation, and idempotency. Consumer capabilities (vehicle service, hospitality, transport booking, appointments) call the same application contract and must not create parallel schedulers or write reservation tables directly.

The PostgreSQL adapter is an optional infrastructure package. The reservation domain and SPI must remain free of JDBC, Quarkus, PostgreSQL, and JSON implementation details. A standalone app or modular monolith may compose this adapter; a distributed consumer must use a versioned application/API contract instead of sharing the database.

## Required atomic command boundary

The durable implementation must have one transaction-aware booking operation. It is not sufficient to make `IdempotentReservationRepository.saveIdempotently` atomic by itself while saving the availability ledger in a separate call.

For a new booking, the same database transaction must:

1. Claim the tenant-scoped idempotency key and store the canonical request fingerprint.
2. Lock the facility/day ledger row (creating it safely when absent), then load the latest ledger snapshot.
3. Re-evaluate resource allocation against that locked ledger; a stale in-memory ledger must never overwrite a concurrent allocation.
4. Persist the reservation snapshot and its resource assignment.
5. Persist the updated ledger/allocation.
6. Insert domain-event outbox records, if events are emitted from this command.
7. Store the idempotency result and commit once.

Any failure before commit rolls back all writes. A duplicate key with the same fingerprint returns the canonical stored reservation without allocating again. A duplicate key with a different fingerprint fails with `IdempotencyKeyConflictException`. A unique constraint, not an application-level check-then-insert, arbitrates concurrent requests.

The idempotency lookup fast path may return a previously committed reservation before consulting live resource discovery or booking rules. The final write path must still perform the key claim and capacity decision under transaction/locking to close the race between simultaneous first attempts.

## Storage constraints

The migration and adapter should enforce these invariants at the database boundary:

- Every reservation, ledger, and idempotency record is tenant-scoped.
- Idempotency uniqueness is `(tenant_id, idempotency_key)`; keys have a documented maximum length and fingerprints are non-empty fixed-format SHA-256 hex values.
- An idempotency record references the canonical reservation within the same tenant. Use a composite tenant/reservation foreign key or equivalent enforced constraint.
- Each ledger is unique by tenant, facility, and local service day; row locking serializes competing allocations for the same ledger.
- Reservation and ledger writes use optimistic version checks in addition to transaction locks where aggregate versions are maintained.
- Outbox records are committed with the booking and published asynchronously after commit. Event consumers must tolerate at-least-once delivery.
- Tenant identifiers come from trusted application context. SQL queries must include tenant predicates even when IDs are globally unique.

Snapshots may be stored as JSONB to preserve the domain snapshot boundary, but the adapter must use a configured, tested mapper for Java time and immutable value types. Do not use Java native object serialization. If JSONB is chosen, schema versioning and compatibility tests are required.

## Transaction integration

The adapter must not open independent connections for the idempotency claim, reservation save, and ledger save. All must receive/use the same transaction-bound connection or a single transaction coordinator. A generic `UnitOfWork` wrapper is not proof of a database transaction: the runtime composition must bind it to the actual transaction and tests must verify rollback against PostgreSQL.

If the booking transaction crosses a service boundary, a local SQL transaction cannot make the remote service atomic. Use an explicit workflow/saga and outbox/inbox with compensation rather than claiming a distributed ACID transaction.

## Required integration tests

Use PostgreSQL (not only mocks or the in-memory adapter) to verify:

1. First booking persists reservation, idempotency key, ledger allocation, and outbox rows together.
2. Same-key/same-fingerprint replay returns the original reservation and creates no additional allocation or event.
3. Same-key/different-fingerprint fails without changing any stored state.
4. Two concurrent first attempts using the same key create exactly one reservation.
5. Two concurrent different keys competing for one resource cannot double-book the overlapping slot.
6. Injected failure after reservation persistence but before ledger/outbox completion rolls back every write.
7. A failed transaction leaves no idempotency key that blocks a legitimate retry.
8. Tenant A cannot replay, inspect, or overwrite tenant B's key/reservation/ledger.
9. Stale ledger versions are rejected or retried after reload; they are never silently overwritten.
10. Migration can be applied to an empty database and upgraded reproducibly.

## Exit criteria

Do not call the PostgreSQL booking adapter production-ready until the adapter, transaction-bound application integration, migrations, PostgreSQL integration tests, and module-specific Maven verification are all present and passing. The current in-memory adapter remains for tests/local development only.
