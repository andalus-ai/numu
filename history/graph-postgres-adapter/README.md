# History Graph PostgreSQL Adapter
PostgreSQL is the authoritative relationship and projection-event store. Apply Flyway migrations V1–V3 before enabling delivery.

## Projection delivery
- JdbcHistoricalProjectionOutboxStore claims bounded batches with PostgreSQL FOR UPDATE SKIP LOCKED.
- Each claim receives a random lease token. Every acknowledgement, retry, or dead-letter transition is fenced by that token, preventing an expired worker from overwriting a newer worker's result.
- Expired leases are reclaimable. Lifetime attempts remain monotonic; retry_attempts is the bounded retry window and resets only on audited dead-letter replay.
- Replay appends an actor-attributed audit record in the same transaction as requeue.
- Projection adapters must be idempotent by event key and monotonic by aggregate revision. The graph provider is a rebuildable projection, never the source of truth.
The host supplies scheduling, worker identity, projection adapter, operational authorization for dead-letter inspection/replay, and metrics/alerts. Never expose raw store methods as tenant-facing endpoints.