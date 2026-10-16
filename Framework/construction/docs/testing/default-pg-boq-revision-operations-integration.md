# Default PostgreSQL BOQ revision operations integration tests

`DefaultPgBoqRevisionOperationsFactoryIntegrationTest` invokes the real `DefaultPgBoqRevisionOperationsFactory` through `PgTransactionalCommandRunner` and the configurable `MappedPgLiveBoqRevisionStoreFactory`.

## Scenarios

1. **Capture commits all writes together**: a live BOQ and one item produce a revision snapshot and item, a tenant/BOQ revision counter, the live BOQ compare-and-set update, and a `BoqRevisionCaptured` outbox event.
2. **Failure after capture rolls everything back**: the test injects a failure after the operation returns but before the transaction callback completes, then verifies snapshot header/item, counter, outbox event, and live BOQ state all remain unchanged.

## Environment requirements

- Run the bundle migrations through `V20261014_01__tenant_scope_boq_revision_storage.sql` before the Quarkus test suite.
- Provide a working Quarkus PostgreSQL test datasource and the host project's dependencies.
- The test creates `it_live_boq` and `it_live_boq_item` fixture tables. These are deliberately test-only tables; they do not assert that Numou's production live BOQ tables have these names or columns.
- The database used for tests should be disposable. The test uses unique tenant/BOQ identifiers and leaves fixture rows behind for diagnosis.

## What this does not prove

The source bundle does not include the host build descriptor or a complete application checkout, so these Java tests have not been compiled or executed here. They test the real operations factory class with a test mapping, but they do not verify the host application's actual BOQ schema mapping, authentication adapter, or production datasource wiring.
