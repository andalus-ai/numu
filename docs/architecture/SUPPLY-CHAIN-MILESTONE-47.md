# Supply-chain milestone 47 — stale worker fencing

## Goal

Prove that an export worker which resumes after its lease has been reclaimed cannot overwrite the replacement worker's durable checkpoint.

## Implementation

The PostgreSQL integration test starts with a pending export, claims it for a first worker, simulates expiry in an isolated test schema, and reclaims it for a replacement worker. The first worker then attempts to write a checkpoint derived from its earlier job version.

The test asserts that the stale update returns `false` and that the persisted owner, version, cursor, event count, and byte count remain those of the replacement claim.

## Fencing model

The repository's monotonically increasing job version is the fencing token. Claiming a job increments the version; every checkpoint update uses the caller's expected version in its SQL predicate. A stale process therefore cannot update the row after a later claim has advanced the version.

This milestone adds an executable regression test rather than introducing a second lease-token mechanism. The integration test is opt-in and uses a randomly named PostgreSQL schema that it drops after execution.

## Validation status

- PostgreSQL integration test added; it requires `NUMOU_TEST_POSTGRES_URL` and optional `NUMOU_TEST_POSTGRES_USER` / `NUMOU_TEST_POSTGRES_PASSWORD`.
- Maven tests were not run as part of this change.
- The test does not establish multi-host filesystem artifact-store safety; that remains a separate deployment concern.
