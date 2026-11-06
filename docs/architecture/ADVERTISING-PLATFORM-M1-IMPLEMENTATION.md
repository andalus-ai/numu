# Advertising Platform — M1 Implementation

- **Status:** Domain, application, PostgreSQL adapter, schema and initial persistence tests committed; build/test execution remains unverified.
- **Branch:** `feat/advertising-m1-account-inventory-core`
- **Scope:** Advertiser and publisher onboarding, publisher properties, ad placement definitions.

## Ownership and dependency rules

Advertising is a standalone business capability, not a child of Commerce Promotion. Its domain has no HTTP, CDI, database, broker, Finance, Party, or Identity dependency. `PartyReference` and `TenantId` are opaque boundary values; the host supplies authorization and party resolution through explicit adapters.

## Implemented contracts

- Distinct `AdvertiserAccount` and `PublisherAccount` aggregates, with explicit pending/active/suspended/closed lifecycle.
- `PublisherProperty` lifecycle begins at pending verification. Inventory cannot be defined until the property is verified.
- `AdPlacement` has a constrained stable code and format token; it is not deliverable until enabled.
- Tenant-scoped repository SPI for all four aggregate types.
- Application use cases authorize before mutation, require active publishers, and require verified properties before placements can be defined or enabled.
- The PostgreSQL adapter implements tenant-scoped reads/writes and compare-and-set updates using aggregate versions.
- PostgreSQL schema uses tenant-composite primary keys and foreign keys, tenant-scoped party/property/placement uniqueness, status checks, and supporting indexes.
- Adapter module is included in the Advertising aggregator; Advertising aggregator is included in the framework Maven reactor.
- No dependency on Finance Billing, Payment, Wallet, Accounting, Commerce Promotion, or Marketplace internals.

## Persistence semantics

- Version-zero aggregates are inserted; duplicate tenant/aggregate keys or uniqueness conflicts fail.
- Version-greater-than-zero aggregates update only when the stored version equals the incoming version minus one. A stale writer affects zero rows and fails rather than overwriting newer state.
- All aggregate reads and updates include tenant scope.
- Property and placement foreign keys include tenant ID, preventing cross-tenant parent references at the database boundary.
- The JDBC adapter uses blocking JDBC calls and returns completed/failed `CompletionStage` values. Runtime wiring must therefore invoke it on a blocking worker, not an event-loop thread.

## Initial verification tests added

- Domain lifecycle and URI/placement validation tests.
- Application tests for denied authorization, active-publisher requirements, property verification, and placement enablement.
- PostgreSQL Testcontainers tests for tenant-isolated reads, stale-version rejection, tenant-scoped uniqueness, cross-tenant foreign-key rejection, and duplicate placement codes.

**Verification caveat:** These tests have been added to the repository, but no Maven build or Testcontainers execution has been run from this environment. Do not interpret committed tests as passing CI.

## Remaining M1 work

1. Run the narrow Maven reactor and PostgreSQL Testcontainers suite; resolve compiler/test failures.
2. Add application service tests for denied authorization and state-transition preconditions.
3. Integrate the actual public Identity authorization adapter only after its stable app contract is confirmed.
4. Resolve Party references through a public Party/Organization query contract; until then references remain opaque and no fake lookup API is introduced.
5. Add versioned REST/API contracts and runtime wiring after persistence and host-authentication boundaries are proven.
6. Consider database-level row-level security only in a deployment profile that can guarantee tenant session context is set and reset safely; tenant predicates and composite constraints remain mandatory regardless.

M1 does not implement campaigns, creative review, ad serving, raw impression ingestion, billing, or publisher payout. Those remain later milestones with separate consistency and security requirements.
