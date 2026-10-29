# CRM tenant-isolation preflight

Run `db/preflight/tenant-isolation-preflight.sql` from the repository root against a **restored staging copy** of the CRM PostgreSQL database:

```sh
psql "$CRM_DATABASE_URL" -v ON_ERROR_STOP=1 -f crm/db/preflight/tenant-isolation-preflight.sql
```

The script is intentionally read-only with respect to application tables. It creates a temporary report table inside a transaction, reports the presence and row count of the current CRM aggregate/child tables, and emits null-`tenant_id` counts for tables that already have that column. It does not backfill, alter, or delete business data.

## Interpreting the report

- `TABLE_MISSING`: confirm the active schema, migration history, and deployed version before proceeding.
- `BLOCK`: tenant ownership cannot be safely inferred from this schema. Identify an authoritative, auditable mapping first.
- `REVIEW`: inspect actual ownership and tenant-null counts before constraints or activation.

A zero row count is not by itself proof that a table's tenant policy is correct. This script only checks the known table list; compare it with the deployed schema and include any additional CRM-owned tables (especially outbox, audit, or async work tables) before declaring the inventory complete.

**Do not use the report to assign a default tenant.** Do not treat actor, participant, account, or organization identifiers as tenant IDs without a documented domain guarantee. No tenant-column migration or backfill should be applied until every populated table has a verified ownership source and unresolved rows are reconciled.

This is a preflight utility, not tenant enforcement and not a migration. It does not establish that CRM queries are isolated, and it does not replace adapter integration tests.
