# Supply Chain Milestone 54 — Manifest and checkpoint reconciliation

## Problem and ownership

The warehouse recovery-audit export worker owns the transition from a durable artifact to a completed export job. The archive manifest is immutable integrity evidence, while the job row is mutable orchestration state. A process or database interruption can leave a persisted manifest while the job is still in verification, for example if the manifest insert commits but the final job update fails.

## Changes

- Centralize manifest evidence comparison in the immutable manifest contract.
- Reconcile a newly inspected artifact and current job checkpoint against any persisted manifest before completing the job.
- When a manifest already exists, verify artifact bytes against that persisted manifest, preserving its original creation timestamp instead of constructing a replacement manifest as the verification authority.
- Insert a new manifest only after the candidate artifact has passed verification. Existing conflicting manifest evidence fails closed and is not overwritten.
- Add tests that prove creation time may differ on a retry while export identity, tenant, location, digest, size, event count, and creator must remain consistent.

## Recovery outcomes

- **Recoverable interrupted finalization:** persisted manifest evidence matches the inspected artifact and checkpoint; verification may be repeated and the job may transition to completed.
- **Conflicting manifest/checkpoint:** export identity, tenant, artifact location, digest, size, event count, or creator differs; fail closed without mutating the existing manifest.
- **Persistence failure:** retain the durable manifest and let the job's existing retry/lease recovery path resume reconciliation.

The manifest repository and job repository remain separate ports; this change does not assume a distributed transaction across them. The persisted manifest is the integrity authority after it exists.

## Validation

Focused unit tests were added for stable manifest evidence comparison and drift detection. Maven tests were not run in this environment. Review the affected warehouse reactor's CI results before merging.
