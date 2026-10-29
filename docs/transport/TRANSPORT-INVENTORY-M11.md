# Transport M11 — Durable refund-operation reconciliation

## Problem

A refund request can time out after Payment accepts it. If Transport only persists the refund reason after receiving a response, a retry could use a different reason with the same idempotency key. Pending refunds also need bounded polling so the system can observe settlement without issuing duplicate refund operations.

## Implementation

- Persist a `REFUND_REQUESTED` intent and its reason before calling the Payment capability. If the request times out, the same semantic command remains durable.
- `REFUND_REQUESTED` and `REFUND_PENDING` use a stable checkout-derived idempotency key and the persisted reason. A caller cannot change the reason during retries.
- The Payment adapter uses the public `PaymentApplicationPort.requestRefund` contract. Transport does not read Payment-owned tables or infer refund settlement from payment redirects.
- The checkout repository exposes `findStaleRefunds(updatedBefore, limit)`, selecting an oldest-first bounded batch of refund intents and pending refunds.
- `TransportCheckoutService.reconcileDueRefunds` replays the same refund command, validates tenant/payment/amount/currency correlation, and advances the retry cursor. Terminal `REFUND_FAILED` outcomes are surfaced as failures for operational alerting.
- `TransportRefundReconciliationScheduler` is opt-in with checkout, prevents overlapping local runs, uses bounded batches, and emits Micrometer run/attempt/failure/duration metrics.
- V5 migration adds the persisted reason, tightens refund state constraints, and includes `REFUND_REQUESTED` in the allowed lifecycle statuses.

## Configuration

- `numou.transport.checkout.refund-reconciliation.interval`: default `60s`.
- `numou.transport.checkout.refund-reconciliation.batch-size`: default `50`, range 1–500.
- `numou.transport.checkout.refund-reconciliation.minimum-age-seconds`: default `60`, range 1–86400.

## Operational behavior

- `REFUND_REQUESTED`: the intent is durable; the provider operation may be unknown after a timeout and must be retried idempotently.
- `REFUND_PENDING`: Payment accepted or is processing the refund, but settlement is not proven.
- `REFUNDED`: Payment reports the refund operation as succeeded.
- `REFUND_FAILED`: requires operator review; M11 does not create a second refund attempt automatically.
- A refund request accepted by Payment is not proof that funds have reached the customer. Reconcile with provider settlement evidence and retain the audit reason.

## Verification

- Application tests cover a timeout after intent persistence, same-reason retry, pending-to-succeeded reconciliation, and rejection of changed reasons.
- PostgreSQL integration tests cover intent/reason persistence and bounded selection of both refund-intent and pending-refund states.
- Run the Transport reactor and inspect CI before enabling the feature. Docker/Testcontainers availability determines whether PostgreSQL integration tests execute locally.
