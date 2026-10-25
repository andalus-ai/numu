# Supply Chain Milestone 55 — Export lifecycle fault injection

## Goal

Exercise the recovery-audit export worker at the boundary between a durable final-page checkpoint and artifact verification. The worker must distinguish a retryable storage inspection failure from evidence that the artifact is invalid.

## Added coverage

- Inject a one-time I/O failure from artifact inspection after the final page bytes and checkpoint are durable.
- Assert the worker leaves the job recoverable, does not create a manifest before inspection/verification succeeds, and does not append a second copy of the artifact.
- Advance the test clock past the lease and retry as a different worker.
- Assert recovery completes from the existing final checkpoint and persists exactly one manifest.

This complements the existing crash-window cases for durable chunk writes followed by checkpoint persistence failure, manifest persistence failure, completion persistence failure, artifact-size drift, checksum mismatch, and conflicting persisted manifest evidence.

## Recovery contract

A transient artifact-store I/O error after the final checkpoint must not cause the worker to append the first page again or create integrity evidence prematurely. Once the lease expires, a new worker resumes from the checkpoint, re-inspects the existing artifact, verifies its bytes, persists/reuses the manifest, and completes the job.

The test uses an in-memory artifact store with a deterministic one-shot failure; it requires no external service and adds no production dependency or public API.

## Validation

The test and documentation were added to the supply/warehouse/warehouse-application reactor. Maven tests were not run in this environment. Confirm the focused warehouse application tests and repository CI before merging.
