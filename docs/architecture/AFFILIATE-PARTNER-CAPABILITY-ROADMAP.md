# Affiliate & Partner Capability Roadmap

- **Repository:** `andalus-ai/numu`
- **Status:** Proposed architecture and implementation plan; code delivery is incremental
- **Scope:** Reusable affiliate, referral, and partner commission capability
- **Initial market profile:** Indonesia, with configurable localization and later regional/enterprise deployment
- **Related architecture:** [AGENTS.md](../../AGENTS.md), [ADR-003](ADR-003-reusable-capability-modules.md), [Capability ownership matrix](DOMAIN-CAPABILITY-MATRIX.md)

## 1. Objective

Provide a reusable capability for businesses that acquire customers, orders, subscriptions, bookings, leads, or completed services through affiliates and other partners. It must cover the full auditable lifecycle from program configuration and referral attribution through commission approval, payable creation, settlement, reversal, and reconciliation.

The capability is not limited to e-commerce referral links. Potential consumers include marketplace sellers, SaaS products, hospitality bookings, automotive/service businesses, courier/logistics, professional services, and B2B lead/referral programs. Each consumer integrates through an explicit adapter and retains ownership of its order, booking, subscription, service completion, refund, and customer lifecycle.

This document is a target roadmap, not evidence that these capabilities currently exist or pass CI.

## 2. Architecture decision

Treat Affiliate & Partner as a business capability, not automatically a microservice or a universal marketing module.

- **Affiliate core owns:** programs, partner enrollment and status, referral identifiers, attribution policy and decisions, commission rules and immutable commission records, eligibility state, disputes, and payout-batch orchestration.
- **Consumer domain owns:** source transaction lifecycle and facts such as order accepted, booking completed, subscription paid, service fulfilled, refund, cancellation, chargeback, and source transaction identity.
- **Finance owns:** accounting classification, payable/journal integration, period controls, and reconciliation.
- **Payment execution owns:** payment-provider operations and verified provider outcomes.
- **Wallet owns, where available and appropriate:** balance movements and transfer semantics. Affiliate must not create a parallel wallet or treat a commission record as a wallet balance.
- **Identity/authorization owns:** authentication and permission primitives. Affiliate applies its program- and tenant-specific authorization rules.
- **Workflow/messaging/analytics are optional integrations:** no mandatory dependency on their runtime or database.

Use public application ports for in-process composition and versioned API/event contracts for distributed composition. The same domain rules must work embedded in an application, in a modular monolith, or behind a separately deployed service. Do not split into a service until operational needs justify it.

Before creating code, inspect the actual repository tree, Maven reactor roots, domain ownership matrix, existing Commerce/Promotion/CRM/Finance/Wallet/Payment/Workflow contracts, tests, and applicable `AGENTS.md` files. Reuse only verified public contracts; do not assume similarly named modules have production-ready capabilities.

## 3. Core domain model

Names below are conceptual and must be reconciled against existing repository conventions before implementation.

- **AffiliateProgram:** tenant/owner, name, status, eligible source types, supported markets/currencies, effective period, attribution and disclosure policies.
- **Partner:** durable partner identity/reference, profile and contact references, lifecycle, risk/review status, payout eligibility and required compliance evidence references. Avoid duplicating identity-provider or customer master records.
- **ProgramMembership:** partner's participation in a program, approval, effective dates, assigned terms, and suspension/termination history.
- **ReferralCode / ReferralLink:** unique public identifier, campaign metadata, destination/reference, lifecycle, and optional expiry. Never embed secrets or trusted commission amounts in the URL.
- **Touchpoint:** minimal, purpose-limited referral observation with timestamp, campaign, pseudonymous visitor/session reference where permitted, and consent/provenance metadata as applicable.
- **AttributionDecision:** immutable decision connecting a qualified source event to an eligible partner under a named, versioned policy; records evidence, decision time, policy version, and reason.
- **CommissionPlan / CommissionRule:** versioned fixed, percentage, tiered, recurring, or product/service-specific terms with effective dates, eligible base, caps/floors, currency, rounding, and exclusions.
- **CommissionEntry:** immutable source-linked financial obligation candidate with amount, currency, rule version, calculation snapshot, lifecycle, and idempotency key.
- **CommissionAdjustment:** explicit positive or negative adjustment linked to the original entry and reason; never silently rewrite history.
- **PayoutBatch / PayoutInstruction:** reviewed set of payable entries, approval trail, idempotency/correlation references, status, and settlement/reconciliation references. Provider execution remains outside the affiliate domain.
- **Dispute / RiskCase:** evidence and decisions for attribution disputes, self-referrals, suspicious activity, duplicate conversions, and reversals.
- **AuditRecord:** actor, tenant, action, timestamp, correlation, and before/after references, using the repository's established audit primitives where suitable.

Do not force multi-level/network marketing into the initial affiliate model. A later partner hierarchy or reseller extension requires a separate decision, explicit policy, and jurisdictional review.

