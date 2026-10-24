# Numou Omnichannel — Milestone 14: Operational reliability

## Goal

Make the inbound worker easier to operate and safer to integrate without coupling the core module to Micrometer, OpenTelemetry, a cloud provider, or a specific runtime.

## Included

- `InboundEventObserver` provides optional low-cardinality outcome and sanitized-failure hooks. Runtime adapters can bridge these hooks to Micrometer/OpenTelemetry or another metrics system.
- `InboundEventIdempotencyKey` derives a stable opaque key from tenant, provider, provider account, and external event ID. The key is length-prefixed before SHA-256 hashing so separator characters cannot create ambiguous identities.
- `VerifiedInboundEvent.idempotencyKey()` exposes this key to application dispatchers while keeping the payload indirect.
- Worker observer callbacks are best-effort: observer exceptions must not fail or alter event processing.

## Delivery and idempotency contract

The worker provides **at-least-once dispatch**, not exactly-once business effects. A process can commit a business effect and crash before the inbox row is marked `PROCESSED`; the event can then be dispatched again after lease expiry. The dispatcher should pass `event.idempotencyKey()` to the target application use case and the target must atomically persist/check that key with its business effect, or use an equivalent domain-native idempotency mechanism.

The key is stable for retries and replays of the same tenant/provider/account/event identity. It intentionally changes if any scope component changes. It is not authorization, a signature, or a substitute for tenant checks.

## Operational metrics

Recommended counters:
- worker outcomes, tagged only by the finite `InboundWorkerDisposition` enum;
- retry/dead-letter failures, tagged by the sanitized error code emitted by the worker;
- stale-lease completion attempts and no-work polls.

Recommended gauges/alerts, supplied by the runtime and/or database queries:
- age and count of due `RETRYABLE_FAILURE` rows;
- count/age of `DEAD_LETTER` rows by bounded error code;
- expired `PROCESSING` leases;
- stale `RECEIVED` rows without payload references (`INGESTION_INCOMPLETE`).

Never use tenant IDs, provider account IDs, external event IDs, correlation IDs, payload contents, or exception messages as metric labels. They create high cardinality and may leak sensitive data. Use secured operator queries for event-level investigation.

## Out of scope

This milestone does not add a metrics vendor, a scheduler, a replay endpoint, or automatic dead-letter replay. Replaying a dead letter requires an explicit audited operator action and a clear decision about whether the downstream idempotency ledger is intact. PostgreSQL-backed concurrency/crash integration tests and production dashboards remain deployment validation work.
