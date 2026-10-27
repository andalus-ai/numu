# Reservation PostgreSQL transaction boundary

The reservation adapter's PostgreSQL foundation establishes the schema and shared-connection transaction primitive. It does not by itself constitute the production repository implementation.

## Required composition

A booking transaction must construct or obtain reservation and ledger repository implementations bound to the SqlConnection passed to PgReservationTransactionRunner.inTransaction. The idempotency-key insert, reservation snapshot write, availability-ledger update, and outbox insert must all use that same connection.

Do not use pool-level repository calls inside this callback. A pool-level call can borrow another connection and commit independently, defeating rollback guarantees.

## Required write ordering

1. Lock/load the tenant-scoped facility/day ledger (create it safely if absent).
2. Re-evaluate capacity against the locked ledger state.
3. Write the reservation snapshot and allocation/ledger snapshot.
4. Bind the tenant-scoped idempotency key to the reservation using the unique key constraint.
5. Insert domain events into reservation_outbox_event.
6. Commit once; dispatch outbox events only after commit.

The idempotency conflict path must compare the canonical request fingerprint and return the stored reservation only for a matching request. The foreign key is deferred so a reservation and its key can be inserted in either order within the same transaction.

## Not yet implemented by this change

- JSON serialization/deserialization and optimistic-locking repository implementations.
- Ledger row locking and the absent-row race strategy.
- Outbox dispatcher/lease worker.
- Wiring ReservationService to a transaction-scoped repository composition.
- PostgreSQL integration tests.

Until these are implemented and verified, the in-memory adapter remains the only complete repository implementation and production atomicity must not be claimed.
