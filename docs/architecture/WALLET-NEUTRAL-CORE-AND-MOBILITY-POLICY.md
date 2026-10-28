# Finance Wallet Neutral Core and Mobility Policy Boundary

- **Status:** Implementation contract for the next wallet milestone
- **Scope:** `finance/wallet` and its first consumer, Mobility
- **Related:** [ADR-003: Reusable capability modules](ADR-003-reusable-capability-modules.md), [Capability Packaging and Composition Contract](CAPABILITY-PACKAGING-AND-COMPOSITION.md)
- **Repository:** `andalus-ai/numu`

## Purpose

Establish one reusable wallet capability that multiple business domains can consume without copying monetary state transitions or inheriting Mobility-specific commercial rules.

This document defines target ownership and acceptance criteria. It does not claim that all existing wallet modules or production guarantees already conform.

## Observed starting point

The current `finance/wallet` modules are:

- `numou-wallet-domain`
- `numou-wallet-spi`
- `numou-wallet-application`
- `numou-wallet-adapter`

The existing `Wallet` aggregate includes rider/driver/platform owner kinds, fare-capture shortfall behavior, and negative-balance/overdraft behavior. `WalletService` also has driver-overdraft, payout-minimum, and platform-sharding configuration. Those are useful Mobility behaviors, but they must not become mandatory semantics for every Finance wallet consumer.

The adapter's module description identifies it as an in-memory adapter for tests/dev. Production persistence and its transactional guarantees must be verified separately. The recent Finance reactor repair only establishes module discovery; it does not prove the wallet is neutral or production-ready.

## Ownership boundaries

### Neutral Finance Wallet owns

- Wallet identity scoped to tenant and legal/business owner references.
- Currency-aware monetary values and exact scale/rounding policy.
- Wallet lifecycle state, including active/frozen/closed where supported.
- Available, posted, and held amounts with explicitly documented semantics.
- Holds, capture against a hold, release, and reversal/adjustment references.
- Wallet-to-wallet transfers with atomic debit/credit semantics where both wallets share a transaction boundary.
- Durable operation identity, idempotency outcome, and conflict behavior.
- Auditable movement history and reconciliation hooks.
- Stable application ports and versioned integration contracts.

### Mobility owns

- Rider/driver/platform roles and their eligibility rules.
- Fare authorization, post-trip capture policy, and any fare shortfall that becomes a rider debt.
- Driver commission debt, cash-ride eligibility, and overdraft policy.
- Driver payout thresholds, ride settlement, platform subsidy/sharding strategy, and ride-specific ledger mappings.
- Ride lifecycle events and the decision about when a wallet operation is permitted.

Mobility can configure a wallet policy or orchestrate a wallet operation through the public contract. The neutral core must not infer these policies from an owner-kind enum.

### Adjacent Finance capabilities retain their ownership

- **Payment** owns provider attempts, provider references, external payment status, refunds, and settlement reconciliation with payment providers.
- **Accounting** owns general-ledger journals, chart of accounts, and financial reporting.
- **Business domains** own commercial eligibility, pricing, commissions, tax rules, and delivery/fulfillment decisions.

A payment success may cause a wallet credit through an idempotent application flow; it must not independently mutate wallet tables. A wallet movement and its accounting mapping must not create duplicate economic value.

## Core contracts and invariants

1. **Money is exact and currency-aware.** Use decimal/integer minor-unit arithmetic; never binary floating point. Currency and supported scale are explicit. Reject unsupported scale or apply only a documented rounding rule at a named boundary.
2. **Balance concepts are distinct.** Define posted balance, held amount, and available balance separately. A hold reserves value; it is not a posted debit. A release removes a reservation; capture consumes it according to the documented capture policy.
3. **Overdraft is opt-in policy.** The default wallet does not overdraw. A policy may authorize a bounded overdraft or a separate debt/receivable workflow. Unlimited overdraft must never be an accidental default.
4. **Every mutation has a durable operation identity.** A retry with the same tenant, wallet, operation reference, and identical canonical payload returns the original outcome without a second movement. Reusing the reference with a different payload is a conflict. Idempotency cannot depend on a bounded in-memory list of recent references.
5. **Posting is atomic.** Wallet state, durable movement/journal records, idempotency result, and outbox records commit or roll back together within the chosen transaction boundary.
6. **Concurrent spending is safe.** Two concurrent operations cannot spend or hold the same available funds twice. Enforce this with database constraints and optimistic/pessimistic concurrency appropriate to the persistence implementation, not only an in-process check.
7. **History is append-only in meaning.** Do not edit/delete a posted movement to correct it. Use linked reversals and adjustments that preserve the original record and actor/reason audit.
8. **Tenant and authorization context are explicit.** Every read and mutation is tenant-scoped; possession of a wallet ID is not authorization. Cross-tenant references must fail closed.
9. **Events are durable and replay-safe.** Persist events through an outbox or equivalent atomic mechanism. Event delivery may be at-least-once; consumers must be able to deduplicate safely.
10. **Remote and in-process contracts preserve semantics.** Switching between embedded use and a service adapter must not change authorization, tenant scoping, idempotency, or business outcomes.

