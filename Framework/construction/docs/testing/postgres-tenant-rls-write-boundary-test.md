# PostgreSQL tenant RLS read/write boundary test

## Purpose

Exercise PostgreSQL row-level security `USING` and `WITH CHECK` behavior using a disposable PostgreSQL 16 container and a non-superuser, non-`BYPASSRLS` role. This complements the tenant-context pool test by checking write boundaries, not only row visibility.

## Run

From the `numou-phase1` directory:

```bash
bash scripts/postgres-tenant-rls-write-boundary-test.sh
```

Requires Docker and uses `SYIRKAH_TEST_PG_PORT` (default `55446`) on loopback. The script removes its disposable container on exit. Do not point this test at an existing application database.

## Acceptance criteria

- No tenant context exposes zero rows and rejects inserts.
- Tenant A sees only its own row.
- `WITH CHECK` rejects inserting a row owned by tenant B while tenant A is active.
- A tenant A update cannot change the row's tenant ownership to tenant B.
- A same-tenant payload update succeeds.
- The test role is neither superuser nor `BYPASSRLS`.

## Limits

This proves PostgreSQL fixture behavior only. It does not prove the Numou authentication layer supplies the trusted tenant ID, that the production connection pool installs it transaction-locally, or that canonical production roles and migrations use equivalent policies. Those must be checked in the actual repository and deployment configuration.
