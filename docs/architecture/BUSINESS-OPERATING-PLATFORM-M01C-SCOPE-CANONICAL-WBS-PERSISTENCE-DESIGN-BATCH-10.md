# Business Operating Platform M01-C — Scope canonical WBS persistence design (Batch 10)

- **Repository:** `andalus-ai/numu`
- **Branch:** `docs/business-platform-m01c-wbs-persistence-design-batch10`
- **Baseline:** `main` at `c98522068f83d0e1aaee713f3bdc9e440e23d4a6`
- **Status:** Design proposal; no production implementation or data migration
- **Related:** ADR-003, DOMAIN-CAPABILITY-MATRIX.md, M01-C batches 8–9

## 1. Decision sought

Make **Scope** the canonical owner of neutral breakdown structure identity, hierarchy invariants, and immutable revisions. Planning remains the owner of schedule activities, schedule versions/baselines, and the association between a schedule activity and a specific WBS node in a specific WBS revision.

This is a target boundary, not a claim that Scope currently has durable PostgreSQL persistence. Planning is presently the only inspected capability with concrete PostgreSQL WBS tables and repositories. Do not cut over or remove Planning's WBS storage until Scope persistence, revision contracts, and a compatibility bridge are implemented and verified.

## 2. Evidence from the current source

### Scope
- `BreakdownStructure` owns type, code, name, owner reference, and DRAFT/ACTIVE/RETIRED lifecycle. Rename is draft-only; activation and retirement are explicit.
- `BreakdownNode` owns structure ID, parent ID, code, name, kind, and position. Its source comment explicitly delegates complete-tree invariants to application/repository boundaries.
- `SaveBreakdownNode` and `DeleteBreakdownNode` validate/mutate through repository ports under `BreakdownStructureWriteLock`.
- The current Scope repository SPI methods do not carry tenant ID. The lock port takes only `BreakdownStructureId`.
- The PostgreSQL adapter currently provides `PgAdvisoryBreakdownStructureWriteLock`; no Scope PostgreSQL structure/node repository or Scope WBS migration was found in the inspected source tree. The lock adapter is not durable WBS persistence.
- The lock key is currently `numou:scope:structure:<structureId>`, omitting tenant identity.

### Planning
- `WorkBreakdownStructure` and `WorkBreakdownNode` are neutral immutable snapshot types.
- `WorkBreakdownRepository` explicitly accepts tenant ID and persists a new immutable snapshot; its current lookup is by tenant and structure ID only.
- Migration V6 creates tenant-composite `work_breakdown_structure` and `work_breakdown_node` tables, sibling code/position uniqueness, parent FK, and mutation-rejection triggers.
- Migration V7 creates tenant-scoped schedule activity links, currently keyed by structure ID and node ID, without a WBS revision ID.
- `JdbcWorkBreakdownRepository` persists an immutable structure snapshot in one local JDBC transaction.
- `LinkScheduleActivityToWorkBreakdownNode` validates the node against a tenant-scoped Planning snapshot and creates a link. It does not currently pin the link to a revision identifier.

### Architecture contract
- The capability matrix assigns neutral hierarchical WBS identity, parent/child integrity, ordering, codes/labels, structure type, and revision references to Scope.
- Scheduling remains a separate capability; a WBS hierarchy is not a schedule.
- ADR-003 requires explicit cross-capability contracts, tenant isolation, and no hidden coupling through another capability's tables or private implementation.

## 3. Ownership boundary

| Concern | Owner | Contract |
|---|---|---|
| Structure identity, type, code/name, lifecycle | Scope | Scope public domain/application contract |
| Node identity, parent-child hierarchy, kind, sibling ordering, structural validation | Scope | Scope revision snapshot |
| Revision creation, immutable content, revision metadata/checksum | Scope | Scope revision contract |
| Current/draft revision selection and lifecycle transitions | Scope | Scope application contract |
| Schedule activities, dependencies, calendars, plan versions and baselines | Planning | Planning schedule contract |
| Activity-to-WBS association | Planning | Stores Scope structure/revision/node references |
| Industry-specific WBS meaning | Extension capability | Extension-owned metadata/contracts; no construction-only fields in Scope |
| Tenant identity and authorization | Host/runtime security boundary; tenant carried explicitly through application/persistence ports | Trusted tenant context, not caller-controlled payload alone |

