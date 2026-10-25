# M24 — Shipment-level delivery completion

## Behavior

A POD is now the authoritative input for completing both the matched delivery task and its shipment. Within the same PostgreSQL transaction, the projector:

1. Validates the tenant, shipment reference, delivery-attempt reference, and POD outcome.
2. Applies the guarded delivery-task transition.
3. Changes the shipment from `BOOKED` to `DELIVERED` only if the task transition was newly applied and there are no remaining active delivery tasks for the shipment.
4. Persists `delivered_at` and a `courier.shipment.delivered.v1` outbox event in the same transaction.
5. Writes the append-only tracking event and acknowledges the POD outbox row.

A replay must match the already-persisted task and shipment completion timestamps. A mismatched or partial state is not silently repaired or acknowledged; the transaction rolls back to its savepoint and follows the existing retry path.

## Multi-parcel policy

The current model assigns a delivery task to a shipment, not to an individual parcel. Therefore, this transition treats that task's POD as confirmation that the entire shipment was delivered. It does not infer per-parcel outcomes. If operational workflows require split delivery, the model must evolve to parcel-level delivery attempts before enabling partial-delivery completion.

## Domain and persistence

- Adds `ShipmentStatus.DELIVERED` and a domain `deliveredAt` value with validation.
- Migration V24 adds the persisted timestamp, extends the status constraint, and indexes completed shipments.
- Repository reconstitution reads the completion timestamp; ordinary save operations bind it.

## Validation gates

Contract and domain tests are added. Tests have not been run in this session. CI must verify migration compatibility, transactional rollback, concurrent duplicate PODs, exact replay, conflicting outcomes, tenant isolation, and delivery-event outbox idempotency.