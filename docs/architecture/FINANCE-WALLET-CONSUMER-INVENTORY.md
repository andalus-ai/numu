# Finance Wallet Extraction — Consumer and Contract Inventory

- **Status:** Initial source-tree audit; not a production-readiness assessment
- **Date:** 2026-10-26
- **Related decision:** [ADR-006 — Reusable finance wallet capability](ADR-006-finance-wallet-capability.md)
- **Repository:** `andalus-ai/numu`

## Purpose and method

This inventory records concrete wallet-like and finance-ledger implementations found in the current source tree. It distinguishes similarly named concepts from semantically interchangeable capabilities. It is the evidence gate before creating `finance/wallet`; it does not claim exhaustive repository-wide dynamic analysis, successful builds, or production verification.

The repository's `AGENTS.md` and ADR-003 require inspection of real Maven reactors and contracts, framework-independent domain behavior, explicit capability ownership, compatibility-preserving changes, and honest verification claims. The Finance aggregator is `finance/pom.xml` and currently lists accounting, billing, common, payment, subscription, Syirkah, and tax; it does **not** currently include a wallet module.

## Inventory findings

| Existing area | Observed responsibility and contract | Reuse assessment | Decision |
|---|---|---|---|
| `mobility/wallet/mobility-wallet-domain` | `Wallet` aggregate has currency/fraction digits, balance, holds, overdraft limit, frozen state, owner kinds, operation references, and debt-tolerant post-service capture/shortfall. `LedgerEntry` models credit/debit/hold/capture/release and per-wallet sequence. | Strong source for candidate primitives, but its aggregate embeds Mobility owner kinds and ride economics. | Keep as compatibility reference and policy owner during extraction; do not move unchanged. |
| `mobility/wallet/mobility-wallet-application` | `WalletService` orchestrates wallet mutation, ledger append, event publication and bounded optimistic-lock retries in a unit of work. Top-up/payout/payment provider workflows are present. | Useful behavioral evidence; application orchestration and provider integration are not a neutral wallet domain. | Extract only stable contracts after inspecting persistence transaction and idempotency guarantees. |
| `mobility/wallet/mobility-wallet-spi` + adapter | Repository, ledger repository, payment gateway and payout ports; in-memory adapters exist. | Ports show separation seams, but production guarantees depend on actual persistence adapters and database constraints. | Audit schema/SQL implementation before asserting durable idempotency or atomicity. |
| `education/school-pesantren/pesantren-domain` | `SantriWallet` represents santri pocket money; tenant/santri ownership, non-negative balance, optional daily spend limit, deposits/spends, in-memory transaction history and domain events. `WalletTransaction` has type, amount, date and note. | A real second wallet-like use case, but its semantics are narrower and materially different: no holds, capture, payout, overdraft or explicit operation idempotency in the inspected aggregate. It is not yet proof that a shared financial kernel fits unchanged. | Treat as a candidate consumer for a future adapter only if the school product's funds flow and persistence/audit requirements justify it. Do not migrate automatically. |
| `finance/accounting/numou-accounting-domain` | `Ledger` is a tenant-aware set of books with base currency and compliance standard. Accounting also has journal-entry, reporting and general-ledger services. | Accounting books and balanced journals are not a spendable customer wallet or operational hold ledger. | Integrate wallet movements to accounting via posting/reconciliation ports; never substitute the accounting ledger aggregate for a wallet. |
| `finance/payment` | Finance payment reactor includes gateway, method, core, merchant, gRPC, REST and processor modules; `numou-payment-ewallet` includes an `EWalletProcessor`. Payment core inspected at the reactor level includes framework/infrastructure dependencies. | Payment method/provider execution is adjacent to, not equivalent to, wallet balances and movements. | Keep provider state and callback lifecycle in Payment. Do not call an e-wallet payment processor the shared wallet capability. |
| `finance/syirkah` | Syirkah owns contractual capital, profit/loss allocation, partner movements, close snapshots and posting plans; README states its movements map to balanced journal entries. | Contractual participant entitlements and profit/loss allocation are domain-specific accounting invariants, not generic wallet transfers. | Keep Syirkah calculations and legal/contractual rules in Syirkah. Wallet integration, if needed, must be a narrow settlement boundary. |
| `education/school-pesantren/pesantren-application` | Deposit handler opens on first use and deposits; spend handler loads and spends. Handlers check tenant/santri identity and active status in the inspected paths. | Application boundary evidence only; this does not establish durable idempotency, concurrency control, database uniqueness, or atomic transaction behavior. | Verify its persistence adapter and command semantics before considering a production consumer. |

