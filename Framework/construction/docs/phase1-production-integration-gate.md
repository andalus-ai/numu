# Phase 1 real-repository integration gate

## Why this gate exists

This starter contains source-contract checks and test proposals, but it is not a complete checkout of the Syirkah application. Static assertions and disposable SQL fixtures cannot establish that the production adapters compile, that migrations match the canonical schema, or that tenant isolation works through the deployed connection pool.

Run `scripts/verify-real-repository-readiness.sh` from the root of the complete Syirkah repository before claiming integration readiness. It checks for conventional build, migration, runtime-configuration, and Java source entry points. Nonstandard layouts require manual review; a pass is not a runtime verification.

## Required integration sequence

1. **Reconcile source and dependencies**: port the starter files into the canonical modules, resolve duplicate types and package conventions, and use the repository's supported JDK and build tool.
2. **Reconcile schema**: compare every proposed SQL statement and fixture with canonical migrations, constraints, indexes, tenant keys, outbox columns, and RLS policies. Do not apply starter migrations directly to production.
3. **Bind trusted tenant identity**: use the authenticated server-side tenant resolver; reject missing identity. Do not trust a raw client header or command payload as the authority.
4. **Set tenant context on the transaction connection**: if RLS relies on a transaction-local setting, set it inside the same transaction and on the same connection before tenant-sensitive SQL. Verify commit, rollback, connection reuse, and concurrent tenants.
5. **Verify database roles**: test with the actual application/relay roles, not a superuser or table owner that bypasses RLS. Confirm grants and policies are least-privilege.
6. **Run supported tests**: compile and execute the complete Java unit/integration suite; run PostgreSQL tests using the canonical migrations; run any broker/consumer tests with the real supported harness.
7. **Capture evidence**: record commit SHA, build command, database version, migration version, test results, and any skipped tests.

## Release acceptance criteria

- Supported project build completes without compilation errors.
- Canonical migrations apply cleanly to an empty test database and the migration rehearsal succeeds against a representative sanitized database.
- Tenant A cannot read or mutate tenant B's BOQ revisions, counters, idempotency records, or outbox rows using the real application role.
- Tenant context is transaction-local and cannot leak across commit, rollback, pool reuse, or concurrent requests.
- BOQ capture, submission, and approval commit their snapshot/live state/outbox effects atomically.
- Outbox claim/ack/retry operations enforce current lease fencing and tenant restrictions.
- All tests required by CI pass; skipped tests are explicitly justified and not presented as passing.

## Current starter limitation

The current ZIP does not contain the complete host application's build descriptor, canonical migrations, authenticated tenant provider, or production database role setup. Therefore this gate is expected to report `BLOCKED` when run against the starter alone. That is intentional and safer than treating static source checks as proof of production readiness.
