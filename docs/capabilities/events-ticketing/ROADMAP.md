# Events and Ticketing Capability Roadmap

**Repository:** `andalus-ai/numu`  
**Status:** Roadmap and acceptance contract; not evidence that implementation or CI is complete.  
**Initial market:** Indonesia, with locale, currency, tax, payment methods and deployment choices configurable.

## Objective

Build a reusable event publishing, ticket product, booking, payment orchestration, ticket issuance and admission capability for conferences, concerts, classes, community events, venues, attractions, and online/hybrid events. It must work as a standalone application, embedded capability in a modular monolith, or versioned API in a distributed deployment.

The event capability owns event publication, ticket products, inventory allocation, booking intent, attendee assignment, ticket issuance, admission and event-specific cancellation rules. It must not become a second payment ledger or duplicate a generic reservation engine.

## Required reconciliation before code changes

Read root and nested `AGENTS.md`, relevant ADRs, capability matrix, Maven reactor roots, migrations and current PR/CI status. Inspect these boundaries:

- **Transport:** current work includes departure/segment capacity and passenger booking. Reuse only for actual scheduled passenger journeys; concert seating and admission are not journey segments. A capacity hold is not a ticket.
- **Payment:** consume the public, deployment-neutral payment application contract, never payment entities, repositories or provider SDKs directly. PR #763 describes a tenant-scoped PostgreSQL idempotency adapter; verify merge and CI before relying on it. Payment authorization, capture, provider execution, verified callbacks, refund settlement and reconciliation are distinct states.
- **Reservation:** reuse only where resource/time-slot semantics fit. Do not force ticket tiers, assigned seats or admission entitlements into a lossy generic model.
- **Finance, identity/customer, messaging, files, audit and outbox:** integrate via documented ports and versioned contracts. Never write another capability's tables directly.

PRs #677, #683 and #727 were reported as transport booking workflow, expiry worker and authenticated passenger API. Verify their current status instead of assuming they are merged or production-ready.

## Capability scope

### Event catalogue and publishing
Own event identity, tenant/organizer references, venue or online delivery reference, timezone, schedule, sales windows, localized content, accessibility information and publication lifecycle. Support draft, review, published, postponed, cancelled and completed states with explicit transition rules. Snapshot buyer-accepted terms and order pricing; published edits must not silently rewrite historical purchases.

### Ticket products and inventory
Support general admission, ticket tiers, complimentary/invitation tickets, quantity and per-order limits, sales windows, capacity pools, optional seat maps/seat holds, promotion allocations and audited organizer overrides. General admission and assigned seating are different inventory strategies. Capacity must be enforced atomically under concurrency; never rely on read-then-write checks that can oversell.

### Booking and checkout
Keep booking/order snapshots separate from payment and ticket entitlements. Define explicit transitions such as `DRAFT -> HELD -> PAYMENT_PENDING -> CONFIRMED`, with `EXPIRED`, `CANCELLED`, `PAYMENT_FAILED` and `REFUND_PENDING` outcomes where policy requires them.

- Require tenant-scoped idempotency keys and canonical request fingerprints for mutating commands.
- Persist capacity holds and expiry deadlines; on conflict reload and re-evaluate invariants.
- A redirect return URL/browser success page is not payment proof.
- Verify webhook signature/provider identity, amount, currency, merchant account, reference and allowed state transition.
- Use transactional outbox/inbox for reliable side effects; consumers must tolerate at-least-once delivery.
- Define compensation for late payment success after expiry, duplicate/out-of-order callbacks, cancellation races and partial failures. Escalate irreconcilable cases to operator review.

### Payments and refunds
The Payment capability owns payment obligations, provider execution, payment/refund state transitions, reconciliation and provider adapters. Event ticketing owns the commercial order and immutable amount/currency reference. Support pluggable local Indonesian payment methods/providers where contracts and legal requirements permit. Never hardcode IDR into shared domain contracts, store raw card data, or treat a requested/pending refund as settled. Refund eligibility, fees, cancellation, partial refunds and organizer responsibility need explicit audited policies.

### Ticket issuance and admission
Issue a stable entitlement only after the defined confirmation condition. Use cryptographically strong opaque QR/barcode tokens that expose no personal data; support revocation/rotation and replay-safe scan records. Admission checks tenant/event scope, ticket status, entry rules and scan idempotency. Concurrent scans must not admit the same single-entry ticket twice. Transfers and scan reversals are audited policy-controlled commands.

### Organizer operations
Support publishing, sales/capacity dashboards, attendee lists, check-in, complimentary tickets, cancellation/rescheduling, refunds, reconciliation and exports. Apply least privilege across organizer, box-office, scanner, finance and attendee roles. Authorize bulk actions and personal-data exports and record audit events.

## Cross-cutting requirements

- Tenant isolation on every API and repository query; never trust tenant/actor IDs from request payloads or caller-controlled headers.
- Currency-aware money representation, validated currency codes and immutable order-price snapshots.
- Explicit event timezone plus unambiguous persisted instants; inject clocks for deterministic tests.
- Version public API/event schemas and document migration/compatibility behavior.
- Durable outbox/inbox, bounded retries, dead-letter/operator workflows, correlation IDs and metrics.
- Privacy/data minimization and retention, protected attendee details, rate limits and abuse controls.
- Optional adapters for notifications, receipts/tax documents, identity, provider gateways and analytics.
- No secrets in source. Do not claim PCI/tax compliance or production readiness without the relevant assessment and operational evidence.

