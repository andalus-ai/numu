# CRM legacy tenant ownership inventory

## Purpose

Use `legacy-tenant-ownership-inventory.sql` to measure the remaining CRM
tenant-ownership reconciliation work after migrations V8-V15.

The report is deliberately read-only. It returns:

1. Total, tenant-owned, and `tenant_id IS NULL` row counts for Accounts,
   Customers, Leads, Opportunities, Referrals, Territories, and Territory
   Assignments.
2. Existing relationship rows where source and target ownership differs or
   either side is unassigned. Each row includes aggregate relationship name,
   source/target IDs, source/target tenant IDs, and an issue classification.

## Running the report

Run it using a database role with SELECT access to the CRM tables:

```sh
psql "$CRM_DATABASE_URL" \
  -v ON_ERROR_STOP=1 \
  -f crm/docs/operations/legacy-tenant-ownership-inventory.sql
```

The script contains only SELECT statements. For an additional database-side
guard, run it in a read-only transaction:

```sh
psql "$CRM_DATABASE_URL" -v ON_ERROR_STOP=1 \
  -c "BEGIN READ ONLY" \
  -f crm/docs/operations/legacy-tenant-ownership-inventory.sql \
  -c "ROLLBACK"
```

## Reconciliation rules

- A NULL tenant is unresolved, not a default tenant.
- Do not infer ownership from a parent, customer, referral target, territory,
  participant, email address, name, UUID pattern, or whichever relationship
  happens to be populated.
- A relationship mismatch is an investigation item, not permission to rewrite
  either row.
- Only an independently authoritative source (for example, a reviewed
  tenant-scoped import manifest or source-system ownership record) can support
  a proposed ownership mapping.
- Preserve the original evidence, mapping provenance, reviewer, and execution
  audit for every approved remediation.
- Do not relax global uniqueness constraints or change them to tenant-local
  uniqueness until duplicate and ownership inventories have been reviewed.

## Operational interpretation

The aggregate counts establish the size of the unowned population. The
relationship result set helps prioritize investigation, but it is not a
complete ownership oracle: rows without relationships will appear only in the
aggregate counts, and consistent relationships do not prove that their tenant
assignment is authoritative.

This milestone intentionally does not backfill `tenant_id`, validate V15
constraints against historical rows, or change application access behavior.
