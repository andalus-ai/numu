# Finance Wallet — Persistence and Replay-Safety Audit

- **Status:** Source inspection only; persistence guarantees not yet runtime-verified
- **Date:** 2026-10-26
- **Related decisions:** [ADR-006](ADR-006-finance-wallet-capability.md), [consumer inventory](FINANCE-WALLET-CONSUMER-INVENTORY.md)
- **Scope:** Mobility wallet ledger adapter/schema and Pesantren wallet repository available in the inspected tree

## Executive finding

The inspected source contains useful safeguards, including a tenant-scoped append-only PostgreSQL wallet ledger, deterministic entry IDs, transaction-aware ledger append comments, and optimistic-lock retry orchestration. However, the source inspection does **not** prove end-to-end exactly-once wallet effects or safe concurrency. The schema includes a durable unique index on `(tenant_id, wallet_id, ref)` in migration V302 in addition to the ledger primary key `(tenant_id, entry_id)`. The aggregate still keeps only a bounded recent set of refs, so the database constraint—not that cache—must be the lifetime operation guard. The schema does not declare uniqueness for per-wallet `seq`. The Pesantren wallet adapter inspected is in-memory and does not provide durable persistence.

These findings are a design review, not a claim that production runtime behavior has been tested. The original Mobility persistence source labels its adapter “NOT COMPILED in sandbox”; the extracted neutral adapter and its integration wiring still require verified CI and PostgreSQL runtime tests.

## Mobility findings

### Positive controls found

1. `LedgerEntry.of(...)` derives a deterministic UUID from wallet ID, operation ref and entry kind.
2. `wallet_ledger` is keyed by `(tenant_id, entry_id)`; migration V302 adds unique `(tenant_id, wallet_id, ref)` to enforce lifetime operation-reference uniqueness. The extracted neutral adapter now targets the primary-key conflict explicitly so exact entry replay is a no-op while other unique violations remain failures.
3. The migration installs a trigger rejecting UPDATE/DELETE and TRUNCATE on the ledger table.
4. The migration enables and forces row-level security with a tenant policy using `app.tenant_id`.
5. `PgLedgerRepository` wraps queries in `TenantTransaction.run(tenantId, ...)` and comments that ledger append joins the wallet transaction.
6. `WalletService` describes mutation, save, ledger append and event publication within a unit of work, with bounded retries for optimistic-lock conflicts.
7. The in-memory ledger repository uses a concurrent map and tenant-scoped entry key, which is useful for unit tests but is not a substitute for database behavior.

### Gaps and questions that require tests or schema verification

| Concern | Evidence from inspected source | Required follow-up |
|---|---|---|
| Lifetime idempotency | V302 enforces unique `(tenant_id, wallet_id, ref)`; `Wallet` also stores a bounded recent-ref cache (`MAX_REFS = 500`). Ledger entry ID includes ref **and kind**. | Verify the migration is applied in every runtime that uses the neutral adapter, and test replay after eviction/restart plus same-ref/different-payload rejection. |
| Operation identity across kinds | V302 adds a unique index on `(tenant_id, wallet_id, ref)`, which rejects reuse of the same ref for a different movement kind on the same wallet. | Verify SQL conflict behavior and the service's durable lookup/fingerprint checks under sequential replay and concurrency. |
| Sequence ordering | `seq` is documented as wallet version and used for ordered history, but the inspected ledger table has no `UNIQUE (tenant_id, wallet_id, seq)` constraint. | Confirm the wallet aggregate's optimistic version and write transaction serialize sequence assignment. Add a database constraint or equivalent atomic guarantee if the invariant requires it. Test concurrent writes. |
| Duplicate ledger append vs duplicate wallet mutation | `ON CONFLICT DO NOTHING` suppresses duplicate ledger rows, but by itself does not roll back or suppress a separate wallet balance update. | Test duplicate command replay through the full service transaction, including the case where the ledger insert conflicts after aggregate mutation. Ensure the entire operation is atomic and duplicate semantics are surfaced correctly. |
| Ledger-to-wallet atomicity | Adapter comments say append joins the wallet change transaction; service comments describe a unit of work. | Trace the actual wallet repository and `UnitOfWork` wiring. Add an integration test proving wallet save + ledger append roll back together on failure. |
| Event reliability | Service comments say publish events in the unit of work, but the inspected snippets do not establish transactional outbox or durable publication. | Inspect event sink implementation and runtime composition. Test crash after commit/before publish and redelivery behavior; use outbox/inbox where needed. |
| Tenant isolation | RLS policy is defined in V300 and adapter uses `TenantTransaction`. | Test missing tenant setting fails closed; test cross-tenant reads/writes and connection-pool setting reset. Confirm migration role/ownership does not bypass intended RLS. |
| Wallet document schema | Comment in V300 says wallet aggregates live in `aggregate_document` (V200), while ledger is in V300. | Inspect V200 schema and repository implementation together; verify version compare-and-set, tenant key, transaction scope and uniqueness of aggregate identity. |
| Payout/top-up workflow | Top-up and payout models/services exist; provider callbacks and payout execution are separate paths. | Test provider signature, amount/currency/account correlation, callback replay, timeout, late success, refund/reversal, and failed payout compensation against wallet movement and status transitions. |

## Pesantren findings

The inspected `InMemorySantriWalletRepository` stores aggregate objects in a `ConcurrentHashMap`, keyed by wallet ID. `findBySantri` scans the values and returns the first matching wallet; the inspected repository interface does not itself declare a uniqueness guarantee for tenant + santri. `SantriWallet` holds a mutable in-memory transaction list, prevents spending beyond balance, and optionally enforces a daily limit. Its operations do not accept an idempotency key in the inspected domain API.

This is useful domain behavior, but not evidence of durable movement history, concurrency-safe spending, database uniqueness, or retry safety. Before using it as a production consumer of a shared wallet contract, inspect whether another production repository exists, define the actual funds flow and ownership/guardian authorization, and test daily-limit enforcement under concurrent spend attempts.

## Required verification matrix

| Test | Expected invariant |
|---|---|
| Same command and same idempotency key, sequential replay | One balance effect and one logical movement; same result or explicit duplicate result |
| Same key with different amount/operation payload | Deterministic conflict; never silently reinterpret the original command |
| Replay after aggregate ref-cache eviction and process restart | No second financial effect |
| Two concurrent debits/holds against one balance | No overspend beyond the configured policy; no duplicate sequence or inconsistent available balance |
| Fail after aggregate update but before ledger append | Entire transaction rolls back, or a documented durable recovery process repairs it |
| Fail after ledger append but before event delivery | Durable retry/outbox produces the required event without duplicating the movement |
| Cross-tenant lookup and write | Rejected/empty as designed; no data leakage |
| Missing or stale tenant transaction context | Fails closed and does not reuse prior pooled-connection tenant state |
| Payment callback replay/out-of-order delivery | Verified state machine; one authorized wallet credit/capture/reversal |
| Reconciliation against provider and accounting postings | Missing, duplicate and mismatched effects are detectable and repairable |

## Gate decision

**Do not extract the Mobility aggregate wholesale and do not claim exactly-once effects yet.** First trace the actual aggregate-document repository, unit-of-work, event sink/outbox and production runtime composition. Add integration tests around those real components. Then design the shared wallet's durable operation identity and movement constraints using the observed guarantees.

This audit does not prescribe a microservice. The same invariants must hold in an in-process modular monolith; remote deployment adds delivery/reconciliation failure modes but does not fix local persistence weaknesses.
