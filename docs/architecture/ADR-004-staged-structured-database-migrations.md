# ADR-004: Staged, generation-based structured database schema migrations

- **Status:** Proposed
- **Date:** 2026-10-25
- **Owners:** Numou Workspace capability maintainers
- **Scope:** `workspace` structured database capability

## Context

The current structured database schema migration path can validate and atomically migrate a bounded number of active records in one database transaction. The JDBC adapter deliberately caps this synchronous path at 5,000 records. Raising that limit or updating live records in batches is not safe by itself: readers could observe a mixture of old- and new-schema values while the table schema advertises only one schema version. A process crash between batches would leave the same inconsistency.

Schema migration also has concurrency concerns. Records may be edited while a migration is running, the schema may be changed by another actor, and a worker may retry after losing its acknowledgement. Any larger-table solution must preserve tenant isolation, optimistic concurrency, auditability, and the current no-partial-apply guarantee.

## Decision

Use **generation-based staging and atomic cutover** for migrations that exceed the synchronous limit. Do not implement a worker that incrementally rewrites the live `record_data` column under the currently active schema.

### 1. Active generation is the read boundary

A table has a monotonically increasing active data generation. Record reads, queries, relation validation, and writes must resolve records in that active generation only. A migration writes a candidate generation while the current generation remains authoritative.

The generation identifier is internal storage metadata, not a new industry-specific business concept. Workspace remains neutral and owns only generic structured-record semantics.

### 2. Job and staged data lifecycle

A durable migration job records tenant, database, table, source schema version, target schema version, source generation, target generation, immutable migration-plan reference/checksum, creator, timestamps, status, progress counters, and a bounded failure classification.

Staged record rows are keyed by tenant, table, target generation, and record ID. Each row records its source revision and transformed candidate values. Candidate data is validated against the proposed schema and relation constraints before it can be published. Job state and staged rows must be tenant-scoped by constraints and every query.

### 3. Worker behavior

- Job creation validates authorization, source schema version, migration plan, and active-generation snapshot.
- A worker claims work with a lease or compare-and-swap token; two workers must not both advance the same checkpoint.
- Each batch is bounded and idempotent. Replaying a batch produces the same staged result or detects a conflicting source revision.
- Progress is committed in the same transaction as staged rows and checkpoint advancement.
- A record changed after the source snapshot causes a visible conflict; it must not be silently overwritten.
- Retries resume from durable checkpoints. Terminal failure leaves the old generation active and available.
- The target generation is never exposed as partially active.

### 4. Atomic cutover

Cutover is a short transaction that verifies the source schema version and active generation are unchanged, verifies all expected active records have validated staged counterparts, then switches the active generation and schema version together and appends the audit event. If any precondition fails, the transaction rolls back and the previous generation remains active.

The old generation is retained until a separate, guarded cleanup step confirms it is not referenced by an active migration or reader policy. Cleanup is not part of cutover and is not automatic on a failed job.

### 5. Compatibility with synchronous migration

The existing synchronous migration remains appropriate for small tables because its single transaction gives all-or-nothing semantics. The implementation must not route a table through staged migration solely on a guessed threshold unless the API clearly reports which execution mode was selected. Both paths use the same schema/value/relation validation rules and audit vocabulary where practical.

## Consequences

### Positive

- Readers never see mixed schema generations.
- Worker crashes and duplicate delivery are recoverable.
- Large migrations avoid a single transaction that rewrites every record.
- Conflicts, progress, and terminal failures are observable and auditable.
- Tenant and optimistic-concurrency guarantees remain explicit.

### Costs and follow-up work

- Reads and writes must consistently select the active generation.
- Staged storage consumes additional space until cleanup.
- The implementation needs explicit job and stage repositories, worker coordination, retention policy, and integration tests.
- Existing tables require a migration that initializes their active generation without rewriting user data.

## Required acceptance tests

1. A partially staged target generation is invisible to all normal record reads and queries.
2. Restarting a worker resumes from the committed checkpoint without duplicate staged rows or progress inflation.
3. Two workers cannot claim or advance the same job concurrently.
4. A source record revision conflict prevents cutover and leaves the old generation active.
5. A schema-version or active-generation conflict prevents cutover.
6. Cutover changes schema version and active generation atomically and writes exactly one audit entry.
7. A failed or cancelled job never changes the active generation.
8. Cross-tenant job, stage, status, and cleanup operations are denied.
9. Cleanup refuses to delete the active generation or data referenced by a non-terminal job.
10. Existing synchronous migration and saved-view impact behavior remain unchanged.
