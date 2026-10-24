# Supply-chain milestone 38: artifact receipt crash recovery

## Goal

Exercise the filesystem artifact store's recovery path for the crash window after artifact bytes are durably written but before the chunk receipt is committed.

## Changes

- Add a deterministic crash-state fixture containing the complete chunk bytes and no receipt.
- Retry the same chunk through the artifact-store API and verify it reconstructs the receipt without appending duplicate bytes.
- Construct a fresh adapter instance and repeat the same write to verify the reconstructed receipt remains idempotent across adapter restarts.
- Keep the existing partial-prefix repair, tenant isolation, conflicting chunk ID, offset conflict, and concurrent retry tests.

## Validation status

The regression test and documentation are committed to this branch. Maven tests have not been executed in this environment; CI must run the filesystem adapter module and warehouse reactor tests.

This covers one artifact-local crash window only. It does not prove the end-to-end database checkpoint, manifest insertion, job completion, or PostgreSQL worker-claim transaction boundaries. Those still require integration tests with a real supported database and controlled failure injection.

## Deployment boundary

The filesystem adapter remains a single-host/test implementation. Multi-host production use requires a separately implemented object-storage adapter with atomic/conditional chunk semantics and integration coverage for the chosen provider.
