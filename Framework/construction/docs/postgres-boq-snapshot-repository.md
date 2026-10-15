# PostgreSQL BOQ revision snapshot repository

## Included adapter

`PgBoqRevisionSnapshotRepository` implements `BoqRevisionSnapshotRepository` against the snapshot tables introduced by `V20261009_01__boq_revision_snapshots_and_outbox.sql`.

- `insert` writes the revision header and its copied line items using the supplied transaction-bound `SqlConnection`.
- `find` and `findAllByBoqId` reconstruct domain snapshots from the header and item tables.
- `replaceIfStatus` is a compare-and-set update on the lifecycle status; a missing row or unexpected status raises `RevisionConflictException`.
- It does not acquire connections or manage transactions. The caller must pass a connection enlisted in the same transaction as the BOQ aggregate update, idempotency outcome, and outbox event.

## Integration requirements before merge

1. Verify the actual `BoqItemType` enum values against the `item_type` values stored in the migration.
2. Confirm the project's Vert.x Mutiny version supports the row accessors used here (`getOffsetDateTime`, `getBigDecimal`, `getUUID`).
3. Add the adapter in the real construction adapter module and ensure its existing module already includes the SPI/domain dependencies.
4. Add Testcontainers PostgreSQL tests for insert/read round-trip, duplicate revision insert, status compare-and-set conflict, header/item rollback, empty BOQ, and concurrent approval.
5. Add tenant/project authorization at the application boundary. The snapshot schema currently has no tenant column and must not be treated as an authorization boundary.
6. Ensure BOQ aggregate locking/revision allocation occurs before snapshot insertion. The snapshot primary key prevents duplicate `(boq_id, revision_number)` but does not allocate revision numbers safely by itself.

## Known scope boundary

This adapter persists snapshots; it does not load the live BOQ aggregate or allocate a new revision number. Those operations depend on the real BOQ persistence mapping, which was not established by the source dump. Do not deploy the starter migration without reconciling it with the project's migration conventions and schema.


## Tenant-isolated keys

Repository methods require `tenantId` for every read, insert, and status compare-and-set. Snapshot and item keys include tenant ID, and the item foreign key includes `(tenant_id, boq_id, revision_number)`. This protects against identical BOQ UUIDs in separate tenant namespaces; the tenant ID must come from trusted server-side context.
