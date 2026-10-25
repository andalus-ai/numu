# ADR-004: Configurable document binary storage providers

- **Status:** Proposed for review
- **Date:** 2026-10-25
- **Decision owners:** Numou platform maintainers

## Context

Document references and evidence links are used by multiple capabilities, but binary content has different deployment needs. A standalone application may need a local directory, a cloud deployment may use a managed S3 service, and a self-hosted deployment may use an external S3-compatible service such as RustFS. These deployment choices must not leak into project, Workspace, or document-reference domain models.

## Decision

1. Keep the storage contract provider-neutral in `numou-document-storage-spi`.
2. Isolate local filesystem behavior in `numou-document-storage-local`.
3. Isolate S3 protocol behavior in `numou-document-storage-s3`; use the same protocol adapter for AWS S3 and compatible endpoints such as RustFS.
4. Keep mode selection and client construction in `numou-document-storage-config`, not in the domain contract.
5. Support explicit `LOCAL`, `CLOUD`, and `EXTERNAL` modes. `EXTERNAL` defaults to provider `rustfs` and a development endpoint of `http://localhost:9000`; production external deployments must use HTTPS.
6. Fail on invalid configuration. Never silently switch providers when a selected provider is unavailable.
7. Treat tenant-prefixed keys as namespacing, not authorization. The consuming application must authorize each operation before invoking the storage port.
8. Keep metadata and lifecycle concerns distinct from binary bytes. Version retention, signed URLs, malware scanning, legal hold, and document-level authorization require explicit future contracts rather than being implied by this storage port.

## Consequences

- Applications can choose storage topology at composition time without changing domain code.
- AWS S3 and RustFS share the S3 adapter, while endpoint, region, credentials, and path-style access are deployment configuration.
- Credentials are supplied through a secrets manager/environment or the AWS SDK default credential chain; they must not be committed.
- The host application remains responsible for authorization, observability, bucket provisioning/policy, encryption, backups, timeouts, and production operations.
- The initial S3 adapter stores a supplied SHA-256 value as object metadata but does not independently verify the remote upload digest; the upload application must compute/verify the digest before storage.
- This ADR is a target decision proposal until reviewed and accepted.
