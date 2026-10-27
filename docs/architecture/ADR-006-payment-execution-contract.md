# ADR-006: Deployment-neutral payment execution contract

- **Status:** Proposed for implementation
- **Date:** 2026-10-27
- **Decision owners:** Numou platform maintainers
- **Related decisions:** [ADR-003: Reusable capability modules](ADR-003-reusable-capability-modules.md), [ADR-005: Accommodation booking](ADR-005-accommodation-booking.md)

## Context

Numou needs payment execution to be reusable by Hospitality, Commerce, Mobility, Logistics, and other capabilities without requiring each consumer to depend on gateway internals or payment persistence entities. A payment transaction is not a reservation, an order, a wallet movement, a payout, or an accounting journal; those records have distinct owners and lifecycles.

The current `finance/payment/numou-payment-core` implementation exposes a Quarkus/CDI-oriented `PaymentService`, Panache repository, entity-based operations, and DTOs. Its current flow creates and processes a payment, but the inspected `createPayment` path does not establish tenant-scoped idempotency, and the processing context hardcodes `IDR`. The existing `PaymentGatewayProvider` is a gateway strategy contract, not a consumer-facing payment application contract. These observations are a boundary assessment, not a claim that every payment adapter or callback path has been exhaustively audited.

Hospitality already owns stay-specific quote and inventory semantics. It must not reach into payment entities or make booking availability depend on a provider-specific response. A verified payment outcome is evidence for a workflow decision, not permission to bypass the booking lifecycle's own invariants.

## Decision

Introduce a deployment-neutral public application contract owned by the Finance Payment capability. Implement it in stages, retaining the existing gateway strategies and REST API while consumers migrate.

The public contract must be usable in-process by a modular monolith and mappable to a versioned HTTP or messaging contract for distributed deployments. Domain/application contracts must not require Quarkus, Panache, a specific database, a gateway vendor, or Hospitality classes.

### Contract responsibilities

The payment application contract must support:

1. **Create or retrieve a payment obligation** using a caller-supplied stable business reference, amount, ISO currency, tenant scope, and idempotency key. It must return a stable payment identifier and an explicit lifecycle state.
2. **Retrieve current payment state** by stable identifier under tenant authorization; identifiers alone are not authorization.
3. **Request cancellation or refund** through an explicit command with reason, amount where applicable, and an idempotency key. The result must distinguish accepted/pending from completed or failed provider operations.
4. **Consume verified provider outcomes** only after provider-specific signature/authenticity checks. Duplicate delivery must be harmless; mismatched merchant, amount, currency, or payment reference must be rejected or quarantined for investigation.
5. **Reconcile uncertain outcomes** through a query/reconciliation path rather than treating a timeout as proof of failure.
6. **Expose durable correlation** for audit and operational support, including tenant, payment ID, caller reference, idempotency scope, provider reference, and correlation metadata. Avoid logging payment secrets or unnecessary customer PII.

### Required invariants

- Idempotency is scoped by tenant and operation. Repeating the same key and semantically identical request returns the original operation; reusing it with a different fingerprint is a conflict.
- Payment amounts are positive and use explicit currency/precision rules. Currency is carried through persistence and gateway request construction; it must not be silently defaulted or hardcoded in the application flow.
- Provider callback verification and payment state transitions are server-side responsibilities. Browser redirects and caller-provided status claims are not proof of payment.
- State transitions are explicit, replay-safe, auditable, and concurrency-safe. Unknown or conflicting provider outcomes remain observable and reconcilable.
- Refunds have independent identifiers and idempotency; refund eligibility and cumulative refundable amount are enforced atomically.
- Tenant and merchant ownership are validated at the application boundary. Cross-tenant lookup or mutation must not disclose another tenant's payment.
- External gateway calls are not assumed to participate in a local database transaction. Persist intent and correlation before dispatch, and use durable retry/reconciliation or an outbox-equivalent mechanism where the adapter's guarantees require it.
- Payment execution does not post general-ledger journals or mutate wallet balances directly. Accounting, wallet, order, and booking transitions are separate contracts.
- Public contracts contain stable identifiers and value objects, not JPA entities, Panache repositories, gateway DTOs, or provider-specific enums.

## Hospitality composition

Hospitality may depend on the public payment application contract through an adapter. It remains responsible for stay quote snapshots, inventory holds, and booking lifecycle transitions. The payment capability remains responsible for payment execution and verified provider outcomes.

A checkout workflow must preserve the following distinctions:

- an inventory hold is not a payment authorization;
- a payment request accepted by a gateway is not necessarily a settled payment;
- a successful payment does not bypass the booking lifecycle's confirmation guard;
- a failed, expired, or uncertain payment must trigger a defined retry, release, compensation, or reconciliation path;
- payment and booking transitions must be correlatable and recoverable after process restarts.

The first Hospitality adapter should not directly inject `PaymentService`, query `PaymentRepository`, or depend on payment persistence tables.

## Implementation sequence and acceptance criteria

### P1 — Public contract and conformance tests

- Inspect and document existing payment module ownership and current public APIs.
- Add a public application-level port and framework-neutral request/result types in the owning payment capability.
- Add conformance tests for request validation, tenant-scoped idempotency replay/conflict, explicit currency, status semantics, and refund command semantics.
- Keep existing APIs compatible; do not claim the contract is production-ready until an implementation enforces these semantics.

### P2 — Persistence and gateway adapter

- Persist payment intent, tenant, currency, caller reference, idempotency key/fingerprint, and operation state under uniqueness/transaction guarantees.
- Make gateway dispatch retry-safe and record provider correlation.
- Verify provider events before applying them; deduplicate events and reconcile ambiguous outcomes.
- Add database integration tests for concurrency, rollback, tenant isolation, duplicate callbacks, and duplicate refund requests.

### P3 — Hospitality adapter

- Implement the adapter against the public payment contract without importing payment entities or repositories.
- Coordinate payment and booking state machines with durable recovery behavior.
- Add end-to-end tests for duplicate checkout, duplicate provider callbacks, payment failure, expired holds, concurrent confirmation/expiry, and restart/reconciliation behavior.

### P4 — Migration and operations

- Migrate consumers incrementally; preserve REST compatibility and existing payment history.
- Add audit-safe metrics and operator-visible handling for stuck or conflicting operations.
- Verify representative modular-monolith composition and a remote-contract mapping before claiming topology neutrality.

## Non-goals

- This ADR does not make payment execution a separate microservice by default.
- It does not unify payment state with reservation, order, wallet, payout, or accounting state.
- It does not select a payment gateway or define every provider's callback format.
- It does not assert that the existing payment implementation currently satisfies the invariants above.

## Consequences

The existing gateway strategy can remain an internal implementation detail. A stable application boundary lets Hospitality and other consumers reuse payment execution while preserving independent lifecycles and deployment options. The cost is an incremental migration of idempotency, currency handling, tenant scoping, verified callbacks, and recovery behavior; these are implementation requirements, not documentation-only guarantees.
