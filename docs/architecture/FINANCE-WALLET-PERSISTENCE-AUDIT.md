# Finance Wallet — Persistence and Replay-Safety Audit

- **Status:** Source inspection only; persistence guarantees not yet runtime-verified
- **Date:** 2026-10-26
- **Related decisions:** [ADR-006](ADR-006-finance-wallet-capability.md), [consumer inventory](FINANCE-WALLET-CONSUMER-INVENTORY.md)
- **Scope:** Mobility wallet ledger adapter/schema and Pesantren wallet repository available in the inspected tree

## Executive finding

The inspected source contains useful safeguards, including a tenant-scoped append-only PostgreSQL wallet ledger, deterministic entry IDs, transaction-aware ledger append comments, and optimistic-lock retry orchestration. However, the source inspection does **not** prove end-to-end exactly-once wallet effects or safe concurrency. The existing SQL ledger has uniqueness for `(tenant_id, entry_id)`, but the inspected table does not declare uniqueness for per-wallet `seq` or a separate operation key across all movement kinds. The Mobility aggregate keeps only a bounded recent set of refs. The Pesantren wallet adapter inspected is in-memory and does not provide durable persistence.

These findings are a design review, not a claim that production runtime behavior has been tested. The relevant Mobility persistence file itself labels `PgLedgerRepository` “NOT COMPILED in sandbox”.

## Mobility findings

### Positive controls found

1. `LedgerEntry.of(...)` derives a deterministic UUID from wallet ID, operation ref and entry kind.
2. `wallet_ledger` is keyed by `(tenant_id, entry_id)`; `PgLedgerRepository.append` uses `ON CONFLICT DO NOTHING`.
3. The migration installs a trigger rejecting UPDATE/DELETE and TRUNCATE on the ledger table.
4. The migration enables and forces row-level security with a tenant policy using `app.tenant_id`.
5. `PgLedgerRepository` wraps queries in `TenantTransaction.run(tenantId, ...)` and comments that ledger append joins the wallet transaction.
6. `WalletService` describes mutation, save, ledger append and event publication within a unit of work, with bounded retries for optimistic-lock conflicts.
7. The in-memory ledger repository uses a concurrent map and tenant-scoped entry key, which is useful for unit tests but is not a substitute for database behavior.

### Gaps and questions that require tests or schema verification

| Concern | Evidence from inspected source | Required follow-up |
|---|---|---|
| Lifetime idempotency | `Wallet` stores a bounded set of recent refs (`MAX_REFS = 500`); ledger entry ID includes ref **and kind**. | Define durable idempotency scope independent of the aggregate cache. Verify repeated operations after eviction/restart cannot apply a second balance change. |
| Operation identity across kinds | `wallet_ledger` primary key is `(tenant_id, entry_id)`; no separate unique constraint on `(tenant_id, wallet_id, ref)` is present in inspected V300. Because kind contributes to the deterministic ID, the same ref with a different kind can produce a different entry ID. | Decide whether a ref is allowed to be reused across kinds; enforce that rule at the command/operation registry layer if required. Test key reuse with different payloads and operation types. |
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
