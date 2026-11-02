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
