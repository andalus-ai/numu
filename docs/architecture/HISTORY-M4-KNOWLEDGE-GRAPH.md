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

This milestone is domain-only. It adds no SQL schema, repository adapter, event consumer, Neo4j dependency, or graph synchronization worker. PostgreSQL remains the intended authoritative store. A future Neo4j or other graph projection must be rebuildable, idempotent, revision-aware, and disposable without data loss.

## Deferred

- M5 source claims, citations, evidence, conflicting assertions, and verification.
- Repository/application ports, persistence schema, tenant and authorization enforcement.
- Durable event/outbox projection, rebuild cursors, dead-letter handling, and operational metrics.
- Graph query APIs, traversal limits, pagination, and protection against unbounded traversals.
- Controlled vocabulary governance and localization.