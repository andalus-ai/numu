# Optional Quarkus runtime adapter

This module composes the transport booking application service with PostgreSQL inventory persistence and a Quarkus scheduled hold-expiration worker. It is optional: domain and application consumers can continue to invoke `DepartureBookingService` directly without Quarkus.

## Add to a Quarkus host

Declare `numou-transport-runtime-quarkus` as a dependency of the host runtime and configure its JDBC datasource. The module provides default CDI beans for `DepartureInventoryRepository`, `Clock`, and `DepartureBookingService`; a host may replace these with CDI beans of the same types to customize composition.

The PostgreSQL schema migration is packaged by `numou-transport-postgres-adapter`. The host must run that migration through its normal Flyway/bootstrap process before enabling the worker. Do not configure a shared application's migration locations to transport-only paths if that would hide other capabilities' migrations.

## Configuration

| Property | Default | Meaning |
|---|---:|---|
| `numou.transport.expiry.interval` | `5s` | Quarkus scheduler interval |
| `numou.transport.expiry.batch-size` | `200` | Maximum inventory keys processed per tick; valid range 1–10000 |

Environment-specific datasource credentials, TLS settings, migration policy, health endpoints, and management ports belong to the host application. Do not place credentials in this module.

## Operational behavior

- The scheduler uses Quarkus `concurrentExecution=SKIP` to avoid overlapping ticks within one process.
- Every tick processes a bounded candidate batch. If a batch is full, the next scheduled tick continues the backlog; the worker does not run an unbounded drain loop.
- Multiple application replicas may see the same due inventory. The repository's optimistic compare-and-set and the application's reload/re-evaluate loop make repeated processing safe; this is not a distributed scheduler lease.
- A failed tick increments a failure counter and logs the exception. It does not terminate the process; later ticks retry due work. Alert on sustained failures and on due-hold backlog using host-specific telemetry.
- Expiration is an inventory lifecycle transition, not a payment refund, cancellation of a confirmed ticket, or a notification. Those cross-capability side effects require a separately designed durable workflow/outbox.

## Metrics

The module registers these Micrometer metrics without tenant labels (to avoid leaking tenant data and high-cardinality labels):

- `numou.transport.hold.expiry.runs` — scheduler executions.
- `numou.transport.hold.expiry.transitions` — hold transitions to EXPIRED.
- `numou.transport.hold.expiry.failures` — failed executions.
- `numou.transport.hold.expiry.duration` — execution duration.

Expose metrics through the host's approved management endpoint and configure alerts according to its SLOs. This module does not open an additional network port or expose a public endpoint by itself.
