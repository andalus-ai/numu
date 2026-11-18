# Numou Accounting Persistence — M6 tenant-scope foundation

**Status:** Tenant-scope bridge implemented; PostgreSQL repositories and durable posting are not yet implemented.  
**Base:** M5 commit `a965b974282b932b356e8209ad553e02b2e0782e`

## Evidence and boundary

The Accounting application ports currently expose unqualified operations such as `findById(id)`, `findAll()`, and `findByAccountNumber(number)`. The Account and JournalEntry aggregates carry `TenantRef` and `LedgerId`; FiscalPeriod and FixedAsset do not currently carry tenant identity. The shared Foundation runtime exposes the authenticated request tenant through `TenantContext`.

It would be unsafe to add SQL repositories that silently infer tenant from aggregate defaults or issue unscoped list/look-up queries. M6 therefore starts by establishing one fail-closed bridge from the authenticated Foundation tenant context into the Accounting application layer.

## Implemented

- Added `AccountingTenantScopeProvider` as an application port returning the current `TenantRef`.
- Added `RequestAccountingTenantScopeProvider` as the Quarkus adapter over Foundation's `TenantContext`.
- Missing or blank tenant context fails closed. The provider never falls back to `TenantRef.defaultTenant()`.
- Added tests for explicit tenant resolution, missing context, and blank context.

## Required next work before production persistence

1. Extend tenant ownership consistently to FiscalPeriod and FixedAsset, including ledger scope where appropriate; define migration/backfill behavior for existing records.
2. Change Accounting repository contracts so tenant scope is explicit in their semantics and enforce it in every query, unique key, update, and delete. Do not rely only on caller-provided aggregate tenant IDs.
3. Inspect and reuse the shared JDBC/Quarkus transaction conventions. Journal aggregate, journal lines, account balance changes, audit records, and outbox event inserts must commit or roll back together.
4. Add PostgreSQL migrations with tenant+ledger composite uniqueness and foreign keys. Avoid globally unique business numbers unless the domain contract explicitly requires them.
5. Add PostgreSQL integration tests for cross-tenant read/write isolation, tenant-scoped uniqueness, transaction rollback, concurrent posting, and outbox replay/claim semantics.
6. Only then replace in-memory CDI producers in a selectable persistence profile and compose the standalone Accounting distribution.

## Verification

Focused test source was added. It has not been executed in this environment. Suggested reactor command:

```bash
cd finance
mvn -pl accounting/numou-accounting-adapter -am -Dtest=RequestAccountingTenantScopeProviderTest -Dsurefire.failIfNoSpecifiedTests=false test
```

This milestone does not claim PostgreSQL durability, tenant isolation of existing repository implementations, or production-ready standalone Accounting.
