# POS Platform M9 — Managed Outbox Worker

## Goal

Run the provider-neutral outbox dispatcher as a managed application worker without coupling the POS application layer to a scheduler or broker vendor.

## Runtime composition

- `PosOutboxDispatcherProducer` composes the dispatcher from the delivery-store and event-publisher ports.
- `PosOutboxDispatchJob` invokes one bounded dispatch pass on the Quarkus scheduler.
- `concurrentExecution = SKIP` prevents overlapping passes in one process.
- PostgreSQL `FOR UPDATE SKIP LOCKED`, lease expiry, and fencing tokens coordinate claims across replicas.
- A failed claim/store pass is logged and retried on the next scheduler tick; individual publication failures use the dispatcher's retry/dead-letter policy.

## Configuration

| Property | Default | Meaning |
| --- | --- | --- |
| `numou.pos.outbox.dispatch-interval` | `1s` | Scheduler polling interval |
| `numou.pos.outbox.lease-duration` | `PT30S` | Claim lease duration |
| `numou.pos.outbox.initial-backoff` | `PT1S` | First retry delay |
| `numou.pos.outbox.max-backoff` | `PT5M` | Retry delay ceiling |
| `numou.pos.outbox.batch-size` | `100` | Maximum events claimed per pass |
| `numou.pos.outbox.max-attempts` | `12` | Attempts before dead-letter status |

Durations use ISO-8601 format except the scheduler interval, which uses Quarkus scheduler duration syntax.

## Deployment and safety

- Disable the scheduler in deployments that do not own delivery responsibility using the Quarkus scheduler's supported disable mechanism for the configured schedule.
- A concrete `PosEventPublisher` must be supplied by a deployment integration; this milestone does not select a broker vendor.
- Delivery remains at-least-once. A process can publish successfully and crash before marking the row published; downstream consumers must use the M8 inbox boundary.
- Graceful shutdown relies on Quarkus scheduler lifecycle and bounded dispatch passes; a claim that outlives shutdown is recovered by lease expiry.

## Verification still required

The repository Maven build and CI have not been executed by this change. Add Quarkus runtime tests for schedule activation, disabled scheduling, exception recovery, and multi-replica claim behavior. Confirm each deployment supplies exactly one publisher bean. Add metrics and dead-letter administration in the next operational milestone.
