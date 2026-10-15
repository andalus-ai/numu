# PostgreSQL BOQ revision allocation

## Included

`PgBoqRevisionNumberAllocator` allocates revision numbers on a caller-owned Vert.x Mutiny `SqlConnection`. It uses a row per BOQ, initializes that row if absent, locks it with `SELECT ... FOR UPDATE`, and updates the allocated revision before returning it.

## Required transaction protocol

1. Start the command transaction with `PgTransactionalCommandRunner`.
2. Load the authoritative BOQ state and derive `minimumNextRevision` according to the real aggregate semantics.
3. Call `allocateNext(boqId, minimumNextRevision)`.
4. Capture the snapshot using that allocated revision number and insert the header/items.
5. Update the live BOQ state (if the domain requires a current revision update) with an optimistic concurrency predicate.
6. Append the domain event to the outbox and complete the idempotency response.
7. Commit once.

Do not call this allocator using a pool-acquired connection or outside the same transaction as the snapshot insert. The row lock only protects callers that use this allocator. All revision-creation paths must converge on it.

## Why the minimum is an argument

The source dump shows `Boq.currentRevision` initialized to `1`, but it does not establish the intended relation between the mutable aggregate's field and a new immutable snapshot number. The adapter must decide whether to snapshot the current revision or allocate the next one. This class intentionally avoids silently choosing that business rule.

## Required tests against PostgreSQL

- Two concurrent transactions allocate for the same BOQ: distinct increasing values.
- Two transactions allocate for different BOQs: no cross-BOQ blocking beyond normal database contention.
- A transaction that allocates and then rolls back does not persist its counter advance.
- A seeded/imported BOQ whose minimum next revision exceeds the stored counter receives the supplied minimum.
- Existing counter greater than supplied minimum continues monotonically.
- Snapshot insert failure rolls back both counter advancement and other command writes.
- Every revision creation route uses the same allocator and transaction-bound connection.

## Limitations

This slice does not implement live BOQ loading or update the existing BOQ table, because the provided source is a text dump and does not establish physical table names or ORM mappings. The migration name/order, schema qualification, tenant model, and numeric precision must be reconciled with the real checkout before deployment. No Maven build or live PostgreSQL test has been run here.


## Tenant scope

Revision counters are keyed by `(tenant_id, boq_id)`, not only by BOQ UUID. Pass the trusted tenant identifier to `allocateNext(tenantId, boqId, minimumNextRevision)`. Snapshot and item persistence use the same tenant predicate and composite keys. Never infer tenant identity from request payload fields.

Migration `V20261014_01__tenant_scope_boq_revision_storage.sql` deliberately refuses to proceed when revision/counter rows already exist: the source bundle does not define the authoritative live BOQ table needed to backfill tenant ownership. For a populated database, write and review an operator-authored backfill from the real BOQ ownership mapping before applying the key changes.