## Milestones

### M0 — Reconcile repository and contracts
Verify `AGENTS.md`, ADRs, capability matrix, payment public API and adapter, Reservation semantics, Transport PRs, migrations, reactor roots and CI. Record current PR state and exact build commands. Decide the owner of each invariant and document contract gaps. **Acceptance:** dependency map and reviewed boundary/ADR; no speculative empty modules.

### M1 — Framework-neutral event domain
**Initial domain implementation is on branch `feat/events-ticketing-m1-domain`; this is not yet persistence-backed or production-ready.** The new `events` reactor currently models channel-neutral ticket products and booking snapshots, explicit sales channels, payment-confirmation/expiry transitions, and one admission entitlement that can accept multiple credential types (QR, barcode, RFID, NFC, wallet pass, or controlled manual lookup). Credential records store digests rather than raw presented tokens; scanner requests carry a durable scan ID for idempotent retry. Complete event publication/scheduling, application orchestration, persistence and distributed concurrency in follow-up milestones. Validate timezone, schedule ordering, sales windows, currency, capacity and state transitions. **Acceptance:** unit tests for invalid transitions, boundary conditions and immutable order snapshots, without HTTP/database/provider dependencies.

### M2 — Inventory and booking application
**Implementation on branch `feat/events-ticketing-m2-persisted-booking`; CI and PostgreSQL integration tests still require verification.** Added a public booking-store port, channel-neutral application service, PostgreSQL JDBC adapter, tenant-scoped inventory/booking/idempotency tables, row-lock-based capacity allocation, immutable price snapshots, expiry/release/confirmation transitions, and Testcontainers coverage for replay, payload mismatch, tenant isolation, oversell prevention, and late payment. This implementation currently covers general-admission capacity; assigned seating, durable transactional outbox, verified payment callback integration, and event admission persistence remain follow-up work. **Acceptance:** concurrent allocation cannot exceed capacity; same-key replay returns original result; changed payload conflicts; expiry/confirmation races retain audit history; PostgreSQL tests actually run in CI.

### M3 — Payment orchestration
Integrate through the public Payment application contract. Add provider execution, signed callback verification, durable delivery, reconciliation and explicit state transitions in the owning payment capability/adapters. Test duplicate/out-of-order callbacks, amount/currency mismatch, timeouts, late provider success, refund limits and tenant isolation. **Acceptance:** deterministic reconciliation and observable operator-actionable unknown outcomes.

**Current implementation update:** the shared Payment API now has a verified-provider-outcome command and the PostgreSQL adapter persists provider event IDs in a tenant-scoped inbox, validates amount/currency/reference against the obligation, applies explicit state transitions under a payment-row lock, and emits a transactional outbox event. Late success after an expired/cancelled/failed state emits an operator-reconciliation event rather than a normal success event. This is a transition/persistence foundation only: it does not verify provider signatures itself, execute provider requests, publish the outbox, or complete Events booking orchestration. A provider-specific adapter must authenticate and verify callbacks before invoking this contract.

### M4 — Ticket issuance and check-in
Add idempotent ticket issuance, secure tokens, attendee assignment, transfer/revocation policies, scanner validation and duplicate-scan controls. Integrate optional delivery through ports/outbox. **Acceptance:** no duplicate ticket per entitlement; revoked tickets fail; concurrent scans cannot double-admit.

### M5 — Authenticated APIs and experience
Add optional versioned HTTP adapters, trusted tenant/actor extraction, authorization, stable errors, pagination and rate limits. Compose buyer checkout, order status, ticket wallet, organizer publishing and box-office UI independently. **Acceptance:** DTOs do not accept trusted identity; contract tests cover cross-tenant access and abuse cases.

### M6 — Operations and readiness
Add cancellation/rescheduling policy, reconciliation reports, audit exports, receipt/tax integration points, retention, observability, support workflows, load/concurrency/security/migration/failure-injection tests and disaster-recovery runbooks. **Acceptance:** documented SLOs, alerts, restore/reconciliation procedures and evidence-based deployment checklist.

## Minimum acceptance matrix

| Area | Required proof |
|---|---|
| Inventory | Concurrent allocation never exceeds capacity; holds expire safely |
| Idempotency | Same key/request replays; same key/changed request conflicts |
| Payment | Verified provider evidence controls transitions; amount/currency/reference checked |
| Recovery | Duplicate/out-of-order callbacks, timeouts and partial failures recover |
| Ticketing | Only eligible confirmed bookings issue tickets; revocation and duplicate scans enforced |
| Security | Tenant isolation, authorization, rate limits, no caller-controlled identity |
| Audit | Overrides, refunds, transfers, cancellations and check-ins are attributable |
| Delivery | At-least-once outbox/inbox retries do not duplicate business effects |
| Architecture | Framework-neutral domain; optional adapters avoid unrelated dependencies |
| Verification | Narrow reactor, PostgreSQL tests and CI status reported accurately |

Start with M0, then deliver one coherent M1 milestone. Do not begin with a wide set of empty modules or endpoints. Production readiness requires demonstrated invariants, tests, persistence semantics, security, migrations and operational behavior.