## 4. Lifecycle and invariants

### Partner and program
- Explicit draft, active, paused, and retired program lifecycle with validated transitions.
- Partner approval, suspension, and termination do not erase historical attribution or commissions.
- Program and commission-rule changes are effective-dated and versioned. Existing commission calculations retain their original rule snapshot.
- Tenant and program ownership are checked at every application boundary; opaque IDs are not authorization.

### Tracking and attribution
- Treat browser tracking as an untrusted signal, not proof of a payable conversion.
- Define supported models as policy (for example last eligible click, first eligible click, or explicit coupon/code attribution); define tie-breaking, attribution window, direct-traffic behavior, and cross-device limitations.
- Bind conversion to an authoritative consumer-domain event and stable source transaction identifier.
- Make ingestion and attribution idempotent under duplicate delivery, retry, concurrency, and out-of-order events.
- Define precedence for coupon vs click, partner self-referral, conflicting referrals, existing customers, and cross-program eligibility.
- Minimize personal data; make consent, retention, deletion/pseudonymization, and tracking configuration explicit per deployment and jurisdiction.

### Commission and money
- Calculate only from defined eligible bases. State explicitly whether tax, delivery, discounts, refunds, fees, and partially fulfilled quantities are included.
- Use decimal/money primitives and currency-specific precision/rounding from established finance contracts; never use binary floating point for money.
- Store the calculation inputs and rule version so a result can be explained and reproduced.
- Separate pending, approved/eligible, payable, paid, reversed, disputed, and rejected states as explicit transitions. A click or conversion does not automatically mean payable funds.
- Model refund/cancellation/chargeback as linked adjustments or reversals with a defined policy, including post-payout recovery; never delete the original entry.
- Payout eligibility must consider settlement delay, minimum threshold, partner status, disputes, compliance review, and available finance/payment capabilities.
- Do not invent a universal wallet, deposit, escrow, interest, or withdrawal policy. Host/product configuration and applicable legal review govern these behaviors.

### Reliability and security
- Tenant-scoped unique constraints and idempotency keys at source-event ingestion, attribution, commission calculation, payout batching, and adapter boundaries.
- Use durable outbox/inbox or equivalent mechanisms when event delivery crosses transaction boundaries; retries must not double-create commissions or pay twice.
- Protect public referral endpoints against enumeration, injection, open redirects, spoofed conversion callbacks, replay, bot abuse, and rate-limit exhaustion.
- Require authenticated, authorized, audited administrative actions; apply least privilege and separation of duties to commission changes and payout approvals.
- Keep secret material and provider credentials out of domain objects, URLs, logs, and audit payloads.
- Make failures observable with correlation IDs, retry/dead-letter handling where applicable, reconciliation reports, and operator-visible terminal states.

## 5. Integration contracts

Define small ports only after reviewing actual contracts. Expected conceptual boundaries:

- **Conversion source port:** consumes verified source facts/events with tenant, source type, source transaction ID, lifecycle state, amount/currency snapshot where applicable, event ID/version, occurred-at time, and correlation metadata.
- **Finance payable port:** requests a payable or equivalent finance-owned record after commission approval; returns a stable reference and explicit result. Affiliate does not write finance tables.
- **Settlement port:** requests settlement through the selected payment/wallet/finance integration; returns status and reconciliation references. Provider callbacks must be verified by the provider adapter.
- **Consent/identity/audit ports:** optional and implemented using existing platform contracts if verified.
- **Reporting read model:** derives partner/program performance from authoritative records; analytics is not the source of truth for money.

Use versioned domain events only where there is a real consumer and a defined delivery guarantee. Candidate events include PartnerApproved, AttributionRecorded, ConversionQualified, CommissionAccrued, CommissionApproved, CommissionReversed, PayoutBatchApproved, and PayoutSettled. Event names and payloads must be finalized alongside actual transaction and outbox semantics.

## 6. API and user experiences

Initial API surface, subject to repository conventions:
- Public: resolve referral code/link and record allowed referral touchpoint; never expose partner private data.
- Consumer adapter: submit/consume authoritative conversion lifecycle events idempotently.
- Partner portal: program applications, referral links/codes, qualified conversions, commission breakdown, payable/paid history, dispute submission, and required profile/compliance status.
- Operator portal: program/rule versioning, partner review, attribution/commission investigation, dispute handling, payout batch review/approval, and reconciliation.
- Reporting: clicks/touchpoints, qualified conversions, conversion rate, gross/eligible basis, accrued/approved/reversed commissions, payout aging, and source freshness, with definitions, time windows, and currency stated.

Public tracking and partner reporting must not reveal other partners' data or raw personal visitor data. Reporting filters and exports must be tenant-scoped and audited when sensitive.

## 7. Indonesia-first readiness

