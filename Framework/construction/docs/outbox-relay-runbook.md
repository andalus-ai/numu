# Outbox relay runbook

## Runtime wiring

1. Provide an `OutboxRelayStore` implementation backed by PostgreSQL.
2. Provide an `OutboxDeliveryPort` adapter for the selected broker.
3. Construct `OutboxRelayService` with a stable worker identifier, injected UTC clock, bounded batch size, lease duration, and retry policy.
4. Invoke `runOnce()` through the existing scheduler/job mechanism; do not add a new scheduler dependency until the module's current dependency conventions are confirmed.
5. Set broker request timeouts shorter than the lease duration, or implement lease renewal for long-running deliveries.

## Operational signals to expose

- claimed events per run;
- successful acknowledgements and lease-token misses;
- retries by attempt number and terminal failures;
- age of oldest pending event;
- claim query duration and delivery latency;
- outbox row count by status.

## Recovery

- Expired leases should become claimable again in the database adapter.
- A lease-token mismatch means another worker reclaimed the row; do not overwrite the newer worker's state.
- Terminal rows require an explicit operator replay process that preserves the event ID and records who initiated replay.
- Do not delete published rows until retention and audit requirements are agreed.

## Required integration tests

- delivery succeeds and row is marked published;
- delivery fails and retry time follows the configured backoff;
- max attempts marks an event terminal;
- lease expires and another worker can reclaim the event;
- stale worker cannot acknowledge after a newer lease is issued;
- broker accepts an event but acknowledgement fails, resulting in safe duplicate delivery;
- one event failure does not prevent independent events in the same batch from being processed.

This runbook is implementation guidance. No production adapter, scheduler registration, or broker client is included in this starter bundle.