## Cross-cutting gaps to resolve before extraction

1. **Durable idempotency:** Mobility's aggregate comment documents a bounded recent-reference set (`MAX_REFS = 500`). This is not sufficient by itself for lifetime replay protection. Inspect ledger unique constraints, operation-key scope, retention, and recovery after restart.
2. **Atomic mutation:** prove balance update and movement append are atomic; inspect optimistic locking, database isolation, unique constraints and retry behavior under concurrent commands.
3. **Tenant and authorization:** establish tenant/account scoping for every command and idempotency key; verify authorization is enforced by application boundaries, not only by the caller.
4. **Money rules:** document currency code validation, supported fraction digits, rounding, zero/negative amounts, maximum precision, and whether each amount is transaction currency or account currency.
5. **Movement semantics:** distinguish available balance, posted balance, holds, captures, releases, reversals, pending provider transactions and completed settlement. Do not assume all consumers need every operation.
6. **Event reliability:** establish transactional outbox/inbox or an equivalent guarantee for movement events, payment callbacks and downstream journal posting.
7. **Audit and reconciliation:** define source references and procedures for missing/duplicate movements, provider mismatches, reversals, failed payouts and accounting-posting drift.
8. **Legal/product profile:** document the actual funds flow and required KYC/KYB, safeguarding, withdrawal, limits, dispute, retention and reporting controls for each host product with qualified compliance review.

## Candidate shared contract — not yet an API specification

A minimal first design review should decide whether these concepts are truly common:

- Account identity: tenant + stable account ID + external owner reference; no mandatory Mobility `OwnerKind`.
- Money: validated currency and explicit precision/rounding policy.
- Read model: posted balance, held amount and available balance with clearly defined equations.
- Commands: credit, debit, hold, capture, release and reversal only where supported, each with caller-supplied idempotency context.
- Movement record: immutable identity, account, signed/typed amount, currency, operation type, source reference, actor/correlation, effective/recorded time, sequence/version and audit metadata.
- Policy seam: explicit rules for overdraft/shortfall, spending limits, holds, withdrawal, expiry and account restrictions; no universal default that enables riskier behavior.
- Ports: repository/transaction boundary, authorization context, durable operation registry, event delivery and reconciliation integration.

These items are discussion candidates, not a committed universal aggregate. Split contracts where lifecycle or invariants differ.

## Recommended implementation gates

### Gate A — persistence and behavior audit (next)

Inspect all Mobility wallet adapter SQL/migrations and repository implementations, plus Pesantren wallet persistence and tests. Trace command -> domain -> save -> ledger/event path. Record unique keys, transaction boundaries, optimistic locking, tenant constraints and recovery behavior. Run the owning Maven reactors if the environment is available and report exact results.

### Gate B — domain contract and invariant tests

Write a framework-free contract proposal and tests for currency/amount validation, holds, capture/release, duplicate command behavior, concurrent mutation expectations, freeze/restriction and movement ordering. Do not add persistence or remote APIs until the domain contract is stable.

### Gate C — durable adapter and migration

Add a new wallet reactor only after reactor conventions, migration ownership and package/API compatibility are reviewed. Implement durable movement uniqueness, transaction/locking guarantees, audit and migration strategy. Keep Mobility behind an adapter and preserve current policy with explicit tests.

### Gate D — second consumer proof

Use the Pesantren wallet as a candidate only after deciding whether pocket-money operations belong on the shared wallet capability and how daily limits, tenant scoping, parent/guardian permissions, refunds/corrections and audit history work. If semantics do not fit, retain its domain model and reuse only a narrow contract or shared money primitives.

### Gate E — production readiness

Test persistence restart, duplicate and out-of-order callbacks, concurrent credit/debit/hold, rollback, tenant isolation, authorization, reconciliation and failure recovery. Verify Accounting posting separately from wallet movement. Do not claim production readiness based on unit tests alone.

## Explicit non-goals

- Moving Mobility's `Wallet` class wholesale.
- Replacing Accounting's general ledger or Syirkah's contractual ledger.
- Treating payment provider state as wallet balance.
- Forcing all domains to support overdraft, holds, withdrawal or stored-value behavior.
- Creating a wallet microservice by default; an in-process capability is the baseline.
- Claiming that the initial source-tree review proves builds, database guarantees or legal compliance.
