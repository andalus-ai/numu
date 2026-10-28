# Affiliate & Partner Capability

This is a reusable, deployment-neutral affiliate capability, not a mandate to run a separate service.

## M2 — Core setup foundation

- Tenant-scoped affiliate program, partner, program membership, and referral-link lifecycles.
- Normalized referral codes and explicit tenant/actor/idempotency/correlation command context.
- Repository contracts require tenant-scoped lookups; concrete adapters must enforce tenant scope and uniqueness atomically.

## M3 — Attribution foundation

- Immutable, versioned attribution policies with first-touch and last-touch strategies.
- Deterministic selection from eligible touchpoints, bounded by a policy window and conversion timestamp.
- Immutable decisions record source correlation and policy version, and distinguish attributed from unattributed outcomes.
- Application orchestration checks for an existing decision, requires an authoritative qualified conversion, resolves the effective policy, loads a bounded touchpoint set, and uses atomic `saveOrGet` to converge concurrent replays.
- Source adapters must provide a verified qualifying conversion; client redirects and untrusted status payloads are not evidence.
- Decision repository contract specifies an atomic unique key and exact-replay behavior.

## Not yet production-complete

- Concrete persistence adapters and transaction isolation for uniqueness/concurrency.
- Durable ingestion/outbox, backfill and reconciliation jobs.
- Commission calculation, liability accounting, reversals, disputes, payout and provider execution.
- HTTP endpoints and partner/operator UI.
- Identity/tenant authorization adapter implementation, retention policy, and deployment-specific observability.

Those pieces must follow existing reactor/runtime conventions and have integration tests before they are considered production-ready. Do not expose raw personal identifiers as subject keys; use a tenant-scoped opaque/pseudonymous key under a documented privacy/retention policy.


## M4 — Commission calculation foundation

- Immutable, versioned commission plans support fixed amounts and percentage-of-eligible-base calculations.
- Commission calculations require a source adapter to provide a verified conversion, authoritative eligible amount/currency, and the stored attributed decision. Unattributed conversions cannot earn commission.
- Calculation snapshots preserve the conversion/source correlation, partner, attribution decision, plan ID/version, eligible base, currency, rounded amount, and idempotency key.
- Rounding uses the currency's default fraction digits and HALF_EVEN; the plan and eligible base must use the same currency.
- The repository contract requires atomic `saveOrGet` keyed by tenant and idempotency key, with conflicting reuse rejected.
- Reversals are separate negative entries linked to the original earning. Commission status transitions are validated in the domain model.

## Still required before production use

- Concrete persistence adapters, uniqueness constraints, optimistic concurrency/transaction semantics, and concurrent replay integration tests.
- Audit history for status transitions, authorization policies, event/outbox delivery, reconciliation, and operator workflows.
- Refund/chargeback source adapters must supply authoritative, uniquely identified reversal events.
- Finance ledger/liability posting, tax handling, payout eligibility, wallet/payment execution, and provider integrations remain separate follow-up work; commission entries alone do not move money.
- Partner-facing and operator-facing endpoints/UI, retention controls, observability, and end-to-end tests are not implemented by these domain/application contracts.


## M5 — Finance liability integration contract

- Approved commission earnings and approved reversals can enqueue a semantic Finance posting request; pending or held entries cannot be posted.
- The affiliate-owned outbox contract requires atomic idempotent enqueue and leased claims so retries and multiple workers can converge safely.
- A Finance adapter maps semantic liability requests to the configured chart of accounts and posts through Finance's public command/application boundary. Affiliate must not mutate Finance accounts or journal persistence directly.
- Every posting uses a stable tenant-scoped idempotency key. The Finance adapter must deduplicate that key independently because an outbox worker can time out after Finance has committed.
- Receipt validation checks tenant, idempotency key, currency, and exact numeric amount before the outbox is marked posted.
- Reconciliation queries Finance by the same idempotency key after ambiguous outcomes and marks the outbox posted only when the authoritative receipt matches.
- Payout execution remains outside this milestone. Approved liabilities are not evidence that a partner has been paid; Wallet/Payment integration must use its own payout lifecycle and provider idempotency.

## M5 implementation boundary and remaining adapter work

The repository now defines the application/SPI contracts and orchestration workflow, with unit tests for approval gating, outbox dispatch, receipt validation path, and reconciliation. Concrete durable outbox storage, lease/claim transaction implementation, Finance adapter/chart-of-accounts mapping, migration, audit event persistence, and PostgreSQL concurrency/integration tests are still required before production use.

## M6 — Partner payout orchestration

- A payout request is eligible only for an approved positive earning whose matching Finance liability outbox entry is already posted.
- Payout destination lookup is tenant- and partner-scoped; only active, verified destinations in the commission currency are eligible. Affiliate stores an opaque destination reference, never raw bank or wallet credentials.
- Payout intent uses a stable tenant-scoped idempotency key. The payout repository must atomically deduplicate requests and compare-and-set lifecycle updates.
- Wallet/Payment owns actual money movement. The affiliate adapter calls their public application contract and requires independent provider-side idempotency plus authoritative lookup by idempotency key.
- Payout states distinguish requested, submitted, paid, held, retryable failure, reconciliation required, and returned. A request or a provider acceptance is not proof of payment; only an authoritative confirmed receipt transitions to paid.
- Ambiguous submission failures are reconciled before retry to avoid duplicate transfers. Receipts are checked against tenant, idempotency key, currency, and exact amount.
- Commission reversals are not payout-eligible; a reversal changes the liability and must be handled by the Finance reconciliation process, not paid as a positive payout.

## M6 implementation boundary and remaining production work

This milestone adds the payout domain lifecycle and application/SPI contracts, including Wallet/Payment orchestration and destination verification boundaries. Concrete persistence/migrations, audit-event storage, operator approval and hold policies, settlement batch aggregation/scheduling, bank/wallet destination management, provider adapters, rate/fee/tax handling, and end-to-end integration/concurrency tests remain required before production use. Do not mark a payout paid based solely on a submission response or a locally updated record.
