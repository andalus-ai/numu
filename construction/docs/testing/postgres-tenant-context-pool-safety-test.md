# Tenant context propagation and pooled-connection safety

Run `scripts/postgres-tenant-context-pool-safety-test.sh` in an environment with Docker. The script creates and removes a disposable PostgreSQL 16 container and uses a forced-RLS fixture table.

## Scenarios

- A transaction sets `app.tenant_id` with `set_config(..., true)` and sees only that tenant's rows.
- The same physical database session is reused for a later transaction without tenant context; it must see no rows.
- A tenant B transaction on that same session sees only tenant B's rows.
- A tenant A transaction that rolls back must not leak its context to the next transaction.
- Independent sessions with tenant A and tenant B contexts remain isolated.

The `true` argument makes `set_config` transaction-local. Context must be derived from authenticated server-side identity and established inside the transaction that performs tenant-scoped queries. Do not trust a client-supplied tenant identifier or rely on session-level `SET` state with pooled connections.

## Run

```bash
scripts/check-tenant-context-pool-safety-contract.sh
scripts/postgres-tenant-context-pool-safety-test.sh
# or include it in the disposable database suite:
scripts/run-phase1-validation.sh --postgres-only
```

## Limits

This test validates PostgreSQL transaction-local setting and RLS behavior in an isolated fixture. It does **not** prove the real Numou request context, authentication filter, reactive transaction runner, pool checkout/return hooks, or application adapters propagate context correctly. Add an application-level integration test in the full repository that runs tenant A and tenant B requests concurrently through the real connection pool and verifies both commit and rollback paths.
