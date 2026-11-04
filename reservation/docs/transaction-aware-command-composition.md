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

## Durable outbox relay contract

The PostgreSQL adapter now exposes a transport-neutral `ReservationOutboxPublisher`
and a `PgReservationOutboxRelay`. The relay claims a bounded batch using
`FOR UPDATE SKIP LOCKED`, commits an expiring lease before invoking the publisher,
and acknowledges only while its unique batch lease token is still current. Expired
processing leases can be reclaimed after a worker crash. Failed delivery uses capped
exponential backoff and transitions to `DEAD` when the configured attempt limit is
reached. Rows whose attempt limit is exhausted while pending or with an expired lease
are also moved to `DEAD`.

Delivery is **at-least-once**, not exactly-once: a process can publish successfully and
crash before the database acknowledgement commits. Publishers and consumers must use
the stable event ID as an idempotency/deduplication key. The relay deliberately does not
own scheduling, broker credentials, transport-specific serialization, metrics, or runtime
shutdown; the host composition root supplies the publisher and polling lifecycle.

The opt-in PostgreSQL relay contract tests cover successful acknowledgement, retry/dead-letter
transition, expired-lease recovery, and concurrent pollers. Run them with:

```bash
mvn -f reservation/pom.xml -pl reservation-adapter -am -Ppg-integration verify
```

These tests have not been executed as part of authoring this milestone. Production rollout
still requires a concrete publisher/runtime wiring, operational metrics and alerting, and a
successful run against PostgreSQL in CI or a controlled environment.


## Publish/ack gap and duplicate-delivery contract

A successful external publish followed by an expired lease before database acknowledgement is an expected at-least-once failure mode. The relay must reject the stale acknowledgement, leave the row reclaimable, and allow a later worker to publish the same stable event ID. Downstream consumers therefore must deduplicate on the event ID; broker producer idempotence alone does not close the database/broker transaction gap.

The PostgreSQL integration contract now includes this publish/ack-gap scenario in addition to concurrent pollers and retry/dead-letter recovery. It remains opt-in and is not verified until the documented `pg-integration` Maven command passes against PostgreSQL.


## Optional Quarkus outbox runtime

The `reservation-runtime-quarkus` module provides a host-runtime adapter without adding
Quarkus or Kafka dependencies to the reservation domain or PostgreSQL adapter:

- `KafkaReservationOutboxPublisher` serializes the complete delivery envelope and uses
  the stable event ID as the Kafka record key.
- `ReservationOutboxRuntime` constructs the relay from the host-provided reactive
  `PgPool` and publisher, then polls on a reactive Quarkus scheduled task. Overlapping
  polls are skipped within one process; PostgreSQL leases and `SKIP LOCKED` coordinate
  replicas.
- Poll failures are logged and the relay's persisted retry/lease behavior keeps events
  recoverable. This is not a substitute for production alerting or consumer-side deduplication.

To opt in, add `numou-reservation-runtime-quarkus` to a Quarkus host and configure the
reactive PostgreSQL client plus Kafka credentials/servers. The optional defaults are in
`reservation-runtime-quarkus/src/main/resources/application-reservation-outbox.properties`;
load that file through the host's supported configuration-location mechanism or copy its
non-secret settings into the host's `application.properties`. Set
`NUMOU_RESERVATION_EVENTS_TOPIC` for the target topic and provide the host's Kafka
bootstrap configuration. Never package credentials in the module.

The relay publishes the JSON envelope with `eventId`, `tenantId`, `aggregateType`,
`aggregateId`, `eventType`, `payloadJson`, `occurredAt`, and `attempts`. Consumers
should use `eventId` for deduplication. Kafka producer idempotence helps within Kafka's
producer session but cannot atomically commit a PostgreSQL acknowledgement, so duplicates
remain possible after a publish/ack crash.

This milestone wires scheduling and a Kafka adapter but does not yet provide dedicated
outbox metrics, dead-letter alerting, operator replay tooling, or a graceful in-flight drain.
The module and its runtime behavior remain unverified until the reservation reactor and a
host-level Quarkus integration test pass.


## Outbox runtime operational signals and shutdown

The optional Quarkus runtime registers these Micrometer counters:

- `numou.reservation.outbox.polls`: poll attempts started.
- `numou.reservation.outbox.events.published`: events successfully published and acknowledged by the PostgreSQL relay.
- `numou.reservation.outbox.poll.failures`: poll-level failures.
- `numou.reservation.outbox.shutdown.drain.timeouts`: shutdowns where the active poll exceeded the configured drain window.

Expose these through the host's configured Micrometer registry/exporter and alert on sustained poll failures, increasing dead-letter rows, and a growing pending/expired-lease backlog. Counter metrics intentionally avoid tenant or event IDs as labels to prevent high-cardinality and sensitive dimensions. The current milestone does not yet expose a database backlog gauge or dead-letter counter; those require a bounded aggregate query and should be added with operational alert definitions.

On shutdown, the runtime stops admitting new polls and waits up to
`numou.reservation.outbox.shutdown-drain-timeout` (default `PT10S`) for the active poll.
If the timeout expires, it logs a warning and leaves unacknowledged rows to recover through
lease expiry. This is bounded graceful draining, not a guarantee that an external broker
publication and database acknowledgement are atomic.


## Outbox operational metrics

The Quarkus runtime exports low-cardinality Micrometer counters and gauges:

| Metric | Type | Meaning |
| --- | --- | --- |
| `numou.reservation.outbox.polls` | counter | Scheduled relay poll attempts |
| `numou.reservation.outbox.events.published` | counter | Events acknowledged as published |
| `numou.reservation.outbox.poll.failures` | counter | Relay poll failures |
| `numou.reservation.outbox.shutdown.drain.timeouts` | counter | Shutdowns that exceeded the configured drain window |
| `numou.reservation.outbox.backlog.refresh.failures` | counter | Failures refreshing database backlog gauges |
| `numou.reservation.outbox.events.pending` | gauge | Current pending row count |
| `numou.reservation.outbox.events.processing` | gauge | Current leased/in-flight row count |
| `numou.reservation.outbox.events.dead` | gauge | Current dead-letter row count |
| `numou.reservation.outbox.oldest.pending.age.seconds` | gauge | Age of the oldest pending event |

Backlog gauges refresh at `numou.reservation.outbox.metrics-refresh-interval` (default
15 seconds). Metrics are global to the reservation outbox table and deliberately do not
use tenant IDs as labels, avoiding unbounded cardinality and tenant activity leakage.
Operational alert rules should be owned by the deployment: alert on sustained growth in
pending count/oldest age, any dead-letter growth, repeated poll/refresh failures, and
shutdown drain timeouts. Thresholds must be set from the service's SLO and workload rather
than hard-coded into the reusable capability.
