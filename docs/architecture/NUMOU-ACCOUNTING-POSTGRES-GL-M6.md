# M6 — General Ledger PostgreSQL schema hardening

**Branch:** `runtime/m6-accounting-postgres-gl`  
**Status:** Schema migration and scoped account-query contract groundwork added; PostgreSQL repository implementation and integration verification remain follow-up work.

## Why this migration

The existing account domain aggregate is scoped by both `TenantRef` and `LedgerId`, but the original `chart_of_accounts` table only carried tenant scope. It also lacked currency, balance, description, active, and Sharia-compliance fields. Journal lines inherited scope only from their parent journal, leaving cross-scope association mistakes harder to constrain at the database boundary.

## V14 changes

- Adds `ledger_id`, `currency`, `current_balance`, `description`, `active`, and `sharia_compliant` to chart of accounts.
- Backfills existing accounts to the `primary` ledger and `IDR` currency.
- Replaces tenant-only account-code uniqueness with tenant + ledger + account-code uniqueness.
- Adds tenant and ledger scope to journal lines and backfills it from the owning journal.
- Adds a composite foreign key from journal line scope to journal entry scope.
- Adds a composite self-reference for account parent relationships, preventing cross-tenant or cross-ledger parent references.
- Adds reversal metadata columns to journal entries.

## Important rollout note

The IDR backfill is a compatibility default for existing rows, not a claim that all historical balances were denominated in IDR. Before production migration, operators must confirm the currency of existing account data or replace the default in a tenant-specific migration. The migration intentionally does not rewrite monetary balances.

## Scoped account-query groundwork

The application port now exposes explicit tenant + ledger variants for account lookup by ID, lookup by account number, and listing. The in-memory adapter filters on both values, and the account REST read endpoints require both selectors. A focused adapter test covers cross-tenant and cross-ledger isolation.

The create-account handler now checks duplicate account numbers through the scoped tenant + ledger lookup before saving. The database unique constraint remains the final concurrency guard; the pre-check is a usability validation, not a substitute for that constraint. The old unscoped methods remain temporarily, marked deprecated, because journal posting, financial reporting, reversal, and legacy test fixtures still call them. They are compatibility-only and must be migrated before the PostgreSQL implementation can become the default. The REST query parameters are selectors, not proof of authorization: the runtime must derive or authorize the tenant and ledger against the authenticated principal before exposing these endpoints in a production deployment. This tranche does not claim to complete authorization.

## Remaining M6 work

1. Extend journal-posting and reporting query contracts to carry tenant + ledger scope, migrate reversal and remaining callers, then remove deprecated methods; implement PostgreSQL AccountRepository against the scoped contract.
2. Implement JournalEntryRepository with atomic header + line persistence and full lifecycle reconstitution.
3. Implement FiscalPeriodRepository with tenant/ledger ownership (the current FiscalPeriod aggregate does not yet carry those fields; resolve that domain boundary before writing its adapter).
4. Implement outbox persistence in the same transaction as journal posting; do not claim atomic posting until the command transaction boundary is explicit.
5. Add PostgreSQL integration tests for tenant isolation, ledger isolation, duplicate account number, parent-scope constraints, journal line scope, and rollback.
6. Replace in-memory CDI defaults for production profile only; preserve in-memory adapters for tests/local profiles.

## Verification

Migration syntax and behavior have not been executed against PostgreSQL in this session. Validate from a clean database and from an existing V1–V13 database, including populated chart-of-accounts and journal-line tables. No CI success is claimed.