Planning must not import Scope persistence entities, SQL tables, JDBC repositories, or adapter implementation classes. Planning may consume a stable Scope application contract (in-process) or a versioned API/event contract (distributed). A local adapter may translate between the two without sharing storage.

## 4. Canonical revision model

A structure ID identifies the long-lived WBS identity. A revision ID identifies one immutable full-tree snapshot. Node IDs remain stable across revisions when the logical node is retained, but a node reference is only meaningful together with its structure and revision.

Required identifiers:
- `tenant_id`: tenant scope, explicit at every read/write boundary.
- `structure_id`: stable WBS identity within tenant.
- `revision_id`: immutable revision identity (UUID recommended).
- `revision_number`: monotonically increasing per structure, starting at 1.
- `node_id`: stable node identity; uniqueness enforced within structure (and revision membership checked).
- `parent_id`: nullable only for the single root node in a revision.

A revision must be a complete, self-contained snapshot. Editing a published/current revision creates a new revision; it never mutates prior revision rows. A mutable head pointer may identify the current revision, but historical revision and node rows remain append-only. The initial draft editing model in Scope must be reconciled with this snapshot model before implementation: either draft edits live in a separate mutable working copy which is atomically published as a revision, or every accepted edit creates a revision. Do not silently reinterpret existing DRAFT/ACTIVE/RETIRED semantics.

### Revision invariants
1. Revision number is unique and increasing within `(tenant_id, structure_id)`.
2. Revision content is immutable after publication.
3. Every revision has exactly one root; all non-root parents exist in the same revision.
4. No node is its own parent; no cycles; no cross-structure or cross-revision parent references.
5. Sibling code uniqueness is case-insensitive; sibling position is unique and non-negative.
6. Deterministic read order is by parent, position, normalized code, then node ID as a final stable tie-breaker.
7. A Planning link references a node that exists in the exact Scope revision.
8. A retired structure cannot accept new revisions; historical revisions remain readable subject to authorization/retention policy.
9. A tenant mismatch must behave as not found at the public boundary; do not reveal whether another tenant owns an ID.

## 5. Proposed Scope PostgreSQL model

The following is a logical migration-ready model, not an executable migration. Names are proposed and must be checked against repository naming conventions and deployed schemas before adoption.

### `scope_breakdown_structure` — stable identity and current head

- `tenant_id UUID NOT NULL`
- `structure_id UUID NOT NULL`
- `type VARCHAR(80) NOT NULL`
- `code VARCHAR(120) NOT NULL`
- `name VARCHAR(500) NOT NULL`
- `owner_reference VARCHAR(240)`
- `status VARCHAR(24) NOT NULL`
- `current_revision_id UUID NULL`
- `created_at TIMESTAMPTZ NOT NULL`
- `updated_at TIMESTAMPTZ NOT NULL`
- PK `(tenant_id, structure_id)`
- unique tenant-scoped case-insensitive structure code
- status check constrained to the existing Scope lifecycle values.

The current revision pointer is the only mutable pointer on the identity row. It must be updated with compare-and-set/expected-current-revision semantics while holding the tenant-qualified structure lock.

### `scope_breakdown_revision` — immutable revision header

- `tenant_id UUID NOT NULL`
- `structure_id UUID NOT NULL`
- `revision_id UUID NOT NULL`
- `revision_number BIGINT NOT NULL CHECK (revision_number > 0)`
- `content_hash VARCHAR(128) NOT NULL` (algorithm/version explicitly identified; e.g. `sha256:<hex>`)
- `created_at TIMESTAMPTZ NOT NULL`
- `created_by UUID` (nullable only if system actor is explicitly represented elsewhere)
- `change_reason VARCHAR(1000)`
- PK `(tenant_id, structure_id, revision_id)`
- unique `(tenant_id, structure_id, revision_number)`
- FK `(tenant_id, structure_id)` to structure.

### `scope_breakdown_revision_node` — complete immutable tree

- `tenant_id UUID NOT NULL`
- `structure_id UUID NOT NULL`
- `revision_id UUID NOT NULL`
- `node_id UUID NOT NULL`
- `parent_id UUID NULL`
- `code VARCHAR(120) NOT NULL`
- `name VARCHAR(500) NOT NULL`
- `kind VARCHAR(80) NOT NULL`
- `position INTEGER NOT NULL CHECK (position >= 0)`
- PK `(tenant_id, structure_id, revision_id, node_id)`
- FK `(tenant_id, structure_id, revision_id)` to revision
- deferred self-FK `(tenant_id, structure_id, revision_id, parent_id)` to the same node table
- unique sibling code index on tenant, structure, revision, coalesced parent, lower(code)
- unique sibling position index on tenant, structure, revision, coalesced parent, position
- check `parent_id IS NULL OR parent_id <> node_id`
- one-root invariant enforced by a partial unique index on `(tenant_id, structure_id, revision_id)` where `parent_id IS NULL`, plus a publish-time validation requiring at least one node/root.

