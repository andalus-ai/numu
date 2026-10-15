# PostgreSQL tenant-context transaction integration test

`PgTenantContextTransactionIntegrationTest` uses the configured Quarkus `PgPool` and reuses a single acquired connection across transactions.

It checks two database-level properties:

- A tenant identifier installed with `set_config('app.tenant_id', ..., true)` is transaction-local and is absent after commit when the same connection begins a later transaction.
- A transaction rollback also clears the transaction-local setting before that same connection is reused.

## Run

Run this test in the actual Syirkah Quarkus test profile with PostgreSQL available and the project's normal test configuration:

```bash
./mvnw -Dtest=PgTenantContextTransactionIntegrationTest test
```

If the repository uses a different build command or test profile, use its canonical command instead.

## Important limits

This test exercises PostgreSQL's transaction-local setting through the configured pool. It does **not** prove that authentication middleware sets the tenant from a trusted identity, that every production command sets it before any query, or that production RLS policies enforce tenant isolation. Those checks require the actual application request path, migrations, and database roles.
