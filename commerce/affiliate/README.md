# Affiliate & Partner Capability

This is the initial, deployment-neutral affiliate foundation. It is a capability boundary, not a mandate to run a separate service.

## M2 scope

- Tenant-scoped affiliate program lifecycle.
- Partner registration/approval lifecycle.
- Program membership lifecycle.
- Normalized referral codes and link disablement.
- Tenant-scoped repository ports and an application setup contract carrying actor, idempotency, and correlation context.

## Explicit non-goals in this milestone

- Attribution windows, last/first/multi-touch policy, and conversion ingestion (M3).
- Commission plans/calculation, approval, reversals, disputes, and payable ledger (M4).
- Payment-provider execution, wallet balances, and accounting postings (M5 integrations).
- HTTP endpoints, database migrations, and production persistence adapters. Those must follow existing reactor/runtime conventions and have integration tests before being considered production-ready.

The application contract is intentionally not backed by a fake in-memory or provider implementation. Consumers must compose an implementation through explicit adapters. All repository methods require tenant scope; implementations must enforce it in the query and add optimistic concurrency and uniqueness guarantees at the persistence boundary.