The current-revision FK from structure to revision should be added as a deferred composite FK after both tables exist. It must include tenant and structure in the key, preventing a head pointer from referencing another tenant/structure.

### Immutability enforcement
- Revision and revision-node rows reject UPDATE/DELETE through database triggers or equivalent privileges.
- Revision creation inserts the header and all nodes in one transaction, validates the complete tree, then advances the structure head pointer with an expected-current-revision check.
- Retention/deletion is a separate governed process; normal application credentials cannot mutate historical revisions.
- Database triggers complement, but do not replace, application-level validation and authorization.

## 6. Proposed public contract shape

The following is a design sketch; it does not assert these types or methods exist today.

Scope application read contract:
- `getCurrentRevision(tenantId, structureId) -> Optional<BreakdownRevisionSnapshot>`
- `getRevision(tenantId, structureId, revisionId) -> Optional<BreakdownRevisionSnapshot>`
- `createRevision(tenantId, structureId, expectedCurrentRevisionId, candidateSnapshot, actorContext, idempotencyKey) -> BreakdownRevisionSnapshot`

The snapshot contains structure ID, revision ID, revision number, metadata needed by consumers, content hash, and immutable nodes. The contract must return a stable result for retries with the same tenant and idempotency key; a conflicting reuse of that key with different content is rejected.

Planning application boundary should consume only the snapshot contract (or a versioned remote equivalent). Planning persists its own reference:
- `tenant_id`
- `structure_id`
- `revision_id`
- `node_id`
- `plan_id`
- `activity_id`

Planning's link validation checks tenant, structure, revision and node through the Scope contract before persisting. It may cache immutable snapshots, but cache keys must include tenant, structure and revision, and authorization is still checked at the application boundary.

Do not add Scope as a direct Planning Domain dependency merely to share persistence models. If a cross-capability dependency is needed, keep it at Planning Application -> Scope public application contract, or behind a Planning-owned port and adapter. Confirm the existing reactor and deployment composition before selecting the exact Maven edge.

## 7. Tenant-scoped concurrency and transaction design

The current advisory-lock key is `numou:scope:structure:<structureId>`. Replace its contract with tenant + structure identity before multi-tenant persistence is introduced.

Recommended lock key namespace:
`numou:scope:wbs:v1:<tenantId>:<structureId>`

Use PostgreSQL `hashtextextended(key, fixed_seed)` consistently for lock and unlock. Hash collisions can cause unnecessary serialization but must never bypass tenant predicates or database constraints. A future alternative is a two-key advisory lock using stable tenant and structure hash components; choose one convention and test compatibility.

Required lock behavior:
1. Acquire lock before reading the expected current revision and validating the candidate.
2. Keep the lock through revision header/node inserts and current-head update.
3. Release on success, validation failure, SQL failure, timeout, and cancellation; connection close must release any remaining session lock.
4. All writers across all instances must use the same tenant-qualified convention.
5. Every SQL read/write still includes `tenant_id`; advisory locks are not authorization or isolation.
6. Lock timeout and pool exhaustion are observable and bounded.

The current Scope implementation retains a dedicated PostgreSQL connection while the asynchronous operation runs. This can coordinate with writes executed on another connection as long as all writers acquire the same lock and the lock remains held through commit. The actual Scope repository/transaction implementation must ensure the lock is not released before the durable write commits. Prefer an explicit unit-of-work/transaction boundary or a single adapter operation that owns lock + transaction; do not assume a CompletionStage completing implies a transaction committed without inspecting the eventual adapter.

## 8. Planning compatibility and migration sequence

Do not alter Planning V6/V7 in place. Introduce additive compatibility migrations only after Scope schema and contract are implemented.

### WBS-0 — contract and inventory (this batch)
- Confirm ownership, tenant/revision semantics, and current persistence evidence.
- Inventory every Planning WBS consumer, migration, API, event, test fixture, and external integration.
- Identify existing deployed Planning rows and backup/restore obligations.

