# PostgreSQL outbox role-boundary acceptance test

Run `scripts/postgres-outbox-role-boundary-test.sh` in an environment with Docker. It creates and removes a disposable PostgreSQL 16 container and uses an isolated fixture table rather than altering the application schema.

The fixture validates these least-privilege principles:

- a relay role has neither superuser nor `BYPASSRLS` privileges;
- row-level security limits visible and mutable rows to the tenant set in the trusted session context;
- the relay can update only delivery-state columns, not tenant ownership or delete rows;
- a command-writer role can insert but cannot mutate relay delivery state.

## Important limits

This is a protocol/permissions fixture, **not evidence that the real Numou database roles or connection pool set `app.tenant_id` correctly**. Before production use, reconcile the role names, grant model, RLS policy, transaction-local tenant setting, table owner, migration ownership, and service-account configuration with the canonical Numou migrations and runtime. Never accept a tenant setting supplied directly by an untrusted client. Set it from authenticated server-side context, ideally transaction-locally to prevent pooled-connection leakage.
