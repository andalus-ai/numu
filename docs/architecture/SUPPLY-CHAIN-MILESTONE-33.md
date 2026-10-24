# Supply-chain milestone 33: durable recovery-audit export jobs

## Scope
- Model durable export states: REQUESTED, RUNNING, VERIFYING, COMPLETED, FAILED.
- Persist continuation cursors and monotonic event/byte checkpoints for restart recovery.
- Require a live worker lease for checkpointing and verification; expired leases may be reclaimed.
- Require verification before completion and an artifact location before marking a job complete.
- Add tenant-scoped job and append-only access-audit repository ports.
- Add Flyway V8 job/access-audit tables and indexes.

## Safety and operational constraints
- Persist each checkpoint only after its corresponding output chunk is durable. The model cannot itself guarantee object-store durability; worker adapters must enforce this ordering.
- Repository implementations must enforce tenant scope and optimistic version checks. claimNext must atomically claim one eligible row (for example, PostgreSQL FOR UPDATE SKIP LOCKED) and reclaim only expired leases.
- Record request, claim/resume, download, and verification success/failure access events; authorize before serving artifacts.
- Failure codes are identifiers, not raw exception messages, to avoid leaking secrets or PII.
- Verify the completed artifact against the V7 manifest before transitioning VERIFYING to COMPLETED.
- Retention and deletion remain disabled; this milestone adds no purge scheduler or delete API.

## Validation
Unit tests cover state transitions, monotonic checkpoints, lease contention/reclaim, and verification gating. Maven and PostgreSQL migration/integration tests still need to run in CI.