### WBS-1 — Scope persistence
- Implement tenant-explicit Scope repository ports and revision snapshot model.
- Implement PostgreSQL migrations/repositories/integration tests.
- Add concurrent writer, tenant isolation, immutable revision, cycle, root, sibling uniqueness, deterministic order, idempotency and rollback tests.
- Ensure current Scope DRAFT/ACTIVE/RETIRED lifecycle is explicitly reconciled with draft working copies and immutable published revisions.

### WBS-2 — Planning public-contract integration
- Add a Planning-owned WBS reference port and Scope adapter/client.
- Change Planning association model to include `revision_id`.
- Add revision-pinned link persistence and exact-revision validation.
- Keep existing V6/V7 data operational while the bridge is active.

### WBS-3 — dual-write/bridge and backfill
- For each existing Planning structure, create a corresponding Scope structure and immutable revision.
- Preserve tenant, structure IDs, node IDs, parent relationships, code/name/order and type mapping where lossless.
- Record explicit source-to-target mapping and checksums; reject or quarantine invalid/colliding legacy rows rather than silently repair them.
- Add a bridge mapping with tenant-qualified source IDs and target revision ID.
- Dual-write only with a clear consistency/compensation strategy; otherwise use a controlled write freeze and resumable migration.
- Verify row counts, per-structure content hashes, parent/root integrity, schedule link counts, and tenant boundaries.

### WBS-4 — cutover and retirement
- Switch Planning reads/writes to Scope contracts behind a reversible feature flag.
- Compare old and new results during a defined observation window.
- Keep Planning legacy schema read-only until consumers, backups, rollback and operational dashboards are proven.
- Retire old WBS tables only in a separately approved destructive migration after explicit authorization.

## 9. Acceptance criteria before implementation approval

- [ ] Scope owns durable tenant-scoped WBS structure identity and immutable revision history.
- [ ] Scope public application contract exposes current and exact historical revision reads.
- [ ] Planning can link an activity to an exact Scope revision/node without importing Scope persistence.
- [ ] All persistence operations are tenant-qualified; authorization is enforced before data disclosure.
- [ ] A concurrent mutation cannot publish against a stale head revision.
- [ ] Replayed idempotent revision commands do not create duplicate revisions.
- [ ] Revision content hash is deterministic and versioned.
- [ ] Root, parent, cycle, sibling code and sibling position invariants are enforced.
- [ ] Planning baselines remain reproducible after later WBS revisions.
- [ ] Migration is additive, resumable, observable, checksum-verified and reversible until cutover.
- [ ] Narrow Maven reactor tests, PostgreSQL integration tests, and relevant CI evidence are recorded.
- [ ] No legacy table is dropped without separate explicit approval.

## 10. Open decisions (must be settled before code)

1. **Draft semantics:** Are edits to DRAFT structures mutable working-copy edits until activation, or should every saved tree state become an immutable revision? Recommendation: mutable draft workspace + immutable activation/published revisions, with explicit revision creation for published changes.
2. **Node identity:** Should a retained logical node keep its UUID across revisions? Recommendation: yes; the revision membership makes each snapshot immutable while stable IDs support cross-revision comparison. A deleted node ID must not be reused for a different logical node.
3. **Revision publication:** Is activation the publication boundary, or can active structures create new published revisions? Recommendation: allow new immutable revisions on active structures through explicit change/publish use cases; retired structures reject new revisions.
4. **Tenant model:** Confirm tenant ID is available from the trusted application security context for all Scope commands and reads. The persistence ports should still receive tenant explicitly to make isolation testable and unambiguous.
5. **Planning link compatibility:** Existing V7 links have no revision ID. Confirm whether existing Planning snapshots are immutable forever; backfill each link to the exact legacy structure snapshot and preserve that revision mapping.

## 11. Verification and scope of this batch

- Inspected the current `main` tree at `c98522068f83d0e1aaee713f3bdc9e440e23d4a6`.
- Read `AGENTS.md`, ADR-003, the Domain Capability Matrix, Scope domain/application/SPI/lock adapter, Planning WBS domain/application ports, Planning JDBC repositories, and Planning migrations V6/V7.
- No Java, SQL migration, API, Maven dependency, or production behavior was changed.
- No Maven build, unit test, PostgreSQL integration test, or CI run was performed.
- No PR was opened.
