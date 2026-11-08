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
- Delivery uses at-least-once semantics with lease-token fencing, bounded exponential retry, and audited dead-letter replay.
- Neo4j is an optional projection adapter only. It stores typed entity nodes and revisioned relationship nodes, scopes private projections by tenant, and ignores duplicate or stale revisions.
- PostgreSQL remains authoritative; Neo4j is disposable and rebuildable.
- Migration V4 adds a monotonic outbox sequence and durable rebuild checkpoints. A rebuild captures a high-water mark while holding a PostgreSQL SHARE table lock, so transactions that append later events cannot fall below the captured boundary.
- Rebuild pages are keyset-paginated and tenant/public-scope isolated. A checkpoint advances only after every event in its page has been projected; replaying a page after interruption is safe because the Neo4j adapter is revision-idempotent.
- Reconciliation is a bounded, read-only audit over latest canonical outbox revisions and Neo4j projection metadata. It detects missing, stale, ahead, divergent, and orphaned relationship nodes without changing either store.
- Canonical and graph scans use keyset pagination and an explicit public/tenant scope. Public and tenant-private projections must be audited independently.
- Rebuild and reconciliation are maintenance primitives; host wiring must authenticate the operator and derive tenant scope from trusted context before invoking them.

## Operational limits
- Rebuild replays all retained outbox revisions through the captured high-water mark. It does not delete Neo4j nodes or relationships that have no corresponding canonical outbox event; destructive stale-node cleanup requires a separately staged generation/swap protocol.
- Reconciliation compares latest outbox revision payload metadata with projected relationship metadata. It does not yet verify endpoint-node properties, dangling edges, or all historical content.
- Outbox retention must preserve all events required for a full rebuild. Do not purge projection events unless an independently verified canonical snapshot/rebuild source is introduced.
- Reconciliation is diagnostic only in this milestone. Automated repair must use canonical replay, carry actor/audit context, and avoid mutating PostgreSQL history.

## Deferred
- Automated repair orchestration and repair audit records.
- Staged generation rebuild and atomic active-generation swap for destructive orphan cleanup.
- Metrics for projection lag, audit drift, and repair outcomes.
- M5 source claims, citations, evidence, conflicting assertions, and verification.
- Graph query APIs, traversal limits, pagination, and bounded traversal protection.
- Shared-corpus ingestion workflow and editorial authorization.
- Controlled vocabulary governance and localization.


## Projection reconciliation (M4 follow-up)
- The reconciliation application service compares the latest retained PostgreSQL outbox event for each relationship with the scoped Neo4j relationship projection.
- Comparison checks aggregate revision, immutable event key, and SHA-256 of the canonical JSON payload.
- Reports distinguish missing, stale, same-revision divergent, newer-than-source divergent, and orphaned graph records.
- Repair is opt-in and replays the exact canonical outbox event. It never synthesizes relationship content.
- A Neo4j revision newer than the canonical source is reported but never overwritten automatically.
- Both canonical and graph scans use bounded keyset pages (maximum 500) and enforce strict cursor ordering and tenant-scope consistency.
- Orphaned nodes are reported only. Destructive cleanup requires a staged generation rebuild and explicit activation protocol.
- The host is responsible for authenticating and authorizing maintenance access before invoking reconciliation. The read ports do not replace application-level maintenance authorization.
- PostgreSQL outbox retention must preserve the latest event for every relationship; purging canonical history would make the comparison incomplete.
