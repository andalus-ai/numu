# Tenant-scope migration preflight

Use this read-only report before applying `V20261014_01__tenant_scope_boq_revision_storage.sql` to an existing environment. It reports row counts for the three revision tables and whether they satisfy the migration's empty-table guard.

## Run

From a trusted operator workstation, connect explicitly to the intended target database and execute:

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f db/preflight/V20261014_tenant_scope_preflight.sql
```

Review the connection target and schema before running. The SQL uses `public` deliberately; if the application uses a different schema, review and adapt it rather than assuming the report checked the right tables.

## Interpret the result

- All three row counts are `0` and the boolean is `t`: the data-empty guard is satisfied. This is not a complete migration approval; verify the migration history, backup/restore plan, schema, and application rollout first.
- Any row count is nonzero and the boolean is `f`: **stop**. Do not apply the migration unchanged, add a placeholder/default tenant, or delete rows to bypass the guard. Determine tenant ownership from the authoritative live BOQ/project tenancy model, design a reviewed backfill, and adapt the migration to preserve existing data.
- A missing-table exception means the database/schema or migration history differs from the expected starter baseline. Resolve that mismatch before continuing.

## What this does not prove

The report is read-only and intentionally does not infer ownership, test authorization, or validate the host application's physical BOQ/project tables. It must be followed by a schema reconciliation and reviewed migration plan in the real Numou repository. Never run this as a substitute for backup, staging rehearsal, or change approval.
