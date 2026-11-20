# M01-C Batch 11 — Scope tenant-safe WBS revision foundation

- **Branch:** `feat/scope-tenant-wbs-revision-foundation-batch11`
- **Baseline:** `main` at `75912b814526e449e01864a9807853ae62aa7e6e`
- **Status:** Domain model and additive SPI contracts authored; persistence adapter pending
- **Related:** Batch 10 canonical WBS persistence design

## Implemented

1. Added `BreakdownRevisionId`, a UUID-backed identity for one immutable revision.
2. Added `BreakdownRevision`, a framework-independent, tenant-scoped immutable full-tree snapshot using the existing Foundation `TenantId`.
3. Added `BreakdownRevisionRepository` with explicit tenant + structure + revision reads and atomic append/expected-current-revision semantics.
4. Added `TenantBreakdownStructureWriteLock` with tenant + structure identity.
5. Added domain tests for canonical deterministic ordering, empty snapshots, foreign structure nodes, multiple roots, duplicate sibling positions, revision number and hash format.

## Revision invariants now enforced in the domain constructor

- Tenant, structure, revision identity and creation time are mandatory.
- Revision number is positive.
- Content hash must be formatted as `sha256:<64 hex characters>`.
- Snapshot is non-empty and all nodes belong to the same structure.
- Existing tree validation rejects duplicate node IDs, mixed structures, missing parents, duplicate sibling codes and cycles.
- Exactly one root is required.
- Sibling positions must be unique.
- Nodes are copied, sorted deterministically, and exposed as an immutable list.

## Compatibility and boundary choices

- Existing `BreakdownStructureRepository`, `BreakdownNodeRepository`, and `BreakdownStructureWriteLock` were not modified. Existing application use cases and adapters therefore do not need an immediate signature migration.
- The new tenant-explicit ports are additive and are not yet wired into existing Scope use cases.
- The revision model deliberately does not change current DRAFT/ACTIVE/RETIRED lifecycle semantics. Draft editing versus published revision creation remains an explicit application-level decision.
- `contentHash` format is validated, but the domain does not compute it. A canonical, versioned serialization/hash service must be implemented and tested before persistence.
- No PostgreSQL repository, migration, transaction manager, or tenant-qualified lock adapter is implemented in this batch.
- The current non-tenant lock adapter must not be treated as satisfying the new tenant-qualified contract.

## Follow-up required

1. Define canonical serialization and SHA-256 calculation over stable metadata and ordered nodes.
2. Add a Scope application command for first revision creation and compare-and-set append, with trusted tenant/actor/idempotency context.
3. Implement PostgreSQL revision persistence with tenant-composite keys, immutable row enforcement and atomic head-pointer update.
4. Implement the tenant-qualified PostgreSQL advisory lock and verify lock lifetime covers commit.
5. Add PostgreSQL integration tests for tenant isolation, stale revision conflict, concurrent append, rollback, idempotent replay and immutable history.
6. Reconcile existing DRAFT/ACTIVE/RETIRED lifecycle with working-copy and published revision semantics before switching existing node mutation use cases.

## Verification

- Read `AGENTS.md`, ADR-003, Domain Capability Matrix, Scope domain/SPI/application source and existing Scope tests.
- Source files were committed to the feature branch.
- Tests were authored but **not executed**; no Maven reactor build or CI run was performed.
- No production persistence behavior or existing API was changed.
- No PR was opened.
