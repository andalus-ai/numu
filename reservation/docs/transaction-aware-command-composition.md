# Reservation transaction-aware command composition

The reservation capability exposes a transaction-scoped command context containing:
- an `IdempotentReservationRepository`,
- a `LedgerRepository`, and
- a `DomainEventSink`.

`PgReservationTransactionRunner.execute` creates the PostgreSQL reservation repository,
ledger repository, and event sink from the same `SqlConnection` inside
`PgPool.withTransaction`. The connection-bound `PgReservationOutboxEventSink` writes
event rows to `reservation_outbox_event`; it does not acquire a second pooled connection
or publish directly to a broker.

## Current supported path

The constrained/idempotent booking path is routed through the configured transaction
runner. Its idempotency lookup, create/replay decision, repository mutations, and event
sink are selected from the transaction context. The application regression test verifies
that this path uses the context collaborators rather than the legacy repositories and
sink, and that replay does not emit duplicate events.

This is a scoped guarantee, not a claim that every reservation command has been migrated.
Other service methods that still use the legacy `UnitOfWork` and constructor collaborators
must not be described as participating in the PostgreSQL transaction until they are
explicitly routed through the transaction context.

## Composition requirements

Construct the runner with a sink factory that uses the provided connection:

```java
new PgReservationTransactionRunner(
    pool,
    connection -> new PgReservationOutboxEventSink(connection)
)
```

Do not pass a remote publisher or a sink that obtains a separate pooled connection.
Those side effects would not roll back with the reservation transaction. The one-argument
runner constructor remains available for low-level `inTransaction` use, but intentionally
fails when used through the service-facing `execute` contract because it has no
transaction-bound event sink.

The outbox sink inserts rows sequentially on the supplied connection and treats a repeated
event ID as an idempotent insert. The outbox schema supports pending, processing, published,
and dead states, but a durable relay, lease/retry behavior, and operator recovery must be
verified separately; writing an outbox row is not proof of delivery.

## Verification status and next acceptance criteria

The merged implementation adds unit-level contract coverage for transaction-context
selection and connection-bound outbox writes. These tests do **not** prove PostgreSQL
rollback or concurrency behavior, and the Maven reservation reactors have not been
confirmed green as part of this change.

Before describing the reservation path as production-ready, complete and verify:

1. A reproducible PostgreSQL integration-test setup for the reservation adapter using the
   real migration, not a test-only schema that can drift from production.
2. Rollback tests showing reservation, availability-ledger, idempotency, and outbox writes
   all disappear when the command fails after writes have begun.
3. Concurrent idempotency tests proving same-key/same-fingerprint requests converge on one
   reservation and same-key/different-fingerprint requests conflict.
4. Capacity/allocation concurrency tests proving concurrent bookings cannot over-allocate.
5. Composition-root wiring for each supported runtime, with the connection-bound sink.
6. Outbox relay verification covering competing workers, leases/fencing, retries, terminal
   failures, and recovery. Reuse an existing public reliability capability where its
   contract fits; do not fork another generic relay solely for reservation.

Run the reservation Maven reactor first, then the relevant framework/runtime reactor and
repository CI. Report test failures and unavailable infrastructure separately; do not infer
success from source review or from unrelated CI jobs.

## PostgreSQL rollback contract test

The adapter now has an opt-in `pg-integration` Maven profile. Run it from the reservation
reactor after its framework parent/dependencies are available:

```bash
mvn -f reservation/pom.xml -pl reservation-adapter -am -Ppg-integration verify
```

The `PgReservationTransactionRunnerIntegrationTest` applies the production migration and
deliberately fails after inserting a reservation row, availability-ledger row, idempotency
row, and outbox event through one PostgreSQL transaction. It then verifies all four writes
are absent. Quarkus Dev Services requires Docker; alternatively configure a reachable
PostgreSQL test endpoint. The normal test profile excludes `*IntegrationTest` so a plain
unit build does not silently depend on Docker.

The rollback scenario is a persistence-boundary proof, not an end-to-end `ReservationService`
integration test: it exercises the runner's low-level transaction callback and uses SQL
fixtures for the reservation/ledger/idempotency writes. The integration suite now also
exercises `PgReservationRepository.saveIdempotently` through transaction-scoped repositories
under concurrent same-key/same-fingerprint and same-key/different-fingerprint requests.
Those tests assert one canonical reservation is persisted and a conflicting fingerprint is
rejected. They still require execution against PostgreSQL before being treated as verified.

Remaining acceptance criteria include a full `ReservationService` integration path, capacity
allocation races with overlapping booking windows, runtime composition-root wiring, and
outbox relay lease/retry/dead-letter recovery.



### Concurrent capacity-allocation contract

The opt-in PostgreSQL integration suite also races two overlapping allocation attempts against a
single bookable resource. Both attempts use `PgReservationTransactionRunner.execute` and
`PgLedgerRepository.findOrCreate`, whose facility/day ledger row is selected `FOR UPDATE`.
The expected contract is that one attempt persists an allocation and the other observes the
updated ledger and reports no availability; the persisted snapshot must contain exactly one
allocation. This test remains unverified until the `pg-integration` Maven profile passes on
PostgreSQL. It tests the ledger's locking boundary, not the full `ReservationService` path.
