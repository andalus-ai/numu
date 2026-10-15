# BOQ lifecycle concurrency integration tests

`DefaultPgBoqRevisionOperationsFactoryIntegrationTest` now includes two additional database-backed scenarios.

## Concurrent approval

1. Insert an isolated test BOQ and item.
2. Capture revision 1 and submit it for review.
3. Start two independent command transactions at a barrier, using different approvers for the same tenant, BOQ, and revision.
4. Assert exactly one command succeeds, the live BOQ is `APPROVED`, the snapshot is `APPROVED`, and exactly one `BoqRevisionApproved` event exists.

The live BOQ row is locked by the mapped store's `SELECT ... FOR UPDATE`; the second transaction should observe the committed state and fail the status precondition instead of emitting a second approval event.

## Stale revision submission

After capturing revision 1, attempt to submit revision 2. The operation must fail without changing the live BOQ status, snapshot status, or creating a submission event.

## Environment and limitations

- Apply all bundle migrations through `V20261014_01__tenant_scope_boq_revision_storage.sql` before running the Quarkus tests.
- Configure the host application's PostgreSQL test datasource and Java dependencies.
- `it_live_boq` and `it_live_boq_item` are test fixtures, not claims about the production Syirkah schema.
- These tests were added to the source bundle but have not been compiled or executed in this environment. Static contract checks do not substitute for a live test run.
