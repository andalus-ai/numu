# Reservation transaction-aware command composition

The reservation SPI now defines a transaction-scoped command context containing:
- an `IdempotentReservationRepository`,
- a `LedgerRepository`, and
- a `DomainEventSink`.

`ReservationTransactionRunner.execute` supplies that context to one command callback.
The PostgreSQL implementation creates both repositories with the same `SqlConnection`
inside `PgPool.withTransaction`, so their SQL operations participate in one transaction.

## Composition requirements

Construct `PgReservationTransactionRunner` with an event-sink factory that uses the
provided connection (for example, a transactional outbox writer). Do not pass a remote
publisher or a sink that obtains a separate pooled connection; those side effects would
not roll back with the reservation transaction.

The one-argument constructor is retained for low-level compatibility, but intentionally
fails if used through the new service-facing `execute` contract because it has no
transaction-bound event sink.

## Remaining integration work

This commit introduces the public composition contract and PostgreSQL runner support.
It does not yet migrate all `ReservationService` operations to this runner. The current
booking methods that still use the legacy `UnitOfWork` must not be described as atomic
with the PostgreSQL repositories until wired through `ReservationTransactionRunner`.
The next implementation step is to route create/booking/idempotency flows through the
context, persist events through a connection-bound outbox sink, and add PostgreSQL
integration tests for rollback, concurrent allocation, and idempotent replay.
