# ADR-004: Affiliate program as a reusable business capability

- **Status:** Proposed for implementation
- **Date:** 2026-10-28
- **Decision owners:** Numou platform maintainers
- **Scope:** Reusable affiliate/referral programs for Numou applications

## Context

Numou is a capability-first business platform. Affiliate programs are a reusable commercial capability: products may use them to acquire customers, partners may refer prospects, and organizations may configure their own programs. The capability must not be coupled to a specific product, storefront, payment provider, identity provider, or deployment topology.

An affiliate program is more than a referral code. It needs attribution, configurable commission rules, a traceable ledger, lifecycle controls, and fraud-resistant payout workflows. The repository's actual Maven module layout and existing payment/identity contracts must be inspected before selecting artifact paths or adding dependencies.

## Decision

Introduce an optional Affiliate Program capability, composed into products that need it. Preserve one authoritative owner for attribution and commission state. Integrate with existing identity, order/subscription, payment, and finance capabilities only through their documented public contracts.

Do not introduce a second payment processor or duplicate the finance ledger. Affiliate calculates and records commission obligations; a payment/finance capability remains responsible for payment execution and accounting posting.

## Domain responsibilities

### Affiliate profile and enrollment

- Enroll an eligible person or organization as an affiliate within a tenant/program.
- Give each affiliate a stable ID and a unique, human-friendly referral code scoped according to explicit policy.
- Track pending, active, suspended, and terminated states with audited transitions.
- Support terms acceptance, effective dates, and required compliance metadata without embedding provider-specific identity details.

### Program and commission policy

- Configure program status, eligibility, attribution window, eligible products/events, commission basis, rate or fixed amount, currency, caps, and effective dates.
- Support a deliberate initial policy set (for example, percentage of eligible net sale and fixed amount per qualifying conversion); avoid arbitrary executable formulas.
- Version policies. A conversion must retain the policy/rate snapshot that applied when it qualified.
- Keep money in currency-aware decimal/minor-unit representations consistent with Numou finance contracts; never use floating-point arithmetic for monetary amounts.

### Referral attribution

- Record referral touchpoints and attribution decisions with tenant, program, affiliate, timestamp, source, and a privacy-conscious visitor/customer reference.
- Make attribution strategy explicit (initially first-touch or last-touch, selected per program) and enforce the configured attribution window.
- Define deterministic conflict handling, self-referral rules, cookie/identifier consent, and duplicate-event behavior.
- Do not treat possession of a referral code or an affiliate ID as authorization.
- Avoid storing raw IP addresses or unnecessary device fingerprints by default.

### Conversion qualification and commission lifecycle

- Consume a stable order/subscription/payment-success contract or an explicit product-owned conversion command.
- Make conversion ingestion idempotent using a source event/transaction key.
- Apply eligibility and policy snapshots once, then create a traceable commission record.
- Model at least pending, approved, reversed, and paid states; include rejected/voided outcomes if the existing lifecycle conventions require them.
- Handle cancellations, refunds, chargebacks, and partial refunds with explicit reversal/adjustment entries. Never silently rewrite historical commission amounts.
- Keep a complete audit trail and support reconciliation against the source transaction.

### Payouts

- Track payable balances and payout requests/approvals, minimum thresholds, payout status, and references to external finance/payment transactions.
- Delegate actual money movement to the existing finance/payment capability through a port.
- Do not store bank credentials or payment-provider secrets in affiliate domain objects.
- Require authorization, separation of duties where configured, idempotency, and reconciliation for payout execution.

### Reporting and operations

- Expose tenant-scoped affiliate, referral, conversion, commission, and payout summaries through application queries.
- Record reason codes for denied attribution, ineligible conversions, reversals, and payout failures.
- Publish versioned domain/application events where the repository's event/outbox facilities support reliable delivery.

## Architecture and dependency rules

- Keep domain invariants pure Java and independent of HTTP, CDI, persistence frameworks, brokers, and payment vendors.
- Place use cases and ports in the capability application layer; put database and HTTP/messaging integrations in adapters following established Numou module conventions.
- Depend on public contracts for tenant/actor context, source conversions, and finance/payment execution. Never import another capability's persistence entities or tables.
- Keep the capability optional: unrelated Numou consumers must not gain affiliate or payment dependencies transitively.
- Support embedded, modular-monolith, and distributed compositions with equivalent business semantics.
- Enforce tenant isolation and authorization at every application boundary.
- Use durable outbox/inbox or the repository's equivalent where conversion events and commission creation cross transaction boundaries.

## Initial implementation milestones

1. **Repository fit and contract audit:** identify the existing tenant/identity, money, order/subscription, payment/finance, event/outbox, persistence, API, and test conventions. Select artifact paths only after this inspection.
2. **Domain and application core:** affiliate/program/policy models, referral attribution, conversion qualification, commission ledger, lifecycle invariants, idempotency keys, and unit tests.
3. **Persistence and event adapters:** migrations/repositories and reliable event ingestion using the existing persistence and event patterns.
4. **Product/API integration:** authenticated affiliate enrollment and reporting, referral capture, conversion adapter, and payout integration via existing public contracts.
5. **Verification and operations:** tenant-isolation, concurrency, duplicate delivery, refund/reversal, policy-versioning, authorization, architecture-boundary, migration, and integration tests; documentation and observability.

Do not claim the capability is complete until the relevant modules, adapters, tests, and at least one end-to-end conversion-to-commission workflow are implemented and verified.

## Acceptance criteria

- An affiliate can be enrolled in an active program and receive a valid referral code.
- Referral attribution is deterministic, tenant-scoped, policy-driven, and bounded by the configured window.
- A qualifying conversion produces exactly one commission obligation despite retries or duplicate events.
- The applied commission policy and source transaction are auditable and immutable as historical facts.
- Refund/cancellation events create correct reversal or adjustment entries without deleting the original record.
- Payout requests are authorized and idempotent, and execution is delegated to finance/payment rather than duplicated.
- Cross-tenant reads/writes are rejected; domain tests run without infrastructure.
- Capability boundaries are checked by architecture tests and optional dependencies remain optional.
- Relevant build/test commands and results are recorded truthfully in the implementation PR.
