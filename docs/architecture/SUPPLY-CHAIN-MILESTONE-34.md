# Supply-chain milestone 34: recovery-audit export worker orchestration

## Scope
- Process one bounded tenant-scoped audit page per worker invocation.
- Write each chunk through an artifact-store contract requiring durable, idempotent chunk writes at an expected byte offset.
- Persist job checkpoints only after artifact writes are acknowledged as durable.
- Verify the finished artifact against SHA-256 and byte-size metadata before persisting the immutable V7 manifest and completing the job.
- Record claim/resume and verification outcomes in the append-only export-access audit.

## Adapter obligations
- Implement WarehouseRecoveryAuditArtifactStore with durable writes and stable idempotency for the tenant, export, and chunk identity. Retried writes must not duplicate bytes; conflicting offsets must fail closed.
- Artifact inspect must calculate size and SHA-256 from the durable object, not trust caller-supplied metadata.
- Job and manifest persistence must enforce tenant isolation and immutable manifest semantics.
- The API layer remains responsible for authentication, authorization, and deriving tenant identity from trusted security context.
- Do not include object-store credentials, signed URLs, raw exception details, or PII in access events.
- Retention and deletion remain disabled.

## Validation
The worker is bounded to one page per call. Unit tests, database tests, and a concrete artifact-store adapter are required before production enablement.
