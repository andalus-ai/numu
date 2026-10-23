# Supply Chain M11 — Leased warehouse recovery worker

## Delivered
- Added a durable claim contract to the execution-attempt repository.
- Added PostgreSQL claim owner/expiry columns and an index for recovery lease scans.
- Implemented bounded recovery claims using row locks and `SKIP LOCKED`; claims expire after a worker crash.
- Added `WarehouseExecutionRecoveryWorker` to claim one batch and invoke a host-provided recovery handler.
- Successful items release their claim; failed items retain the lease until expiry to avoid immediate retry loops.
- Added worker contract tests for success, failure, and invalid configuration.

## Operating model
The host application owns scheduling and supplies a recovery handler. The handler must use the journal's original tenant, command key, and payload identity and must rely on inventory and warehouse idempotency. The worker does not create a replacement command key.

Claims are bounded to 500 records and one-hour maximum leases. PostgreSQL transactions serialize candidate selection; `SKIP LOCKED` allows multiple workers to claim disjoint batches.

## Limitations
- A handler must be idempotent because a process may finish external work and crash before releasing its claim.
- Lease expiry is not a distributed transaction or proof that a previous worker stopped; optimistic journal transitions and downstream idempotency remain essential.
- The worker is a library component, not automatically scheduled. Runtime configuration and health/metrics integration are host responsibilities.
- PostgreSQL integration tests, concurrency tests against a real database, and a full Maven/CI run remain required before production use.
