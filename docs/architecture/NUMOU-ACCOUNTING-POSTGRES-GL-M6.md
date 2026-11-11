# M6 — General Ledger PostgreSQL schema hardening

**Branch:** `runtime/m6-accounting-reporting-scope`  
**Status:** Tenant/ledger-scoped account and journal contracts, scoped reporting propagation, and a PostgreSQL account adapter are present. PostgreSQL journal persistence, scoped fiscal periods, atomic posting, and full integration verification remain follow-up work.

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

A PostgreSQL account adapter is now present and pushes tenant + ledger predicates into SQL for all scoped reads. It rejects unscoped reads rather than silently broadening access. The create-account handler now checks duplicate account numbers through the scoped tenant + ledger lookup before saving. The database unique constraint remains the final concurrency guard; the pre-check is a usability validation, not a substitute for that constraint. The old unscoped account methods remain temporarily, marked deprecated, for compatibility with legacy services and fixtures. Journal posting and reversal now use tenant + ledger scoped account lookups. Journal-entry repository ports expose scoped `findById` and `findAll` methods; the in-memory adapter overrides them with scope filtering and a focused test covers tenant/ledger isolation. The port's default scoped methods are compatibility fallbacks that filter `findAll()` in memory; production adapters must override them with SQL predicates. Financial reporting query records carry tenant + ledger scope, and trial-balance, balance-sheet, and income-statement handlers now propagate that scope to `FinancialReportingService`, which calls `AccountRepository.findAll(tenant, ledger)`. Legacy service overloads remain deprecated compatibility bridges to `default` / `primary`. A focused service test covers tenant/ledger isolation and rejection of mixed-currency account balances in a single-currency report. The REST query parameters are selectors, not proof of authorization: the runtime must derive or authorize the tenant and ledger against the authenticated principal before exposing these endpoints in a production deployment. This tranche does not claim to complete authorization.

## Remaining M6 work

1. Migrate remaining unscoped account callers and remove deprecated methods. Review the PostgreSQL account adapter's schema mappings and verify its integration tests against V14.
2. Implement PostgreSQL JournalEntryRepository with tenant/ledger predicates, atomic header + line persistence, and full lifecycle reconstitution.
3. Add tenant/ledger ownership to the FiscalPeriod aggregate and repository contract before implementing durable fiscal-period persistence; the current aggregate and repository are not scoped.
4. Implement outbox persistence in the same transaction as journal posting; do not claim atomic posting until the command transaction boundary is explicit.
5. Add PostgreSQL integration tests for tenant isolation, ledger isolation, duplicate account number, parent-scope constraints, journal line scope, and rollback.
6. Replace in-memory CDI defaults for production profile only; preserve in-memory adapters for tests/local profiles.

## Verification

Migration syntax and behavior have not been executed against PostgreSQL in this session. Validate from a clean database and from an existing V1–V13 database, including populated chart-of-accounts and journal-line tables. No CI success is claimed.
\n\n## M6.4 — Journal posting and reversal scope\n\n- `PostJournalEntryCommand` now carries `TenantRef` and `LedgerId`; the builder exposes both fields. The old seven-argument constructor remains as a deprecated compatibility bridge defaulting to `default` / `primary` and must not be used by tenant-aware production entry points.\n- `PostJournalEntryHandler` constructs the aggregate with command scope and resolves every account using the scoped account repository method. A missing account in that scope fails the posting attempt rather than resolving an account from another tenant or ledger.\n- `ReverseJournalEntryCommand` carries the same scope, and reversal lookup/account mutation use scoped repository methods. Missing scoped accounts now fail explicitly rather than being silently skipped.\n- `JournalEntryRepository` exposes scoped lookup/list methods. The in-memory adapter overrides them and a focused test covers tenant and ledger isolation. The default interface implementations are transitional compatibility fallbacks and are not an acceptable PostgreSQL query strategy.\n- Important outstanding correctness boundary: these handlers still perform multiple repository writes without an explicit shared unit-of-work/transaction. Scope isolation is improved, but atomic account balance + journal + outbox persistence is not yet guaranteed. The fiscal-period lookup is also still unscoped and must be resolved before production multi-ledger posting.\n\n\n## M6.5 — Scoped financial reporting\n\n- Trial balance, balance sheet, and income statement now call the account repository with explicit `TenantRef` + `LedgerId`; CQRS handlers propagate the query scope instead of discarding it.\n- The three report query records reject missing tenant or ledger scope at construction time.\n- Deprecated service overloads preserve legacy default-tenant / primary-ledger behavior only for source compatibility. New callers must use scoped signatures.\n- Report totals derive their currency from the scoped account set. Mixed-currency account balances are rejected explicitly: this report implementation does not perform FX translation or consolidation. A future multi-currency report must require an explicit reporting currency and a versioned FX-rate source.\n- Cash-flow output remains a placeholder and does not yet calculate from scoped journal activity. Sharia funds reporting is also still a zero-valued placeholder and its REST endpoint has not yet been given an explicit tenant/ledger query contract. These are not production-complete reports.\n- Added `FinancialReportingScopeTest` for tenant/ledger isolation and mixed-currency rejection. Test execution has not been performed in this session.\n