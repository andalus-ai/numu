# Supply-chain milestone 35: durable recovery-audit artifact storage

## Delivered
- Add a separate `numou-warehouse-artifact-filesystem` adapter module implementing the application artifact-store port.
- Isolate tenant/export paths with SHA-256-derived opaque directory names.
- Serialize writes across processes using an OS file lock scoped to each export.
- Persist per-chunk receipts and reject reuse of a chunk ID with different offset, length, or content.
- Make retries idempotent and repair a partial append when the durable bytes are an exact prefix of the retried chunk.
- Force artifact and receipt data before acknowledging the write, then compute archive size and SHA-256 from stored bytes.
- Add tests for duplicate retries, conflicting chunk IDs/offsets, tenant isolation, and append/read behavior.

## Deployment boundaries
- This is a durable local-filesystem adapter for single-host deployments, integration tests, and development. The root must be on storage whose locking, fsync, and rename behavior meets the deployment's durability needs.
- Multi-host deployments should use an object-storage adapter implementing the same application port; do not mount an arbitrary network filesystem and assume its locking semantics are sufficient.
- API authentication/authorization, download audit, retention policy, and deletion are not enabled by this adapter.
- The artifact location is a file URI and must not be exposed to untrusted clients. Downloads should go through an authorized application endpoint.
- Automated Maven and PostgreSQL integration tests have not been run in this environment.