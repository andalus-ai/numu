# Advertising Platform — M1 Implementation

- **Status:** Initial domain and application contracts committed; durable adapter and verification pending.
- **Branch:** `feat/advertising-m1-account-inventory-core`
- **Scope:** Advertiser and publisher onboarding, publisher properties, ad placement definitions.

## Ownership and dependency rules

Advertising is a standalone business capability, not a child of Commerce Promotion. Its domain has no HTTP, CDI, database, broker, Finance, Party, or Identity dependency. `PartyReference` and `TenantId` are opaque boundary values; the host supplies authorization and party resolution through explicit adapters.

## Implemented contracts

- Distinct `AdvertiserAccount` and `PublisherAccount` aggregates, with explicit pending/active/suspended/closed lifecycle.
- `PublisherProperty` lifecycle begins at pending verification. Inventory cannot be defined until the property is verified.
- `AdPlacement` has a constrained stable code and format token; it is not deliverable until enabled.
- Tenant-scoped repository SPI for all four aggregate types.
- Application service performs authorization before mutation and verifies publisher/property ownership through tenant-scoped lookups.
- No dependency on Finance Billing, Payment, Wallet, Accounting, Commerce Promotion, or Marketplace internals.

## Explicit limitations / next implementation slice

1. Add PostgreSQL schema, tenant-composite keys, uniqueness constraints, optimistic locking, and durable repository implementation.
2. Add application tests for denied authorization, tenant isolation, lifecycle constraints, duplicate registration, and stale writes.
3. Integrate the actual public Identity authorization adapter only after its stable app contract is confirmed.
4. Resolve Party references through a public Party/Organization query contract; until then references remain opaque and no fake lookup API is introduced.
5. Add versioned REST/API contracts and runtime wiring after the persistence boundary is proven.
6. Run the narrow Maven reactor and PostgreSQL Testcontainers suite; no build/test claim is made by this commit.

M1 does not implement campaigns, creative review, ad serving, raw impression ingestion, billing, or publisher payout. Those remain later milestones with separate consistency and security requirements.
