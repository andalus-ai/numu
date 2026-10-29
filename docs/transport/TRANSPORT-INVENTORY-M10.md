# Transport M10 — Explicit compensation refunds

## Scope

M10 adds a narrowly scoped compensation action for the known failure case where Payment reports a verified success but the Transport capacity hold can no longer be confirmed. M8 marks this as `RECONCILIATION_REQUIRED`; M10 lets an authorized operator/support workflow request a full refund through the public Payment application contract.

This is not an automatic refund policy. Refunds move money and are intentionally explicit. The endpoint does not issue tickets, rebook capacity, or claim the refund has settled merely because the request was accepted.

## API

When checkout is enabled:

- `POST /v1/transport/checkouts/{checkoutId}/refund`
- JSON body: `{"reason":"capacity expired after successful payment"}`
- Requires authenticated identity, active tenant membership, and an explicit `PERFORM` decision for resource `transport.booking.checkout`.
- The tenant comes from the trusted security context. The caller cannot supply tenant or payment identifiers in the request body.
- The response includes checkout state, refund operation ID, and refund status.

Only a checkout in `RECONCILIATION_REQUIRED` or `REFUND_PENDING` is eligible. For the initial request, Transport re-reads Payment and requires a verified successful payment with matching tenant, checkout reference, amount, and currency. A retry while `REFUND_PENDING` uses the same idempotency key to recover the existing refund operation, even if Payment's own state has advanced to a refunded status.

## Durable behavior

- The refund idempotency key is deterministic per checkout: `transport-checkout:refund:{checkoutId}`.
- Refund ID and status are persisted in the Transport-owned checkout row using optimistic version checks.
- Migration `V4__transport_checkout_refund.sql` adds refund correlation and permits the new checkout statuses without changing Payment-owned storage.
- `REQUESTED`, `PENDING`, and `UNKNOWN` refund outcomes remain `REFUND_PENDING`. Only Payment's `SUCCEEDED` refund-operation status becomes `REFUNDED`.
- A failed refund becomes `REFUND_FAILED`. The API deliberately does not generate a new refund attempt key automatically; a new attempt requires operator review.
- Repeating a successful refund request returns the durable checkout snapshot without issuing another refund.

## Verification

- Application tests cover a late successful payment, expired capacity, explicit compensation, and idempotent replay.
- PostgreSQL integration tests cover persistence of refund correlation and refund-state round trips.
- These tests must run in the Transport reactor; PostgreSQL integration tests require Docker/Testcontainers.

## Operational requirements before production

1. Restrict `transport.booking.checkout` refund permission to a dedicated role or policy. Do not grant it to ordinary passenger roles.
2. Alert on `RECONCILIATION_REQUIRED`, `REFUND_PENDING`, and `REFUND_FAILED` states and provide an operator queue.
3. Reconcile refund operations against Payment/provider settlement reports; an accepted refund operation is not proof that funds reached the customer.
4. Document manual recovery for a failed refund and retain evidence of the operator's reason and decision in the platform's audit system.
5. Run host-level integration tests with real authorization and Payment implementations before enabling checkout in production.
