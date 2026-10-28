# Marketplace checkout saga

**Status:** Implementation milestone in review  
**Owner:** Marketplace application capability  
**Persistence:** Marketplace PostgreSQL adapter

## Ownership and reuse

Marketplace owns the durable coordination record for a seller-scoped order checkout. Inventory remains the owner of stock quantities and reservations. Payment remains the owner of payment obligations, provider interaction, verified outcomes, and refunds. The checkout record stores references and progress only; it is not a second inventory or payment ledger.

The application depends on narrow inventory and payment anti-corruption ports. Host applications may implement these in-process or with versioned remote adapters. The marketplace application does not depend on a fulfillment implementation or a payment provider SDK.

## Preconditions

- Order is tenant-scoped and exists.
- Trusted authorization confirms the actor is the order buyer.
- Order has been accepted by its seller.
- Each order already contains immutable product/offering/seller/quantity/unit/price/currency snapshots.
- Each marketplace order is seller-scoped. Multi-seller carts are split into separate orders before checkout.

## Durable states

- `RESERVING_INVENTORY`: reserve each line using a stable command key and persist each reservation ID before moving to payment.
- `AWAITING_PAYMENT`: a payment operation exists; continue querying its provider-neutral status.
- `READY_FOR_FULFILLMENT`: payment is verified as succeeded. This is a readiness signal, not an inventory issue or shipment creation.
- `FAILED`: a known terminal failure was reached and known reservations were released.
- `COMPENSATION_REQUIRED`: a reservation release failed; keep references and require operator/automated recovery.
- `RECONCILIATION_REQUIRED`: a reservation or payment result is ambiguous. Do not assume failure and do not release stock while payment could have succeeded.

All state updates use a persisted version compare-and-set. Reservation checkpoints are immutable per checkout line. The tenant-scoped idempotency key and request fingerprint are unique at the database boundary.

## Retry and compensation rules

1. Reservation commands use stable keys derived from checkout ID and order-line ID. If the caller loses a response, the inventory adapter must support lookup/replay by that key.
2. The checkout only starts payment after all line reservations have been durably checkpointed.
3. Payment creation uses a stable checkout-scoped idempotency key and the order's immutable total/currency.
4. `CREATED`, `REQUIRES_CUSTOMER_ACTION`, `PENDING`, and `UNKNOWN` are not payment failures. `UNKNOWN` requires reconciliation and must retain reservations.
5. A known payment failure triggers idempotent release of recorded reservations. Any release failure transitions to `COMPENSATION_REQUIRED`.
6. A timeout or exception around payment creation does not prove payment failure. The checkout is retained for idempotent re-query/reconciliation; stock is not released on that basis.
7. A known reservation failure before payment triggers release of previously checkpointed reservations. If the reservation outcome itself is ambiguous, checkout is blocked for inventory reconciliation and must not create payment.
8. The adapter must preserve tenant isolation and immutable reservation IDs across retries.

## Explicit non-goals for this milestone

- No stock movement or payment ledger is written by marketplace.
- No assumption that payment success means fulfillment/shipment is complete.
- No automatic refund is attempted after successful payment; refund policy is a separate payment/compensation decision.
- No claims of exactly-once distributed execution. Side effects are at-least-once with idempotent command keys and durable checkpoints.
- No claim that every inventory adapter supports reservations. The host must provide an adapter that fulfills the port's idempotency, lookup, tenant, and atomicity contract.

## Verification and rollout

The PostgreSQL migration adds durable checkout state and reservation references. The adapter integration tests exercise tenant scoping, persisted reservation checkpoints, and compare-and-set versioning. Application tests cover successful payment, ambiguous payment outcomes, and reservation failure compensation.

Before production rollout, verify the marketplace Maven reactor and CI, and validate the selected inventory adapter's durable reservation and command-key lookup behavior. Do not enable checkout in a composition using an in-memory-only reservation adapter for multi-instance production traffic.


## Payment capability composition update

The marketplace application now includes `PaymentCapabilityCheckoutAdapter`, which maps the public `PaymentApplicationPort` contract into checkout's provider-neutral payment port. Payment continues to own obligation persistence, idempotency, provider execution, and verified outcomes. The adapter maps refund lifecycle states to `UNKNOWN` for checkout purposes: a refunded obligation is not evidence that the original checkout attempt failed.

An authoritative `PaymentOperationRejectedException` is translated to a known checkout rejection so the coordinator can compensate known inventory reservations. Other runtime failures remain ambiguous and retain reservations for reconciliation.

The inventory adapter is intentionally not declared production-ready yet. The current inventory application exposes idempotent reservation creation, but its public contract does not provide a durable lookup by reservation command key, and the repository's only concrete inventory adapter is in-memory. Marketplace must not wire this into multi-instance production checkout until the inventory capability exposes and implements durable command-key recovery and a persistent adapter is available.


## Inventory command recovery contract

The inventory application now exposes tenant-scoped lookup by the same stable `InventoryCommandKey` used to create a reservation. Recovery returns the current reservation lifecycle state, including released or expired reservations; callers must not interpret a missing result from an adapter with weak consistency as proof that a timed-out reservation did not commit. The in-memory adapter supports this contract for tests and single-process use only. A persistent inventory adapter must implement lookup atomically with durable command-key receipts before checkout can be enabled for multi-instance production traffic.