Treat Indonesia as an initial localization profile, not hard-coded domain logic. Validate with qualified legal/tax/accounting reviewers before production use:
- partner identity/business verification and evidence handling appropriate to the business model;
- tax documentation, withholding/reporting obligations, invoicing, and accounting treatment;
- privacy and electronic-system obligations, lawful tracking/consent, retention, and cross-border data processing;
- payout methods, currency/rounding, reconciliation, disputes, and fraud controls;
- clear partner terms, required advertising/referral disclosures, prohibited claims, and termination/commission-tail rules.

Do not claim legal or tax compliance merely because fields or configuration options exist. Keep jurisdiction-specific policy and documents versioned and reviewable.

## 8. Milestone plan

### M0 — Architecture and repository discovery (this milestone)
- Record ownership, target domain model, invariants, integration boundaries, and milestone acceptance criteria.
- No speculative production code and no claims that existing finance, wallet, CRM, commerce, or tracking implementations are sufficient.
- Acceptance: architecture plan is reviewed against AGENTS.md and ADR-003; open repository questions and verification requirements are explicit.

### M1 — Existing-capability audit and public contract
- Inspect repository tree/reactors and existing Promotion, Commerce/Marketplace, CRM, Finance, Wallet, Payment, Identity, Audit, Events/Outbox, Workflow, and analytics contracts.
- Decide whether the affiliate capability belongs under an existing commerce/partner boundary or a dedicated reusable affiliate capability based on actual ownership evidence.
- Define module/artifact roles, public domain/application ports, tenant/actor/correlation context, money semantics, event contracts, and architecture tests.
- Acceptance: dependency map and contract tests; no dependency on private persistence entities; no duplicate finance/wallet ownership.

### M2 — Partner/program and referral foundations
- Implement program and membership lifecycle, referral codes/links, authorization, tenant isolation, schema/migrations, audit and idempotent referral resolution.
- Acceptance: domain tests, persistence/concurrency/tenant tests, migration compatibility, API security tests, and no secret or personal data leakage.

### M3 — Authoritative attribution
- Implement versioned attribution policies, touchpoint ingestion and source conversion adapter, deterministic conflict resolution, replay protection, and explainable immutable decisions.
- Acceptance: duplicate/out-of-order/concurrent delivery tests; click/coupon conflict tests; consent/retention policy tests; source-domain lifecycle remains authoritative.

### M4 — Commission ledger and lifecycle
- Implement effective-dated commission rules, deterministic money calculations, calculation snapshots, immutable entries and adjustments, refund/cancellation/chargeback reversals, disputes, and eligibility state machine.
- Acceptance: currency/rounding, boundary, partial refund, replay, concurrent calculation, rule-change, and post-payout recovery tests.

### M5 — Finance and settlement integration
- Integrate through verified finance payable and selected settlement contracts; implement payout batching, approval separation, idempotent settlement requests, reconciliation and failure recovery.
- Acceptance: no direct cross-capability writes, no double payout on retries, audit trail, reconciliation coverage, and integration tests for partial failures.

### M6 — Partner/operator experience and reporting
- Build partner and operator workflows, referral assets, commission explanations, disputes, payout history, exports, performance reports and alerting.
- Acceptance: authorization and tenant isolation across every endpoint/query/export; metric definitions and data freshness documented.

### M7 — Risk, compliance and production readiness
- Add risk signals/rules, abuse throttling, review queues, privacy/retention controls, jurisdiction profiles, observability, backups/restore, performance/load testing, runbooks and rollback.
- Acceptance: threat model, penetration/security checks appropriate to deployment, reproducible builds, migration/rollback rehearsal, reconciliation drills, and green required CI. Production readiness is not claimed until verified.

## 9. Cross-cutting acceptance gates

Every implementation milestone must:
1. Follow all applicable `AGENTS.md` instructions and inspect actual contracts before coding.
2. Preserve capability ownership and support embedded, modular-monolith, and distributed compositions without duplicated business logic.
3. Include unit tests for domain invariants and invalid transitions; application, persistence, architecture, and adapter tests where relevant.
4. Enforce tenant isolation, authorization, idempotency, audit, concurrency safety, and data minimization.
5. Document public API/event compatibility, schema migration and rollback implications.
6. Run the narrowest affected Maven reactor first, then relevant dependent reactors and CI where practical; report actual results, failures, skips, and environment limits.
7. Avoid unrelated changes and use milestone-sized pull requests rather than a PR for each small edit.
8. Never merge without explicit user authorization, and never claim tests or production readiness without evidence.

## 10. Open decisions before M1 implementation

- Which existing repository contracts can safely represent partner identity, money, audit, outbox, finance payables, and settlement?
- What source transaction types and conversion events already exist, and how are refunds/cancellations represented?
- Which attribution models and partner terms are required for the first real product?
- Which privacy/consent and retention policies apply to the intended first deployment?
- Which payout methods and accounting/tax flows will the first Indonesian deployment actually support?
- What repository reactor and CI commands cover the selected capability and its consumers?

Resolve these through repository evidence and product requirements, not speculative dependencies or hard-coded assumptions.
