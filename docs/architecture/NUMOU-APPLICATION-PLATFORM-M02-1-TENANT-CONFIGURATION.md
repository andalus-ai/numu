# Application Platform M02.1 — Tenant Application Configuration

## Ownership

The Application Platform owns tenant-specific configuration for an installed application. The application package owns its configuration schema and validation rules. The host owns actor identity, tenant context, authorization, and secret resolution.

Configuration does not become a second source of truth for business data owned by installed capabilities.

## Contract introduced

- A configuration snapshot is scoped by `(tenant_id, application_id)`.
- Every accepted write creates a monotonically increasing revision.
- Writes require an explicit expected revision. Revision zero means create-only.
- Schema validation and normalization happen before persistence.
- A snapshot carries its schema version, SHA-256 payload digest, actor, and timestamp.
- Configuration history is append-only.
- PostgreSQL tables use the same trusted transaction-local tenant RLS setting as installation state.
- Persisted configuration may contain opaque secret references only. Secret values must be resolved at runtime by a host-provided secret integration and must never be written to configuration rows, history, events, logs, or audit payloads.

## Current implementation slice

This increment introduces the immutable domain snapshot, schema-validation and persistence ports, authorization contract, write command/handler, and PostgreSQL migration for current state plus append-only revisions.

The PostgreSQL adapter implementation, migration registration/verification, runtime secret-reference resolver, read/history use cases, and full Testcontainers coverage remain required before M02.1 can be marked complete. Do not treat the presence of the migration or interfaces as proof that the runtime is production-ready.

## Required completion evidence

1. Schema validation binds to the installed package's declared schema version and rejects unsupported versions.
2. Authorization checks tenant ownership, installation state, and configure permission before reading or writing configuration.
3. Concurrent updates with the same expected revision yield exactly one successful write.
4. Current state and immutable revision history commit atomically.
5. Tenant A cannot read or mutate tenant B's configuration, including under the application database role.
6. The persisted digest is verified against the exact normalized payload bytes.
7. Secret values are absent from persistence, history, events, logs, and returned configuration; only opaque references are stored.
8. Tests cover create, update, stale revision, concurrent writes, schema rejection, tenant RLS, rollback, and append-only history.

## Compatibility

This is an additive application-platform contract. Existing application manifests and installation lifecycle remain unchanged. Configuration schema versions are package-owned; they are not the platform package SemVer. Schema migration between app versions must be explicit and must create a new tenant configuration revision rather than rewriting history.
