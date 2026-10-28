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
