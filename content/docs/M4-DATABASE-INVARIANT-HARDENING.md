# M4 — Content Database Invariant Hardening

## Goal
Move critical content guarantees from application conventions into PostgreSQL constraints and triggers. The database must reject invalid writes even when a caller bypasses the application service.

## Enforced invariants
1. A content entry is created only as `DRAFT` at revision 1.
2. The current-revision pointer references an actual revision. The composite foreign key is deferred until commit so create/append can atomically update the pointer and insert the revision in one transaction.
3. Revision pointers advance by exactly one. New revision rows must match the head pointer and have a contiguous predecessor.
4. Revision rows are append-only: UPDATE and DELETE are rejected.
5. New revision rows must reference an existing `PUBLISHED` schema version in the same tenant.
6. Published schema versions cannot be edited or deleted. Their field and allowed-option rows cannot be inserted, updated, or deleted after publication.
7. Schema versions begin as drafts and may transition only once from `DRAFT` to `PUBLISHED`.
8. Content lifecycle updates must follow the same transition graph as `ContentLifecyclePolicy`; compare-and-set in the repository remains the concurrency boundary for application commands.

## Migration
- `V5__content_database_invariant_hardening.sql` adds a deferred current-revision foreign key and PostgreSQL trigger functions.
- Existing revisions and schemas are not rewritten.
- The new published-schema requirement applies to newly inserted revision rows; legacy rows remain readable and are not retroactively assigned schema identity.

## Verification
The PostgreSQL integration suite runs against PostgreSQL 16 in the Content Platform workflow. It covers:
- Published schema/field immutability.
- Revision UPDATE/DELETE rejection.
- Current revision pointer jumps and deferred missing-revision rejection.
- Invalid direct lifecycle transitions and valid editorial progression.
- Existing transactional revision append and tenant-scoped schema FK behavior.

## Operational note
Run migrations through the configured Flyway migration path before deploying application code that depends on these constraints. Do not manually edit immutable revision or published schema rows; corrections require a new revision or a new schema version.
