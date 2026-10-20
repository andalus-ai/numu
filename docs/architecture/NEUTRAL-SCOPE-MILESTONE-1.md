# Milestone 1 — Neutral scope structural integrity

**Milestone:** Neutral WBS/scope mutations are safe to use as a reusable platform capability.  
**Status:** Implementation in progress on `feat/neutral-scope-structural-integrity`.

## Delivered in this milestone

- Keep breakdown tree validation domain-neutral and independent from construction classes.
- Validate that one tree-validation pass contains only one breakdown structure.
- Make cycle detection linear in node/parent-edge count rather than repeatedly walking every ancestor chain.
- Reject mutation of retired breakdown structures.
- Reject reuse of a node identity that already belongs to another structure.
- Add a leaf-deletion use case that shares the structure-scoped write lock with node saves.
- Reject deletion through the wrong structure and deletion of a node that still has children.
- Cover these behaviors with application/domain tests, including a 10,000-node imported hierarchy.

## Concurrency contract

Every operation that can alter parent/child relationships must use the same `BreakdownStructureWriteLock` key for the entire read/validate/write sequence. This includes save/update, delete, and any future move/reorder/bulk-import operations. In a multi-instance deployment, the lock must be backed by a distributed implementation such as the PostgreSQL advisory-lock adapter. An in-process lock is suitable only for tests or a single application process.

A lock alone is not a database constraint. The persistence adapter must still enforce unique node identity and foreign-key/structure membership where practical, and every writer must follow the same lock protocol.

## Acceptance gates

- A valid tree can be created and leaf nodes deleted.
- Missing parents, cross-structure relationships, duplicate sibling codes, cycles and duplicate identities are rejected.
- Nodes cannot be added to or removed from retired structures.
- Parent nodes with children cannot be deleted.
- A node ID cannot be reassigned to a different structure.
- Concurrent writes to the same structure serialize across validation and persistence.
- Neutral scope modules remain free of construction-specific dependencies.
- Existing construction WBS/BOQ revision, authorization and tenant-isolation behavior remains unchanged.

## Next milestone

Wire the PostgreSQL advisory lock into the actual construction WBS composition path, add database-backed concurrent-write integration coverage, then implement schedule activities and immutable baselines as separate neutral planning concepts. Construction BOQ revision/valuation remains construction-owned.