## API shape (conceptual, not a claim about existing APIs)

Use explicit commands rather than one generic mutation method that obscures intent:

- `OpenWallet`
- `CreditWallet`
- `DebitWallet`
- `PlaceHold`
- `CaptureHold`
- `ReleaseHold`
- `TransferFunds`
- `FreezeWallet` / `UnfreezeWallet`
- `ReverseMovement`

Each command carries tenant context, wallet reference(s), currency/amount where applicable, a stable operation/idempotency key, actor/correlation context, and a reason or business reference where required. The application layer validates authorization and coordinates persistence; domain rules remain framework-neutral.

Avoid putting every industry's lifecycle into a universal status enum. Use small shared lifecycle states plus policy/extension contracts where the meanings are genuinely common.

## Migration sequence

### M1 — Neutralize the contract without breaking Mobility

- Inventory all wallet call sites, events, migrations, tests, and the Mobility persistence adapter.
- Add neutral contract tests for money, holds, capture/release, idempotency, and freeze state.
- Replace direct dependence on `OwnerKind.RIDER/DRIVER/PLATFORM` in neutral wallet identity with stable owner type/reference semantics only after call sites and persistence compatibility are mapped.
- Extract fare shortfall, booking eligibility, driver overdraft, and payout eligibility into Mobility policy/orchestration.
- Preserve current Mobility behavior with characterization tests before changing production code.
- Do not perform a broad package rename or introduce a new wallet implementation as part of this milestone.

### M2 — Prove durable persistence guarantees

- Run the wallet integration suite against the real PostgreSQL adapter and transaction manager used by the application.
- Verify atomic wallet state + movement + idempotency + outbox commit/rollback.
- Verify same-key replay, same-key/different-payload conflict, and simultaneous debit/hold races.
- Verify tenant isolation and optimistic-lock retry behavior.
- Make the production persistence adapter explicit in module names and composition; keep in-memory storage test-only unless a documented dev profile selects it.

### M3 — Prove cross-domain reuse

- Migrate Mobility as the compatibility consumer first.
- Integrate one second consumer, selected from marketplace/merchant settlement or courier/provider earnings, using only the neutral public contract.
- Keep commissions, settlement timing, customer eligibility, and payout decisions inside each owning domain.
- Demonstrate that the second consumer requires no fork of wallet monetary state transitions.

## Required tests and evidence

### Domain tests

- Currency/scale validation and exact arithmetic.
- Negative, zero, null, and over-precision amounts are rejected consistently.
- Holds cannot exceed available funds under the default policy.
- Capture and release have explicit behavior for unknown holds and repeated operation keys.
- Same key/same payload is a no-op replay; same key/different payload is a conflict.
- Freeze restrictions are consistent across every mutation.
- Reversal is linked to the original operation and cannot reverse it twice.

### PostgreSQL integration tests

- Two concurrent holds/debits cannot overspend available funds.
- Failed posting leaves no wallet, movement, idempotency, or outbox partial state.
- Transaction retries do not duplicate movements or events.
- Tenant A cannot read or mutate Tenant B's wallet.
- A restarted process can safely replay a previously committed operation.
- Outbox dispatch failure and recovery do not lose events or create duplicate economic movements.

### Compatibility and architecture tests

- Existing Mobility rider, driver, and platform behavior remains covered.
- Neutral domain packages do not depend on HTTP, CDI, PostgreSQL, provider SDKs, or Mobility packages.
- Consumer modules depend only on public wallet contracts, not wallet persistence internals.
- The Finance Maven reactor and affected dependent reactors pass in CI.

## Definition of done

M1 is complete only when the neutral API and ownership boundary are reflected in code, tests, and dependency rules; Mobility-specific policy is no longer mandatory behavior of the neutral core; and compatibility tests pass.

The wallet must not be described as production-ready until the PostgreSQL concurrency, transaction, replay, tenant-isolation, and outbox criteria above have recorded passing evidence.
