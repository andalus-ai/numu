# Milestone 1 — Neutral scope structural integrity

**Milestone:** Neutral WBS/scope mutations are safe to use as a reusable platform capability.  
**Status:** Implementation in progress on `feat/neutral-scope-structural-integrity`.

## Implemented in this milestone

- Keep breakdown tree validation domain-neutral and independent from construction classes.
- Validate that one tree-validation pass contains only one breakdown structure.
- Make cycle detection linear in node/parent-edge count rather than repeatedly walking every ancestor chain.
- Prevent mutation of retired breakdown structures.
- Reject reuse of a node identity that already belongs to another structure.
- Add a leaf-deletion use case that shares the structure-scoped write lock with node saves.
- Bind the construction adapter's scope-lock port to the PostgreSQL advisory-lock implementation through CDI producer wiring.
- Add database-backed integration coverage for same-structure serialization across independent lock adapter instances and independent progress for different structures.
- Correct construction WBS tests to supply the now-required lock dependency.
- Cover mixed structures, deep hierarchies (10,000 nodes), retired structures, cross-structure identity, leaf deletion, parent deletion rejection and wrong-structure deletion.

## Concurrency contract

Every operation that can alter parent/child relationships must use the same `BreakdownStructureWriteLock` key for the entire read/validate/write sequence. This includes save/update, delete, move/reorder and bulk-import operations. In a multi-instance deployment, the lock must be backed by the PostgreSQL advisory-lock adapter. An in-process lock is suitable only for tests or a single application process.

A lock alone is not a database constraint. Persistence adapters must still enforce unique node identity and foreign-key/structure membership where practical, and every writer must follow the same lock protocol.

The PostgreSQL integration tests are opt-in under the construction adapter's `-Ppg-integration` profile because they require a reachable test database. The unit and domain tests do not require PostgreSQL.

## Acceptance gates

- A valid tree can be created and leaf nodes deleted.
- Missing parents, cross-structure relationships, duplicate sibling codes, cycles, mixed-structure validation input and duplicate identities are rejected.
- Nodes cannot be added to or removed from retired structures.
- Parent nodes with children cannot be deleted.
- A node ID cannot be reassigned to a different structure.
- Concurrent writes to the same structure serialize across validation and persistence.
- Writes to unrelated structures do not block one another.
- Neutral scope modules remain free of construction-specific dependencies.
- Existing construction WBS/BOQ revision, authorization and tenant-isolation behavior remains unchanged.

## Next milestone

Add a neutral schedule-activity model and explicit schedule versions/baselines, with tests proving that forecast edits cannot mutate approved baseline history. Then connect schedule activities to neutral scope/work-item IDs through explicit references. Construction BOQ revision/valuation remains construction-owned.
