# Numou Omnichannel — Milestone 17: Operations Metrics and Bounded Replay

## Scope

M17 builds on M14's worker observer and M16's audited replay boundary.

- A dependency-free, thread-safe metrics sink and snapshot count worker outcomes and failures by the fixed InboundWorkerDisposition enum.
- Metrics do not use tenant IDs, external event IDs, payload data, exception messages or arbitrary provider values as dimensions.
- MetricsInboundEventObserver adapts worker observations to the metrics sink. Production deployments can bridge the contract to Micrometer or OpenTelemetry without adding a metrics library to the core.
- DeadLetterReplayBatchService processes 1–100 unique event keys sequentially, checks authorization for every event, and returns an outcome per key.
- Each successful replay retains M16's transactionally coupled state transition and audit entry. A batch is not a single transaction: partial success is explicit in the per-item results.

## Integration guidance

Wire MetricsInboundEventObserver into InboundEventWorker using the existing observer constructor. Export snapshots or adapt InboundEventMetrics to the deployment's metrics stack. Keep dimensions low-cardinality.

Expose replay through an authenticated host-application operator endpoint. Resolve actor identity from the authenticated principal; never trust a caller-supplied actor ID. Apply endpoint-level rate limiting and permission checks in addition to the per-event authorizer. Use the batch response to show per-event outcomes without exposing payload contents.

## Semantics and limits

The batch coordinator is deliberately sequential to bound load and preserve simple per-item audit semantics. The maximum is 100 keys per call. Duplicate keys are rejected. A forbidden item returns FORBIDDEN without revealing whether the event exists. Successful replay preserves the original event identity and attempts. Downstream business handlers still need durable idempotency.

## Validation

Unit tests are included for the metrics adapter and batch replay guardrails. Maven compilation and PostgreSQL integration tests must be run in CI before production rollout; no test pass is claimed by this change.
