# Numou Omnichannel — Milestone 10: Encrypted Payload Storage

## Goal
Provide a portable short-lived inbound payload adapter without coupling the Omnichannel core to a cloud vendor.

## Included
- TenantPayloadKeyProvider supplies tenant-scoped AES keys from a KMS, HSM, or secret manager.
- EncryptedFileSystemInboundPayloadStore uses AES/GCM/NoPadding and authenticates tenant identity, format version, and retention deadline as additional authenticated data.
- SHA-256 is checked before writes; returned references are opaque UUIDs.
- Storage directories are tenant-derived and reads/deletes are always scoped to the requesting tenant.
- Payloads have finite retention, lazy expiry on read, and bounded purgeExpired cleanup.
- POSIX deployments receive best-effort owner-only permissions. Non-POSIX deployments must configure equivalent ACLs.

## Security and operational boundaries
- Use a dedicated protected volume and KMS/HSM/secret-manager-backed keys; never hard-code production keys.
- This adapter encrypts application payload bytes; protect the root volume and backups operationally.
- Ingress must enforce payload-size limits before buffering bytes.
- Schedule and monitor expiry cleanup to remove orphan payloads left by failures before inbox verification.
- Key rotation, distributed purge coordination, audit records for operator access, and cloud object-store adapters remain deployment concerns.
- Tenant authorization must be established before calling this storage port.

## Validation
Tests cover encrypted file contents, tenant isolation, digest mismatch rejection, expiry purge, and path traversal rejection. Maven tests have not been run in this environment; CI must verify Java compatibility, TenantId construction, and module wiring.
