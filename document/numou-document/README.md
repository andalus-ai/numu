# Numou Document domain

The Document capability owns document identity, immutable versions, metadata,
classification and lifecycle. Binary persistence is delegated to the configured
storage adapter.

## Version-scoped malware scanning

Each immutable document version has an independent scan decision. A clean result
for version N must never authorize version N+1. The domain scan result records
the exact document/version IDs, scanner identity, signature version, verdict,
and scan timestamp.

- `CLEAN` is the only verdict that may authorize content delivery.
- `INFECTED` is a security rejection.
- `FAILED` is an operational failure and remains fail-closed.
- `PENDING` is the initial persisted state, not a completed scan result.

The application `DocumentScanner` port is provider-neutral. Its request binds
the tenant, document, immutable version, storage key and verified SHA-256. A
scanner adapter must inspect that exact object and must not trust upload metadata
or infer safety from upload completion.

The PostgreSQL adapter migration V7 creates a result row and durable scan job
for every existing and newly inserted document version. The trigger ensures
new versions enter the scan queue in the same database transaction as version
creation.

**Operational limitation:** this increment establishes the version-scoped
contract and durable queue schema. It does not yet include a scanner vendor
adapter, lease-claiming scan worker, result persistence command, or Feedback
event consumer. Until those pieces are deployed, all versions remain
`PENDING` and must be treated as unavailable for evidence downloads. No
Feedback attachment may become `AVAILABLE` based on this schema alone.
