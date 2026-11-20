# Affiliate & Partner Capability

The Affiliate capability owns partner programs, referrals, attribution, commissions, payouts, and its durable integration outbox. It remains application-neutral; host applications decide how tenant workers and event transports are composed.

## Outbox runtime integration (M18)

The `numou-affiliate-runtime` module provides:

- `AffiliateEventPlatformPublisher`, an adapter from the Affiliate publisher SPI to the shared `EventPublisherPort`.
- A versioned `AffiliateOutboxBusinessEvent` (contract version 1.0) that preserves the original outbox event ID, event type, timestamp, aggregate reference, event key, and JSON payload.
- `AffiliateEventMetadataResolver`, a required host-supplied boundary that resolves opaque Affiliate tenant keys through the authoritative tenant registry and supplies actor/correlation/audit metadata. The adapter rejects missing tenant UUIDs and event-ID substitution; it never parses or hashes tenant identifiers.
- `AffiliateOutboxManagedWorker`, a single-tenant worker implementing the shared `ManagedWorker` lifecycle. Compose one worker per tenant or tenant shard. PostgreSQL row leases remain the cross-process ownership mechanism; delivery is at-least-once and consumers must deduplicate by event ID.
- `AffiliateOutboxMetrics`, which exports low-cardinality aggregate gauges and counters through `MetricsPort`. Tenant, actor, partner, and event identifiers are intentionally not metric tags.

The host must provide an `EventPublisherPort`, an `AffiliateEventMetadataResolver`, a shared `MetricsPort`, and the tenant/worker composition. Do not expose a worker to a tenant until the host has authorized that tenant's execution context.

### Operational notes

- Worker scheduling is per tenant and bounded to one in-flight dispatch pass per worker instance.
- A shutdown waits for the current dispatch and health refresh up to the configured timeout; timeout is surfaced as a lifecycle failure rather than reported as a clean stop.
- Outbox claims recover expired leases and remain safe across competing worker instances through PostgreSQL `SKIP LOCKED` and lease ownership checks.
- Event delivery is at-least-once. A crash after broker acceptance but before outbox acknowledgement can redeliver the same event ID.
- Tenant IDs in Affiliate storage are opaque strings. Mapping to the event platform's UUID tenant field is a host identity responsibility, not a local conversion rule.


## M19 — Managed worker lifecycle hardening

- Dispatch completion always proceeds to the health refresh even if the metrics backend throws; telemetry failures are deliberately isolated from outbox delivery progress.
- A failed health read or failed gauge update cannot leave the worker's in-flight gate permanently closed.
- Runtime tests cover repeated start/stop calls and prove that a telemetry failure does not prevent later scheduled dispatch attempts or health refreshes.
- Register one shared `AffiliateOutboxMetrics` instance per application/runtime. The metric names are process-wide and intentionally have no tenant labels; do not create a separate metrics adapter for each tenant worker.
- A worker remains single-tenant and owns only its scheduling loop. PostgreSQL leases, not JVM synchronization, coordinate competing processes.


## M20 — outbox lease fencing

- Every successful claim returns the persisted outbox row version as its lease fencing version.
- Delivery acknowledgement and retry/dead-letter rescheduling must present that exact version in addition to tenant, event, worker, and unexpired lease ownership.
- Reclaiming an expired lease increments the row version. A late completion from the previous claim is rejected even when a deployment reuses the same worker ID.
- This is an optimistic fencing token, not a broker exactly-once guarantee. A publish accepted by the broker before a process crash may still be delivered again; consumers continue to deduplicate by the stable outbox EventId.
- The existing version column is reused; no new migration is required. The SPI signature change requires custom AffiliateEventOutboxStore implementations to carry the claim version through completion operations.


## M21 — claimed-batch failure isolation

- A stale lease or persistence failure for one claimed event no longer prevents the dispatcher from attempting later independent claims in the same bounded batch.
- Retry/dead-letter rescheduling is still attempted with the original claim's fencing version. If that operation also fails because the lease has been reclaimed, the dispatcher records the batch-level failure and continues processing remaining events.
- The final batch result reports a failure after all claimed events have been attempted. The exception carries the count of events whose delivery acknowledgement succeeded, so runtime telemetry does not lose successful partial progress.
- This is not a claim that the failed row was recovered: a row whose lease completion and reschedule both fail remains protected by fencing and is eligible for normal lease-expiry recovery.
