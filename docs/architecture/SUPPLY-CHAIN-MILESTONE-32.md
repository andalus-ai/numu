# Supply Chain Milestone 32 — Audit archive integrity verification

## Scope

M32 introduces immutable archive-manifest metadata and a streaming SHA-256 verifier for warehouse recovery audit exports. It does not upload files, create export jobs, or decide retention/deletion; those operations must be orchestrated by an authorized application boundary and object-storage adapter.

## Integrity contract

- A manifest records tenant, export ID, artifact location, SHA-256 digest, byte size, declared event count, creation time, and trusted creator identity.
- The verifier reads the artifact as a stream using a fixed-size buffer; it does not load the complete artifact into memory.
- Verification succeeds only when both byte size and SHA-256 match the immutable manifest.
- Event count is recorded as export metadata and is not inferred from newline count because CSV fields can legally contain line breaks.
- Persist the manifest only after the artifact is fully written and durably committed to its storage destination. If storage supports object version IDs, include them in the artifact location or add a dedicated version field before production rollout.
- A mismatch must quarantine the artifact and alert operators; do not treat a checksum mismatch as a transient success or automatically delete either copy.

## Persistence and access control

Migration V7 adds a manifest table and tenant/time index. Export IDs are globally unique. Repository implementations must scope reads by both tenant and export ID, reject duplicate IDs, and expose no update/delete operation in the ordinary application port. Runtime database roles should receive INSERT and SELECT only for this table where possible.

Artifact locations must not contain public bearer tokens or credentials. Store artifacts in encrypted, access-controlled storage, and resolve tenant authorization independently of the caller-supplied export ID. Record archive creation, verification failures, downloads, and administrative changes in the platform security audit facility.

## Retention boundary

No automatic deletion or retention duration is introduced. Archive verification is a prerequisite for a separately approved archive-and-retention workflow, not authorization to delete source audit rows.

## Validation

Unit tests cover streaming checksum/size success, tampered bytes, incorrect size, and manifest validation. Maven and PostgreSQL migration/integration tests must run in CI before deployment; they have not been executed in this environment.
