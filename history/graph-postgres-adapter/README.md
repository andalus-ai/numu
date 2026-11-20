# History Graph PostgreSQL Adapter
PostgreSQL is the authoritative relationship and projection-event store. Apply Flyway migrations V1–V6 through the host's approved migration process before enabling delivery, operational stores, or rebuild lifecycle commands.

## Projection delivery
- JdbcHistoricalProjectionOutboxStore claims bounded batches with PostgreSQL FOR UPDATE SKIP LOCKED.
- Each claim receives a random lease token. Every acknowledgement, retry, or dead-letter transition is fenced by that token, preventing an expired worker from overwriting a newer worker's result.
- Expired leases are reclaimable. Lifetime attempts remain monotonic; retry_attempts is the bounded retry window and resets only on audited dead-letter replay.
- Replay appends an actor-attributed audit record in the same transaction as requeue.
- Projection adapters must be idempotent by event key and monotonic by aggregate revision. The graph provider is a rebuildable projection, never the source of truth.
The host supplies scheduling, worker identity, projection adapter, operational authorization for dead-letter inspection/replay, and metrics/alerts. Never expose raw store methods as tenant-facing endpoints.

## Rebuild lifecycle

- Rebuild runs capture an immutable outbox high-water sequence and advance only through version-fenced checkpoints.
- `advanceFenced` and `completeFenced` compare the observed lifecycle version and cursor; legacy unfenced methods deliberately reject use in this adapter.
- Pause, resume, cancel, fail, and retry are compare-and-set state transitions. The lifecycle state update and actor-attributed audit append share one PostgreSQL transaction.
- Lifecycle audit rows are tenant-scoped, forced-RLS protected, and immutable. Public corpus operations require the separate public-corpus maintenance permission.
- Retry preserves the last successful cursor and clears the active failure detail. COMPLETED and CANCELLED runs are terminal.
- The host must wire `HistoricalProjectionRebuildLifecycleService` as the rebuild worker's `HistoricalProjectionRebuildFailurePort` so provider failures become durable FAILED transitions with a configured service-principal actor. Do not run production rebuild workers without this wiring.
