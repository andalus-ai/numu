# Configurable live BOQ PostgreSQL adapter

This slice implements the previously abstract `PgLiveBoqRevisionStoreFactory` as
`MappedPgLiveBoqRevisionStoreFactory`, configured with `PgLiveBoqSqlMapping`.
It supplies transaction-bound SQL for:

- locking one live BOQ row with `SELECT ... FOR UPDATE`, scoped by both BOQ ID and the trusted tenant ID;
- loading BOQ items in deterministic `(item_code, item_id)` order, gated by an owning BOQ row in that tenant;
- compare-and-set updates of current revision, status, and the configured change timestamp, again scoped by tenant.

## Required integration before use

The source bundle is a text dump, not a checked-out buildable repository, and it does
not establish the authoritative BOQ table/column mapping. Do not instantiate this
factory with guessed names. Confirm the exact tables, columns, enum encodings, numeric
scales, and timestamp column from the live migration/entity mapping first. Then create
`PgLiveBoqSqlMapping` once from those verified identifiers and pass the factory to
`DefaultPgBoqRevisionOperationsFactory`.

Example shape (placeholders only; these are **not** asserted Numou table names):

```java
var mapping = new PgLiveBoqSqlMapping(
    "verified_schema.verified_boq", "id", "project_id", "tenant_id", "name",
    "current_revision", "status", "updated_at",
    "verified_schema.verified_boq_item", "id", "boq_id", "wbs_node_id",
    "item_code", "description", "item_type", "quantity", "quantity_unit",
    "rate_amount", "rate_unit", "currency_code");
var liveStoreFactory = new MappedPgLiveBoqRevisionStoreFactory(mapping);
var operationsFactory = new DefaultPgBoqRevisionOperationsFactory(liveStoreFactory, authorization);
```

All SQL values use prepared-statement parameters. Table/column identifiers cannot be
parameters, so the mapping validates them against a conservative identifier grammar
and quotes them. The adapter never opens, commits, or rolls back a transaction; it
must receive the same `SqlConnection` from `PgTransactionalCommandRunner` used by the
snapshot repository, revision allocator, outbox write, and idempotency completion.

## Tenant and authorization boundary

The live-store SPI now requires `tenantId` for every read and compare-and-set. Pass this
value from the authenticated server-side tenant context; never trust a tenant ID supplied
only in the request body. A BOQ ID belonging to another tenant is returned as not found,
and its items/state cannot be read or mutated through this adapter. Actor identity still
does not itself establish permission to approve or submit; enforce role/policy authorization
at the application boundary.

The revision snapshot and counter starter tables are keyed by `boq_id` rather than
`(tenant_id, boq_id)`. This therefore assumes BOQ UUIDs are globally unique across tenants.
If the actual Numou schema does not guarantee global uniqueness, the snapshot header/item
schema, repository queries, and revision counter must all be migrated to carry tenant scope
before use. Tenant predicates on the live table do not compensate for colliding BOQ IDs in
those globally keyed support tables.

A disposable SQL protocol test is provided as `scripts/postgres-tenant-isolation-test.sh`.
It verifies the intended lookup, item-read, and compare-and-set predicates against test-only
tables; it does not execute the Java adapter.

## Verification checklist

- [ ] Compare every mapping field against actual entities and migrations.
- [ ] Verify the item type stored in PostgreSQL exactly matches `BoqItemType.name()`.
- [ ] Verify `updated_at` (or mapped timestamp) is writable and has compatible type.
- [ ] Verify BOQ IDs are globally unique; otherwise tenant-scope snapshot and counter schemas too.
- [ ] Run `scripts/postgres-tenant-isolation-test.sh` in an environment with Docker.
- [ ] Run the full project Maven build and PostgreSQL workflow/concurrency scripts.
- [ ] Test that any snapshot/outbox/CAS failure rolls back the whole command transaction.


## Authorization wiring

The operations factory requires a `BoqRevisionAuthorization` implementation; there is no allow-all default. It is invoked for capture, submit-for-review, and approve before the live BOQ is loaded. The implementation must resolve permissions from trusted server-side identity and tenant context. A request-body actor UUID is an audit identifier, not authentication.
