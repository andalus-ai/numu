# History M4 — Typed Historical Knowledge Graph Domain

## Scope

M4 adds the provider-neutral relationship model to the existing History entities domain. It does not introduce a second copy of people, collectives, places, or events.

## Contracts

- HistoricalEntityReference is a typed reference to a canonical History identity.
- HistoricalRelationship is one revisioned, optionally time-bounded relationship assertion.
- HistoricalRelationshipDefinition describes endpoint constraints, directed/symmetric semantics, transitivity metadata, and an optional inverse code.
- HistoricalRelationshipVocabulary is an immutable registry that rejects duplicate codes, unknown inverse codes, non-reciprocal inverse definitions, and endpoint-kind mismatches.
- Symmetric relations are represented once and require deterministic endpoint ordering. A symmetric relation is not automatically transitive.
- Directed relations preserve subject/object order. Inverse definitions are vocabulary metadata; no inverse record is silently generated.

## Compatibility with M3

M3 already has specialized contracts for person-to-collective affiliation and event participation/place links. M4 does not replace or dual-write those contracts. The initial standard vocabulary therefore excludes member-of, participated-in, and occurred-at. A future migration must define a single authoritative representation and a compatibility projection before changing those contracts.

## Persistence and graph providers

M4 includes a tenant-aware graph SPI, an authorization-aware application service, and a JDBC PostgreSQL adapter.

- PostgreSQL is the authoritative relationship store. Relationship revisions are append-only; optimistic compare-and-append uses an advisory transaction lock and a unique relationship/revision key.
- Tenant-private research is stored with a tenant UUID. Shared public-corpus rows use a null tenant UUID and are readable by tenants but cannot be inserted by the ordinary application role.
- Forced row-level security limits reads to shared public rows plus the current tenant's rows. Tenant context is transaction-local and must be supplied from authenticated server-side context.
- Application authorization is resource-level and must authorize the relationship and both endpoint references before exposing or changing data.
- Migration V2 creates a durable projection outbox. A PostgreSQL trigger emits one versioned event for every relationship revision in the same transaction; rollback removes both records.
- Event identity is unique by relationship ID and revision. Event content, tenant scope, schema version and payload are immutable; only delivery metadata is mutable.
- The outbox payload carries typed endpoints, relationship vocabulary code, tenant scope, revision and the original historical validity expression/precision. It intentionally excludes free-text descriptions and source evidence.
- The outbox is provider-neutral. Neo4j remains an optional disposable projection and is not authoritative storage.

The request-facing application service writes tenant-private research only. Shared-corpus ingestion remains a separate privileged workflow with editorial authorization; it must not be exposed through the ordinary tenant write path.

## Deferred

- Outbox claim/lease worker, bounded retry/backoff, dead-letter recovery, projection checkpoints and operational metrics.
- Idempotent Neo4j (or alternative graph provider) projection adapter, rebuild and reconciliation.
- M5 source claims, citations, evidence, conflicting assertions, and verification.
- Graph query APIs, traversal limits, pagination, and protection against unbounded traversals.
- Shared-corpus ingestion workflow and editorial authorization.
- Controlled vocabulary governance and localization.
