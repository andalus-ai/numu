# Tenant-scope migration postflight

## Purpose

`db/postflight/V20261014_tenant_scope_postflight.sql` is a read-only structural acceptance check for `V20261014_01__tenant_scope_boq_revision_storage.sql`. Run it against the same reviewed database and schema after the migration completes.

It fails with an exception if any of the three revision tables is missing, any `tenant_id` column is nullable, a composite primary key differs from the intended tenant-scoped column order, the validated item-to-snapshot foreign key is missing, or the tenant/project lookup index is missing.

## Run

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 \
  -f db/postflight/V20261014_tenant_scope_postflight.sql
```

Use a reviewed target connection. A successful result should include `tenant_scope_postflight = PASS`. Preserve the command output with the migration change record.

## Acceptance boundary

A passing postflight establishes only the checked PostgreSQL structure. It does **not** prove that existing rows were assigned to the correct tenant, that the application propagates authenticated tenant context, that outbox polling is tenant-filtered, or that authorization and cross-tenant isolation work end-to-end. Those require data reconciliation plus application-level and adversarial integration tests in the real Numou repository.

Do not treat this postflight as approval to bypass a failed preflight or to apply the migration to non-empty tables without a reviewed authoritative tenant backfill plan. The preflight and migration safety guard remain separate mandatory steps.
