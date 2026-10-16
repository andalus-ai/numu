# Tenant-isolation SQL protocol test

Run `scripts/postgres-tenant-isolation-test.sh` through `scripts/run-phase1-validation.sh --postgres-only` or directly. It creates a disposable PostgreSQL 16 container and never connects to an existing application database.

The fixture checks tenant-scoped composite keys for revision snapshots, items, and counters; same BOQ/revision identifiers in separate tenant scopes; tenant-qualified item joins; no-op cross-tenant snapshot/counter updates; rejection of an item whose tenant does not match its referenced snapshot; and tenant-filtered outbox polling.

## Limits and acceptance gate

This is a representative SQL protocol fixture, not a test of the production migrations or Java adapter. It does not prove that every production query carries tenant predicates, that tenant identity is trusted at runtime, or that row-level security is enabled. The actual Numou checkout must run equivalent tests against the real schema, migrations, repository adapters, trusted tenant context, and outbox relay. Review `V20261014_01__tenant_scope_boq_revision_storage.sql` and the outbox tenant backfill/enforcement plan before rollout; do not infer historical event ownership.
