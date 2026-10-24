# Supply-chain milestone 37: recovery-audit retry concurrency

## Goal

Strengthen the local durable artifact adapter's retry contract under concurrent worker attempts. The export worker writes an artifact chunk before checkpointing the database, so concurrent retries must not duplicate committed bytes or produce divergent digests.

## Changes

- Add a concurrent retry test that invokes the same tenant/export/chunk write 24 times across eight threads.
- Assert every successful retry reports the same byte count and SHA-256 digest, and the resulting artifact contains exactly one copy of the chunk.
- Correct the filesystem adapter tests to use actual newline bytes in their CSV fixtures rather than the two-character backslash-n sequence.
- Retain the existing partial-append recovery, conflicting chunk ID, offset conflict, and tenant isolation tests.

## Validation and limits

The test source has been added, but Maven tests have not been executed in this environment. CI must run the filesystem adapter module tests and the full warehouse reactor. This is a deterministic same-process concurrency test; it does not replace multi-process locking tests, PostgreSQL/Testcontainers lease-claim tests, or end-to-end crash injection between artifact fsync, database checkpoint, manifest insertion, and job completion.

The filesystem adapter remains intended for single-host deployments and tests. Multi-host production deployments need an object-storage adapter with suitable conditional-write semantics.
