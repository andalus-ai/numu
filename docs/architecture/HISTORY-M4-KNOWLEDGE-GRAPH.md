# History M4 — Typed Historical Knowledge Graph Domain
## Scope
M4 adds a provider-neutral relationship model to the existing History entities domain. It does not introduce a second copy of people, collectives, places, or events.

## Contracts
- HistoricalEntityReference is a typed reference to a canonical History identity.
- HistoricalRelationship is one revisioned, optionally time-bounded relationship assertion.
- HistoricalRelationshipDefinition describes endpoint constraints, directed/symmetric semantics, transitivity metadata, and an optional inverse code.
- HistoricalRelationshipVocabulary is an immutable registry that rejects duplicate codes, unknown inverse codes, non-reciprocal inverse definitions, and endpoint-kind mismatches.
- Symmetric relations are represented once and require deterministic endpoint ordering. A symmetric relation is not automatically transitive.
- Directed relations preserve subject/object order. Inverse definitions are vocabulary metadata; no inverse record is silently generated.

## Persistence and graph providers
- PostgreSQL is the authoritative relationship store. Relationship revisions are append-only; optimistic compare-and-append uses advisory transaction locking and a unique relationship/revision key.
- Tenant-private research is stored with a tenant UUID. Shared public-corpus rows use a null tenant UUID and are readable by tenants but cannot be inserted by the ordinary application role.
- Forced row-level security limits reads to shared public rows plus the current tenant. Trusted tenant context is transaction-local.
- Application authorization validates relationship access and both endpoint references.
- Migration V2 writes one versioned projection event per relationship revision in the same transaction as the revision insert.
- Migration V3 adds SKIP LOCKED batch claims, expiring fenced leases, separate lifetime/retry-window counters, and actor-attributed dead-letter replay audit.
- Every delivery transition is fenced by a random lease token. An expired worker cannot acknowledge or reschedule after another worker reclaims the event.
- The application dispatcher applies bounded exponential backoff and moves exhausted events to dead-letter state. Scheduling is host-owned.
- Projection adapters must be idempotent by event key and monotonic by aggregate revision. PostgreSQL remains authoritative; Neo4j is optional and disposable.

The host must authorize dead-letter inspection and replay; raw store methods must not be exposed as tenant-facing endpoints.

## Deferred
- Concrete Neo4j adapter, projection checkpoints, full rebuild and reconciliation.
- M5 source claims, citations, evidence, conflicting assertions, and verification.
- Graph query APIs, traversal limits, pagination, and bounded traversal protection.
- Shared-corpus ingestion workflow and editorial authorization.
- Controlled vocabulary governance and localization.