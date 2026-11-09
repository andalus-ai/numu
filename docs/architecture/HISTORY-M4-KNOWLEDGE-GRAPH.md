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
- Neo4j is an optional projection adapter only. It stores typed entity nodes and revisioned relationship nodes, scopes private projections by tenant, and ignores stale revisions.
- PostgreSQL remains authoritative; Neo4j is disposable and rebuildable.
- Migration V4 adds a monotonic outbox sequence and durable rebuild checkpoints. A rebuild captures a high-water mark while holding a PostgreSQL SHARE table lock, so transactions that append later events cannot fall below the captured boundary.
- Rebuild pages are keyset-paginated and tenant/public-scope isolated. A checkpoint advances only after every event in its page has been projected; replaying a page after interruption is safe because the Neo4j adapter is revision-idempotent.
- HistoricalProjectionAuditStore exposes bounded latest-canonical-event pages and exact latest-event lookup. HistoricalProjectionInspectionPort exposes bounded graph inspection without making Neo4j a dependency of the application layer.
- Reconciliation compares the latest PostgreSQL event with the scoped Neo4j revision, event key, relationship kind, endpoint identities, and SHA-256 payload fingerprint. It also reports graph relationships with no canonical PostgreSQL relationship.
- Missing, stale, and same-revision divergent graph records can be repaired by replaying the exact immutable canonical event. A graph revision newer than PostgreSQL is reported as divergent and is never overwritten automatically.
- Reconciliation is bounded by keyset pages and read-only unless the caller explicitly enables repair. The host must authorize maintenance operations and provide scheduler/runtime wiring.

- The staged-generation lifecycle registry is stored in Neo4j with uniqueness constraints for `(scopeKey, generationId)` and one active pointer per scope. Lifecycle is BUILDING → READY → ACTIVE → RETAINED; activation is compare-and-set against the expected active generation, and rollback reactivates a retained generation without deleting it.
- Maintenance lifecycle commands require the existing maintenance authorization port. The caller may mark a generation READY only after replay and reconciliation have completed successfully.
- This lifecycle registry is the first staged-cutover slice only. Existing relationship projection and inspection paths are not yet generation-aware; generation-targeted replay, active-generation query routing, and high-water/tail catch-up must be integrated before enabling production cutovers.

## Operational limits
- Rebuild replays all retained outbox revisions through the captured high-water mark. It does not delete Neo4j nodes or relationships that have no corresponding canonical outbox event; reconciliation reports such records as orphaned but does not delete them.
- Outbox retention must preserve all events required for a full rebuild. Do not purge projection events unless an independently verified canonical snapshot/rebuild source is introduced.
- Concurrent rebuild workers may read the same page. Provider idempotency makes duplicate projection safe; checkpoint compare-and-set permits only one worker to advance the cursor.
- Reconciliation does not perform destructive orphan cleanup or active-generation swaps. Those operations require a separately staged graph generation, complete validation, and an atomic activation protocol.

## Deferred
- Staged generation rebuild and atomic active-generation swap for destructive orphan cleanup.
- Graph query APIs, traversal limits, pagination, and bounded traversal protection.
- M5 source claims, citations, evidence, conflicting assertions, and verification.
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
