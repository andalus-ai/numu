# Neutral Planning — Milestone 3: PostgreSQL persistence

## Delivered

- JDBC PlanRepository implementation with aggregate-level transactions.
- Normalized PostgreSQL schema for plans, current activities, dependencies, baseline headers, and frozen baseline activity/dependency snapshots.
- Optimistic revision checks on updates. A stale aggregate is rejected instead of silently overwriting a newer write.
- PostgreSQL triggers prevent updates or deletes to baseline headers and snapshot rows.
- Baseline snapshots are appended in sequence; previously persisted baseline identity/sequence cannot be replaced by a caller.
- Flyway migration and Quarkus integration tests exercise save/reload, baseline immutability across revisions, stale-writer rejection, and direct database deletion rejection.

## Persistence behavior

The plan header, current activity graph, and any new baseline snapshots are saved in one database transaction. The working activity/dependency rows may be replaced as a unit; baseline rows are insert-only and protected by database triggers. Optimistic revision is incremented only when the transaction commits successfully.

The adapter uses JDBC and is independent of the REST/API and construction-specific modules. Configure the application datasource for PostgreSQL and enable Flyway migrations when deploying this adapter.

## Production follow-ups

- Tenant identity and tenant-scoped uniqueness are not modeled in this milestone; deployments must not expose the repository across tenants until tenant scoping is added.
- Baseline approval authorization and actor provenance must be enforced by the application/service boundary.
- For high-volume plans, batch inserts and reduce baseline reload queries to avoid N+1 reads.
- Run the integration suite against the supported PostgreSQL version in CI; tests were authored but have not been executed in this environment.
