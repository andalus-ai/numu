# Workspace structured database schema migration roadmap

This roadmap follows [ADR-004: Staged, generation-based structured database schema migrations](ADR-004-staged-structured-database-migrations.md). It is an implementation plan, not a claim that the listed behavior already exists.

## Current baseline

- Small schema changes can be previewed and applied with schema-version compare-and-swap.
- Record migrations are atomic with schema/property updates and audit entries.
- The JDBC adapter bounds the synchronous path at 5,000 active records.
- Large-table resumability is **not implemented**. Do not increase the synchronous cap or rewrite live records in separate batches until the generation-based read/write boundary exists.

## Milestone M16 — Durable migration metadata and generation model

- Add an active data-generation field to structured tables, initialized for existing tables.
- Introduce durable migration job and staged-record domain contracts with explicit status transitions and immutable source/target metadata.
- Add tenant-scoped persistence constraints and migration repository ports.
- Keep normal reads on the active generation; initially all existing records remain in generation 1.
- Acceptance: existing database data remains readable after migration; malformed transitions and cross-tenant references are rejected; migration metadata has no effect on current reads.

## Milestone M17 — Idempotent batch staging

- Add bounded worker batches with claim leases/CAS, deterministic checkpoints, and per-record source revisions.
- Validate candidate values using the same rules as synchronous migrations.
- Commit staged rows and checkpoint updates atomically.
- Acceptance: duplicate batch execution is idempotent; worker restart resumes; competing workers cannot double-advance progress; record conflicts are visible.

## Milestone M18 — Atomic generation cutover

- Verify source schema version and active generation, complete record coverage, and relation integrity.
- Atomically activate target generation and target schema version and write one audit entry.
- Keep the previous generation available for rollback/inspection according to retention policy.
- Acceptance: no normal read observes a partial target generation; failed cutover leaves the source active; exactly one cutover audit is emitted.

## Milestone M19 — Recovery, cancellation, and safe cleanup

- Add explicit retryable and terminal failure classifications, cancellation semantics, operational status/progress queries, and guarded cleanup.
- Do not delete active or referenced generations.
- Add tenant isolation, failure injection, and concurrent record-write integration tests.
- Acceptance: failures are diagnosable, retries are bounded and safe, cleanup is guarded, and the previous generation remains available until explicit safe retirement.

## Implementation constraints

- Follow repository-root `AGENTS.md` and the capability ownership matrix.
- Keep the Workspace domain free of JDBC, framework, HTTP, and worker-runtime dependencies.
- Do not persist sensitive record values in logs or error summaries.
- Do not expose staged values through normal record APIs.
- Verify the `workspace` Maven reactor and PostgreSQL Testcontainers suite before claiming completion.
