# Numou Omnichannel — Milestone 13: Retry and ingestion recovery hardening

## Scope
M13 introduces bounded exponential retry with stable jitter and an operator-visible recovery path for events stranded in RECEIVED when payload persistence or verification transition did not finish.

## Retry policy
- Backoff grows exponentially from a configured base and is capped at a configured maximum.
- Stable per-event/per-attempt jitter spreads retry load while preserving reproducible timing.
- The worker applies the selected delay when it records a failure.
- Deployments should choose limits based on provider retry windows, downstream rate limits, and business SLA.

## Stale RECEIVED recovery
A scheduled recovery job may invoke the recovery port's quarantineStaleReceived(cutoff, now, limit) operation. The PostgreSQL adapter uses FOR UPDATE SKIP LOCKED and bounded batches to move stale RECEIVED rows with no payload reference to DEAD_LETTER, recording only the sanitized code INGESTION_INCOMPLETE.

These records are quarantined, not automatically retried: no verified payload reference exists, and dispatching an unverified body would violate the trust boundary. Operator tooling must arrange provider redelivery/re-ingestion. The operation is safe to run concurrently on multiple nodes.

## Operational boundaries
- This does not provide exactly-once side effects; downstream use cases still need idempotency keyed by tenant/provider/account/event identity.
- The recovery method is a port and adapter, not a scheduler. Runtime deployment must wire a scheduled invocation and alert on quarantined records.
- PostgreSQL concurrency integration tests and full Maven reactor verification remain required before production rollout.
