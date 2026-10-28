# Construction Consultancy M1 — PostgreSQL Persistence

## Implementation

The construction PostgreSQL adapter now provides `DefaultPgConsultancyOperationsFactory`. It creates transaction-bound operations over the exact `SqlConnection` supplied by `PgTransactionalCommandRunner`; it does not acquire a second connection or fall back to the pool.

The command runner's existing two-argument constructor wires this factory by default. The three-argument constructor remains available for explicit adapter composition. The consultancy operations are reachable through `TransactionalCommandContext.consultancy()` and therefore participate in the same `withTransaction` scope as idempotency reservation/completion.

## Schema and invariants

Migration `V20261028_01__consultancy_engagements.sql` creates the consultancy engagement table with:

- composite primary key `(tenant_id, engagement_id)`;
- unique `(tenant_id, engagement_number)` constraint;
- distinct client and consultant organization check;
- lifecycle status and scope-revision constraints;
- cancellation reason constraints consistent with the aggregate's cancelled state;
- tenant-scoped project index.

Project and organization IDs remain opaque references. This extension does not create foreign keys into other capabilities' private persistence tables.

## Test coverage added

The PostgreSQL integration test covers insert/read restoration, lookup isolation across tenants, database-enforced engagement-number uniqueness per tenant, and transaction rollback of an inserted engagement when command work fails. It runs under the existing `pg-integration` profile and requires the construction migration bundle and PostgreSQL test database.

## Operational caveats

- Apply the migration bundle before deploying code that uses consultancy creation.
- Deployments using custom `PgTransactionalCommandContext` implementations must also implement the consultancy operations or keep the fail-closed default.
- A unique-constraint violation currently propagates as a persistence failure; the API/application boundary should map the specific PostgreSQL constraint to a stable conflict response without retrying outside the transaction.
- This slice persists the engagement draft only. It does not yet implement proposal/activation/completion/cancellation command handlers, deliverable persistence, signed scope documents, Indonesia regulatory evidence, or Sharia istisna' controls.
- Build and integration tests have not been run in this environment; CI and a PostgreSQL-enabled verification run remain required.