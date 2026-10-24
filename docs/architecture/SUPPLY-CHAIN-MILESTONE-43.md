# Supply-chain milestone 43: checkpoint-outage recovery and stable chunk identity

## Problem

The worker writes an artifact chunk durably before updating the job checkpoint. If the checkpoint transaction fails, the database still contains the prior byte offset. After the worker lease expires, a new claim increments the job version. Deriving the chunk ID from that claim version therefore changes the idempotency key even though the retry represents the same logical output range.

## Changes

- Derive a chunk idempotency key from the stable export ID and the durable checkpoint byte offset, not the mutable job version.
- Add worker-level failure injection for a persistence outage after artifact bytes are written but before the checkpoint is committed.
- Advance a test clock past the lease expiry and retry from the unchanged checkpoint.
- Assert the recovered job completes, the artifact bytes are physically written only once, and the same logical chunk receipt is reused.

## Safety properties

- The artifact remains the write-ahead durable boundary: the checkpoint is never advanced before the artifact store confirms the chunk.
- A retry at the same checkpoint computes the same chunk identity even when a new worker claim changes the job version.
- Conflicting content at an already-written offset must still be rejected by the artifact-store adapter; this test's in-memory adapter models the idempotent retry contract.

## Validation

Maven tests were not run while authoring this milestone. Run the warehouse application and filesystem artifact-store test suites. PostgreSQL-backed claim and lease recovery tests also need to run in an environment configured with the dedicated test database variables.