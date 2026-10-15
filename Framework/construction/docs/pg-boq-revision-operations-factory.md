# PostgreSQL BOQ revision operations factory

## What this slice implements

`DefaultPgBoqRevisionOperationsFactory` composes the revision counter, immutable snapshot repository, live BOQ adapter, and transactional outbox on the caller's `SqlConnection`. It implements capture, submit-for-review, and approve operations with a transaction-local workflow:

1. Lock/load the live BOQ row.
2. Read existing snapshots and allocate a revision under the counter-row lock.
3. Copy current item values into a snapshot using the explicit calculation policy.
4. Insert the snapshot and compare-and-set the live BOQ revision/status.
5. Insert an outbox event with tenant ID on the same connection.

Submission and approval use expected snapshot status and expected live BOQ status. Failures are propagated so the outer `PgTransactionalCommandRunner` must roll back the whole transaction.

## Required project-specific adapter

`PgLiveBoqRevisionStoreFactory` is intentionally an integration seam. The source dump exposes the domain model and in-memory repository, but does not establish physical SQL table/column mappings for live BOQs and BOQ items. Implement this factory against the actual repository schema. Its `loadForUpdate` must use `SELECT ... FOR UPDATE`; `loadItems` must read the authoritative item table on the same connection; `compareAndSetState` must update with predicates for the expected revision and status and return true only for one updated row.

Do not implement these methods with a separate pool acquisition or ORM session. That would break atomicity.

## Important operational requirements

- Apply `V20261013_01__outbox_tenant_id.sql` before using this factory. The migration adds nullable `tenant_id` because legacy rows cannot safely be assigned a tenant without authoritative data. Backfill historical rows, then enforce `NOT NULL` in a follow-up migration once the mapping is known.
- Relay claim queries must avoid treating NULL-tenant legacy events as tenant-owned; reconcile them before publishing or route them to an explicit manual-recovery queue.
- `tenantId` must be derived from the authoritative BOQ row, not from caller-supplied request data.
- The transaction runner must own commit/rollback. Do not subscribe to a `Uni` internally or start nested transactions.
- JSON payload fields are intentionally small identifiers/status only; do not place confidential BOQ lines or pricing in the outbox payload.

## Semantics and tests to add against a real PostgreSQL instance

1. Capture the first revision where `current_revision = 1` and no snapshot exists.
2. Capture a subsequent revision and assert revision counter and live current revision move together.
3. Reject capture when BOQ status is not `DRAFT`.
4. Reject submission of a non-current revision.
5. Race two captures for one BOQ; assert unique monotonically increasing revision numbers or one clean conflict, never duplicate snapshots.
6. Race two approvals; exactly one should commit.
7. Force outbox insertion failure; snapshot and live BOQ updates must roll back.
8. Assert tenant ID is persisted and relay queries can filter it.
9. Reject mismatched quantity/rate units and verify no writes commit.

## Not yet verified

The bundle cannot verify SQL against live aggregate tables because the actual repository checkout and schema mappings are unavailable. This is orchestration plus a schema adapter seam, not a claim that the full repository compiles or is production-ready.
