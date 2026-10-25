# M25 — Reliable external outbox publication

## Why this is separate from the POD projector

The existing `courier_event_outbox.published_at` is also the acknowledgement marker for the database-local POD tracking projector. M25 deliberately introduces `courier_event_publications`, a per-destination delivery ledger, rather than reusing that marker. The same event can be projected internally and published externally without one consumer suppressing the other.

## Delivery guarantees

- Outbox event creation remains transactional with the business change.
- A destination-specific publication row is created idempotently from the event outbox.
- Rows are claimed under PostgreSQL row locks with `SKIP LOCKED`, leased, and acknowledged only after the configured publisher returns successfully.
- A worker crash leaves an expiring lease; another worker can recover the row.
- Failures use exponential retry delay capped at one hour. After 12 attempts the delivery becomes `DEAD` and remains available for operator inspection/replay tooling.
- This is at-least-once delivery, not exactly-once delivery. A process can crash after the destination accepts a message but before local acknowledgement. Consumers must deduplicate on `tenantId + eventId`.
- Publication state is isolated by destination, allowing independent downstream subscribers.

## Runtime configuration

- `numou.courier.outbox.interval`: poll interval, default `5s`.
- `numou.courier.outbox.batch-size`: batch size, default `100`, allowed range 1–500.
- `numou.courier.outbox.destination`: logical destination name, default `default`.

A deployment must provide a CDI bean implementing `CourierEventPublisher` for its broker or event gateway. Without that adapter, the worker logs at debug and leaves durable rows untouched; it does not fake successful publication. Configure transport-side timeouts shorter than the 60-second lease. The stable idempotency key is the tenant ID and event ID.

## Validation still required

Run the courier PostgreSQL adapter's Maven tests and migration-chain tests against PostgreSQL. Add/verify integration coverage for two concurrent workers, lease expiry, transport success/failure, dead-letter threshold, tenant isolation, multiple destinations, and crash-after-remote-acceptance duplicate delivery. This connector session cannot execute the repository's Maven build, so no test pass is claimed.
